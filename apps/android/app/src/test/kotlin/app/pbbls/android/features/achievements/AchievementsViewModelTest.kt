package app.pbbls.android.features.achievements

import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.Completion
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * The page's load, cache, resume, sheets, seen marks and ceremony (#1307), on a virtual clock:
 * iOS's `AchievementsModelTests`, case for case where the behaviour is shared.
 */
class AchievementsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val page = AchievementsFixture.page
    private val stale = AchievementsFixture.page.copy(daysKept = 1)

    /** The fixture with the six firsts whole, offered and not pulled: a ready mark on Explorer. */
    private val ready =
        page.copy(explorer = page.explorer!!.copy(completion = page.explorer!!.completion!!.copy(ready = true, pulledAt = null)))

    private val pulled =
        ready.copy(
            explorer = ready.explorer!!.copy(completion = ready.explorer!!.completion!!.copy(ready = false, pulledAt = PULLED)),
        )

    private fun AchievementsViewModel.shown(): AchievementsPage? = (uiState.value as? AchievementsUiState.Content)?.page

    private fun AchievementsViewModel.sixFirsts(): Completion? = shown()?.explorer?.completion

    private fun vm(
        service: FakeAchievementsService,
        notify: AchievementNotificationService = AchievementNotificationService(),
    ) = AchievementsViewModel(service, notify)

    private fun TestScope.pull(
        viewModel: AchievementsViewModel,
        stack: CompletionStack = CompletionStack.SIX_FIRSTS,
    ): PullOutcome {
        val outcome = async { viewModel.pull(stack) }
        advanceUntilIdle()
        return outcome.getCompleted()
    }

    // ---- The load -----------------------------------------------------------------------------

    @Test
    fun `the retroactive grant runs before the read, and the page lands`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
            assertEquals(listOf("check", "page"), service.calls)
        }

    @Test
    fun `the device copy shows at once, then the fresh page replaces it`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val service = FakeAchievementsService(served = page, kept = stale).apply { pageGate = gate }
            val viewModel = vm(service)
            runCurrent()
            assertEquals(AchievementsUiState.Content(stale), viewModel.uiState.value)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
        }

    @Test
    fun `a failed read keeps the device copy`() =
        runTest {
            val service = FakeAchievementsService(served = page, kept = stale).apply { failNextPage = IOException("offline") }
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(stale), viewModel.uiState.value)
        }

    @Test
    fun `a cold failure is an error, and retry recovers`() =
        runTest {
            val service = FakeAchievementsService(served = page).apply { failNextPage = IOException("offline") }
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Error(R.string.achievements_error_body), viewModel.uiState.value)
            viewModel.retry()
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()
            assertEquals(AchievementsUiState.Content(page), viewModel.uiState.value)
        }

    @Test
    fun `the zone is read when each load and pull starts, never frozen`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val viewModel = vm(service)
            viewModel.zone = { PARIS }
            advanceUntilIdle()
            viewModel.zone = { TOKYO }
            pull(viewModel)
            assertEquals(listOf(PARIS, PARIS, TOKYO, TOKYO), service.zones)
        }

    @Test
    fun `a read that lands after a newer one already shown is dropped`() =
        runTest {
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            val service = FakeAchievementsService(served = stale).apply { pageGates += listOf(first, second) }
            val viewModel = vm(service)
            runCurrent()
            // The pull reads first (nothing read yet), while the load's read is still out.
            service.served = ready
            val outcome = async { viewModel.pull(CompletionStack.SIX_FIRSTS) }
            runCurrent()
            second.complete(Unit)
            runCurrent()
            assertEquals(ready, viewModel.shown())
            first.complete(Unit)
            advanceUntilIdle()
            assertEquals(ready, viewModel.shown())
            outcome.await()
        }

    // ---- The sheets ---------------------------------------------------------------------------

    @Test
    fun `opening a sheet marks its card seen, Explorer pushes Emotions, a dismissal pops it and those above`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.openSheet(AchievementSheet.Emotions)
            assertEquals(listOf(AchievementSheet.Explorer, AchievementSheet.Emotions), viewModel.sheets.value.visible)
            assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), service.seen)
            viewModel.closeSheet(AchievementSheet.Emotions)
            assertEquals(listOf<AchievementSheet>(AchievementSheet.Explorer), viewModel.sheets.value.visible)
            viewModel.openSheet(AchievementSheet.Emotions)
            viewModel.closeSheet(AchievementSheet.Explorer)
            assertTrue(
                viewModel.sheets.value.visible
                    .isEmpty(),
            )
        }

    @Test
    fun `a sheet whose section left the payload is let go`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            advanceUntilIdle()
            viewModel.onResumed()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.openSheet(AchievementSheet.Emotions)
            viewModel.openMoment("moment-7")
            service.served = page.copy(emotions = null)
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(listOf<AchievementSheet>(AchievementSheet.Explorer), viewModel.sheets.value.visible)
        }

    @Test
    fun `opening a pebble parks the sheets and asks to open it, the return unparks and refreshes`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.pageCalls)
            viewModel.openSheet(AchievementSheet.Pebbles)
            viewModel.openMoment("moment-7")
            advanceUntilIdle()
            assertEquals(listOf<AchievementsEffect>(AchievementsEffect.OpenMoment("moment-7")), effects.values)
            assertTrue(
                viewModel.sheets.value.visible
                    .isEmpty(),
            )
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.pageCalls)
            assertEquals(listOf("check", "page", "page"), service.calls)
            assertEquals(listOf<AchievementSheet>(AchievementSheet.Pebbles), viewModel.sheets.value.visible)
            effects.stop()
        }

    // ---- The ceremony -------------------------------------------------------------------------

    @Test
    fun `a pull closes the sheets, presents its ceremony with the server's karma, and reads again`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertEquals(
                listOf(
                    AchievementMomentCard(
                        "complete-six-firsts",
                        record = null,
                        karmaGranted = 30,
                        titleRes = R.string.achievements_ceremony_six_firsts_title,
                        bodyRes = R.string.achievements_ceremony_six_firsts_body,
                    ),
                ),
                notify.cards,
            )
            assertTrue(
                viewModel.sheets.value.visible
                    .isEmpty(),
            )
            assertEquals(listOf(CompletionStack.SIX_FIRSTS), service.pulls)
            assertEquals(2, service.pageCalls)
            assertNull(viewModel.pulling.value)
        }

    @Test
    fun `a completion not offered, or none, is not ready and asks nothing`() =
        runTest {
            val service =
                FakeAchievementsService(
                    served = page.copy(explorer = page.explorer!!.copy(completion = ready.explorer!!.completion!!.copy(ready = false))),
                )
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(PullOutcome.NotReady, pull(viewModel))
            assertEquals(PullOutcome.NotReady, pull(viewModel, CompletionStack.EMOTIONS))
            assertTrue(service.pulls.isEmpty())
        }

    @Test
    fun `a completion the read shows pulled is already pulled and asks nothing`() =
        runTest {
            val service = FakeAchievementsService(served = page)
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(PullOutcome.AlreadyPulled, pull(viewModel))
            assertTrue(service.pulls.isEmpty())
        }

    @Test
    fun `the pull is decided on a read made in this session, never on the device copy`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val service = FakeAchievementsService(served = page, kept = ready).apply { pageGate = gate }
            val viewModel = vm(service)
            runCurrent()
            assertEquals(ready, viewModel.shown())
            val outcome = async { viewModel.pull(CompletionStack.SIX_FIRSTS) }
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(PullOutcome.AlreadyPulled, outcome.await())
            assertTrue(service.pulls.isEmpty())
        }

    @Test
    fun `with no read landed in this session the pull fails without asking`() =
        runTest {
            val service = FakeAchievementsService(served = ready, kept = ready).apply { failNextPage = IOException("offline") }
            val viewModel = vm(service)
            advanceUntilIdle()
            service.failNextPage = IOException("still offline")
            assertEquals(PullOutcome.Failed, pull(viewModel))
            assertTrue(service.pulls.isEmpty())
        }

    @Test
    fun `a stack pulled in this session is never asked again`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val viewModel = vm(service)
            advanceUntilIdle()
            assertEquals(PullOutcome.Pulled(30), pull(viewModel))
            // The server still says ready (a read that raced the pull): this session knows better.
            assertEquals(PullOutcome.AlreadyPulled, pull(viewModel))
            assertEquals(listOf(CompletionStack.SIX_FIRSTS), service.pulls)
        }

    @Test
    fun `after a pull the mark goes even when the read after it fails`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val viewModel = vm(service)
            advanceUntilIdle()
            service.failNextPage = IOException("offline")
            assertEquals(PullOutcome.Pulled(30), pull(viewModel))
            val completion = viewModel.sixFirsts()!!
            assertFalse(completion.ready)
            assertNotNull(completion.pulledAt)
        }

    @Test
    fun `no row, then a read showing it pulled, is a pull whose answer was lost - played without karma`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.AlreadyPulled)
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            advanceUntilIdle()
            service.served = pulled
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertEquals(0, notify.currentCard?.karmaGranted)
            assertEquals(R.string.achievements_ceremony_six_firsts_title, notify.currentCard?.titleRes)
        }

    @Test
    fun `a lost answer, then a read showing it pulled, plays without karma too`() =
        runTest {
            val service = FakeAchievementsService(served = ready).apply { failNextPull = IOException("lost") }
            val viewModel = vm(service)
            advanceUntilIdle()
            service.served = pulled
            assertEquals(PullOutcome.PulledWithoutKarma, pull(viewModel))
        }

    @Test
    fun `no row and a read still unpulled is already pulled, and shows nothing`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.AlreadyPulled)
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertNull(notify.currentCard)
            assertTrue(effects.values.isEmpty())
            assertEquals(2, service.pageCalls)
            effects.stop()
        }

    @Test
    fun `not_ready from the server reads again and shows nothing`() =
        runTest {
            val service = FakeAchievementsService(served = ready).apply { failNextPull = postgrestException("not_ready") }
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertNull(notify.currentCard)
            assertTrue(effects.values.isEmpty())
            assertEquals(2, service.pageCalls)
            effects.stop()
        }

    @Test
    fun `any other pull failure says so and changes nothing`() =
        runTest {
            val service = FakeAchievementsService(served = ready).apply { failNextPull = IOException("offline") }
            val notify = AchievementNotificationService()
            val viewModel = vm(service, notify)
            val effects = recordEffects(viewModel.effects)
            advanceUntilIdle()
            viewModel.openSheet(AchievementSheet.Explorer)
            viewModel.pullCeremony(CompletionStack.SIX_FIRSTS)
            advanceUntilIdle()
            assertEquals(listOf<AchievementsEffect>(AchievementsEffect.Message(R.string.achievements_ceremony_error)), effects.values)
            assertFalse(
                viewModel.sheets.value.visible
                    .isEmpty(),
            )
            assertNull(notify.currentCard)
            assertTrue(viewModel.sixFirsts()!!.ready)
            effects.stop()
        }

    @Test
    fun `a pull asked while another runs is busy, and every mark waits on the one running`() =
        runTest {
            val service = FakeAchievementsService(served = ready, pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30))
            val viewModel = vm(service)
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            service.pageGate = gate
            val first = async { viewModel.pull(CompletionStack.SIX_FIRSTS) }
            runCurrent()
            assertEquals(CompletionStack.SIX_FIRSTS, viewModel.pulling.value)
            assertEquals(PullOutcome.Busy, viewModel.pull(CompletionStack.EMOTIONS))
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(PullOutcome.Pulled(30), first.await())
            assertNull(viewModel.pulling.value)
            assertEquals(listOf(CompletionStack.SIX_FIRSTS), service.pulls)
        }

    private companion object {
        val PULLED: Instant = Instant.parse("2026-10-30T08:00:00Z")
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")
        val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
    }
}
