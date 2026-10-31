package app.pbbls.android.features.home

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.core.data.AchievementCheckResult
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.HomeShortcut
import app.pbbls.android.core.data.HomeShortcutsLayout
import app.pbbls.android.core.data.HomeShortcutsPreference
import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import app.pbbls.android.features.home.checklist.ChecklistState
import app.pbbls.android.features.home.checklist.ChecklistTask
import app.pbbls.android.features.home.checklist.HomeChecklist
import app.pbbls.android.features.home.shortcuts.HomeShortcutsStyle
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeHomeService
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakePathWrapService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.inMemorySettings
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

/** Home's reads (#1383; iOS `HomeModelTests` where shared): the name, the month (D9), the week tap (D10). */
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = FakeHomeService()
    private val wraps = FakePathWrapService()
    private val tabIntents = TabIntents()

    private val settings = inMemorySettings()
    private val shortcuts = HomeShortcutsPreference(settings)

    private val achievements = FakeAchievementsService()
    private val moments = FakeMomentReadService()

    private fun viewModel() = HomeViewModel(home, wraps, tabIntents, shortcuts, HapticsPreference(settings), achievements, moments)

    /** The server's own month, moved to the live one, [pebbles] telling two reads apart. */
    private fun month(pebbles: Int): MonthWrap =
        WrapJson
            .decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText())
            .toModel()
            .copy(month = YearMonth.now(), pebbles = pebbles)

    @Test
    fun `nothing is read before the first resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `the name lands on resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alexis", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed name read keeps the empty name`() =
        runTest {
            // The service's failure answers "" (it logs and swallows), so the fake's "" is that read.
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed read on a later resume keeps the name already shown`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            home.name = ""
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alexis", viewModel.uiState.value.name)
        }

    @Test
    fun `a later resume picks up a changed name`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            home.name = "Alex"
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alex", viewModel.uiState.value.name)
        }

    // MARK: - The month (D9)

    @Test
    fun `the month paints from the device's copy, then from the network`() =
        runTest {
            val kept = month(pebbles = 1)
            val fresh = month(pebbles = 2)
            wraps.keptMonths[YearMonth.now()] = kept
            wraps.months[YearMonth.now()] = fresh
            val gate = CompletableDeferred<Unit>()
            wraps.monthGate = gate
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(HomeSection.Ready(kept), viewModel.uiState.value.month)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(HomeSection.Ready(fresh), viewModel.uiState.value.month)
            assertEquals(listOf(YearMonth.now()), wraps.monthCalls)
        }

    @Test
    fun `a failed read with no copy hides the month`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(HomeSection.Loading, viewModel.uiState.value.month)

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Failed, viewModel.uiState.value.month)
        }

    @Test
    fun `a failed read keeps the device's copy`() =
        runTest {
            val kept = month(pebbles = 1)
            wraps.keptMonths[YearMonth.now()] = kept
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Ready(kept), viewModel.uiState.value.month)
        }

    @Test
    fun `a failed read on a later resume keeps the month already shown`() =
        runTest {
            val fresh = month(pebbles = 2)
            wraps.months[YearMonth.now()] = fresh
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            wraps.months.clear()
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Ready(fresh), viewModel.uiState.value.month)
            assertEquals(2, wraps.monthCalls.size)
        }

    @Test
    fun `a moment saved from Home reads the month again, without a resume`() =
        runTest {
            wraps.months[YearMonth.now()] = month(pebbles = 2)
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            val after = month(pebbles = 3)
            wraps.months[YearMonth.now()] = after
            viewModel.onMomentSaved()
            advanceUntilIdle()

            assertEquals(HomeSection.Ready(after), viewModel.uiState.value.month)
            assertEquals(2, wraps.monthCalls.size)
        }

    // MARK: - The week tap (D10)

    @Test
    fun `a week tap leaves the Path a focus on that week`() =
        runTest {
            val viewModel = viewModel()
            val monday = LocalDate.of(2026, 10, 12)

            viewModel.onWeek(monday)

            assertEquals(TabIntent.FocusWeek(monday), tabIntents.pending.value)
        }

    // MARK: - Quick actions (#1376)

    @Test
    fun `the quick actions open in the stored style`() =
        runTest {
            shortcuts.setLayout(HomeShortcutsLayout.BENTO)
            shortcuts.setStones(false)
            shortcuts.setLastUsed(HomeShortcut.PEBBLE)

            val viewModel = viewModel()

            assertEquals(
                HomeShortcutsStyle(HomeShortcutsLayout.BENTO, stones = false, lastUsed = HomeShortcut.PEBBLE),
                viewModel.uiState.value.shortcuts,
            )
        }

    @Test
    fun `a tapped quick action becomes the last used, stored and shown`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onShortcutUsed(HomeShortcut.QUICK)

            assertEquals(HomeShortcut.QUICK, shortcuts.lastUsed)
            assertEquals(HomeShortcut.QUICK, viewModel.uiState.value.shortcuts.lastUsed)
        }

    @Test
    fun `a change made in settings reaches home while it is alive`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            shortcuts.setLayout(HomeShortcutsLayout.BENTO)
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(HomeShortcutsLayout.BENTO, viewModel.uiState.value.shortcuts.layout)
        }

    // MARK: - Your first five (D11)

    private val allSlugs = ChecklistTask.entries.map { it.slug } + HomeChecklist.BLUE_SLUG

    /** The six rows as the catalog serves them, each id "id-<slug>". */
    private fun armCatalog() {
        achievements.catalog =
            allSlugs.mapIndexed { i, slug ->
                AchievementRecord(id = "id-$slug", slug = slug, family = "f", sortOrder = i, karmaReward = 0, isActive = true)
            }
    }

    private fun ids(vararg tasks: ChecklistTask): Set<String> = tasks.map { "id-${it.slug}" }.toSet()

    @Test
    fun `the checklist lands on resume after the retroactive check`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(ChecklistTask.MOMENT, ChecklistTask.SOUL)
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            val checklist = requireNotNull(viewModel.uiState.value.checklist)
            assertEquals(2, checklist.doneCount)
            assertEquals(ChecklistState.IN_PROGRESS, checklist.state)
            assertEquals("check", achievements.calls.first())
        }

    @Test
    fun `a failed checklist read keeps the last checklist`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(ChecklistTask.MOMENT)
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()
            val shown = viewModel.uiState.value.checklist

            achievements.failNextUnlocks = IOException("offline")
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(shown, viewModel.uiState.value.checklist)
        }

    @Test
    fun `a slower earlier checklist read never lands over a newer one`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(ChecklistTask.MOMENT)
            val first = CompletableDeferred<Unit>()
            val second = CompletableDeferred<Unit>()
            achievements.unlocksGates += first
            achievements.unlocksGates += second
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()
            achievements.unlocks = ids(ChecklistTask.MOMENT, ChecklistTask.PEBBLE)
            viewModel.onMomentSaved()
            advanceUntilIdle()

            second.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                2,
                viewModel.uiState.value.checklist
                    ?.doneCount,
            )

            first.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                2,
                viewModel.uiState.value.checklist
                    ?.doneCount,
            )
        }

    @Test
    fun `a moment saved from Home reads the checklist again`() =
        runTest {
            armCatalog()
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(
                0,
                viewModel.uiState.value.checklist
                    ?.doneCount,
            )

            achievements.unlocks = ids(ChecklistTask.MOMENT)
            viewModel.onMomentSaved()
            advanceUntilIdle()

            assertEquals(
                1,
                viewModel.uiState.value.checklist
                    ?.doneCount,
            )
            assertEquals(2, achievements.unlocksLoadCount)
        }

    @Test
    fun `a granted claim reloads and the box leaves Home`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(*ChecklistTask.entries.toTypedArray())
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(
                ChecklistState.CLAIMABLE,
                viewModel.uiState.value.checklist
                    ?.state,
            )

            achievements.claimResult = listOf(AchievementCheckResult(HomeChecklist.BLUE_SLUG, 50))
            achievements.unlocks = achievements.unlocks + "id-${HomeChecklist.BLUE_SLUG}"
            viewModel.claim()
            advanceUntilIdle()

            assertEquals(
                ChecklistState.DONE,
                viewModel.uiState.value.checklist
                    ?.state,
            )
            assertEquals(ClaimStatus.IDLE, viewModel.uiState.value.claim)
            assertEquals(1, achievements.claimCount)
        }

    @Test
    fun `an empty claim is not granted and says so`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(*ChecklistTask.entries.toTypedArray())
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            viewModel.claim()
            advanceUntilIdle()

            assertEquals(ClaimStatus.FAILED, viewModel.uiState.value.claim)
            assertEquals(
                ChecklistState.CLAIMABLE,
                viewModel.uiState.value.checklist
                    ?.state,
            )
        }

    @Test
    fun `a thrown claim says so and a retry clears the line`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(*ChecklistTask.entries.toTypedArray())
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            achievements.failNextClaim = IOException("offline")
            viewModel.claim()
            advanceUntilIdle()
            assertEquals(ClaimStatus.FAILED, viewModel.uiState.value.claim)

            achievements.claimResult = listOf(AchievementCheckResult(HomeChecklist.BLUE_SLUG, 50))
            viewModel.claim()
            assertEquals(ClaimStatus.CLAIMING, viewModel.uiState.value.claim)
            advanceUntilIdle()
            assertEquals(ClaimStatus.IDLE, viewModel.uiState.value.claim)
        }

    @Test
    fun `a claim already running is not sent twice`() =
        runTest {
            armCatalog()
            achievements.unlocks = ids(*ChecklistTask.entries.toTypedArray())
            achievements.claimResult = listOf(AchievementCheckResult(HomeChecklist.BLUE_SLUG, 50))
            val viewModel = viewModel()

            viewModel.claim()
            viewModel.claim()
            advanceUntilIdle()

            assertEquals(1, achievements.claimCount)
        }

    // MARK: - The tasks' calls to action (D11)

    @Test
    fun `the pebble task completes the newest bare moment`() =
        runTest {
            moments.path =
                listOf(
                    UiFixtures.pathMoment(id = "done", happenedAt = OffsetDateTime.parse("2026-10-30T10:00:00Z")),
                    UiFixtures.pathMoment(id = "bare-new", pebble = null, happenedAt = OffsetDateTime.parse("2026-10-29T10:00:00Z")),
                    UiFixtures.pathMoment(id = "bare-old", pebble = null, happenedAt = OffsetDateTime.parse("2026-10-28T10:00:00Z")),
                )
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onTaskGo(ChecklistTask.PEBBLE)
            advanceUntilIdle()

            assertEquals(listOf<HomeEffect>(HomeEffect.OpenComplete("bare-new")), effects.values)
            effects.stop()
        }

    @Test
    fun `the pebble task records one when no moment is bare, or the read fails`() =
        runTest {
            moments.path = listOf(UiFixtures.pathMoment(id = "done"))
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onTaskGo(ChecklistTask.PEBBLE)
            advanceUntilIdle()
            moments.failNext = IOException("offline")
            viewModel.onTaskGo(ChecklistTask.PEBBLE)
            advanceUntilIdle()

            assertEquals(listOf<HomeEffect>(HomeEffect.OpenCapture, HomeEffect.OpenCapture), effects.values)
            effects.stop()
        }

    @Test
    fun `the tab tasks leave their intent, then open their tab`() =
        runTest {
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.onTaskGo(ChecklistTask.SOUL)
            advanceUntilIdle()
            assertEquals(TabIntent.CreateSoul, tabIntents.pending.value)
            viewModel.onTaskGo(ChecklistTask.COLLECTION)
            advanceUntilIdle()
            assertEquals(TabIntent.CreateCollection, tabIntents.pending.value)
            viewModel.onTaskGo(ChecklistTask.GLYPH)
            advanceUntilIdle()
            assertEquals(TabIntent.CarveGlyph, tabIntents.pending.value)
            viewModel.onTaskGo(ChecklistTask.MOMENT)
            advanceUntilIdle()

            assertEquals(
                listOf(HomeEffect.OpenPeople, HomeEffect.OpenCollections, HomeEffect.OpenGlyphs, HomeEffect.OpenQuickMoment),
                effects.values,
            )
            effects.stop()
        }

    @Test
    fun `nothing of the checklist is read before the first resume`() =
        runTest {
            armCatalog()
            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.checklist)
            assertTrue(achievements.calls.isEmpty())
        }
}
