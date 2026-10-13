package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.model.CompleteSnapshot
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCompleteSnapshotStore
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CompleteFlowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        scope: CoroutineScope,
    ) {
        val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1", name = "Swim", pebble = null)))
        val write = FakeMomentWriteService()
        val refs = FakeReferenceDataService(hasLoaded = true)
        val snapshots = FakeCompleteSnapshotStore()
        val achievements = FakeAchievementsService()
        val supabase = FakeSupabaseService(session = testSession("user-1"))
        val viewModel =
            CompleteFlowViewModel(
                reads = read,
                writes = write,
                refs = refs,
                snapshots = snapshots,
                karma = KarmaNotificationService(scope),
                achievements = achievements,
                supabase = supabase,
                media = FakeComposerMedia(),
            )

        fun answerEverything() {
            val m = viewModel.machine()
            m.seedValenceIfNeeded()
            m.selectValence(Valence.LOWLIGHT_LARGE)
            m.advance()
            m.selectEmotion("e-1")
            m.selectDomain("d-1")
            m.advance()
            m.advance()
            m.selectVisibility(Visibility.PRIVATE)
        }
    }

    @Test
    fun `completing sends every key, clears the autosave, reads karma and glyph, then checks achievements once`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.glyph = UiFixtures.pebbleGlyph()
            h.write.karma = 5
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.viewModel.complete()
            advanceUntilIdle()

            assertEquals(
                PebbleCompletePayload("m-1", "e-1", 3, -1, Visibility.PRIVATE, listOf("d-1"), emptyList(), emptyList()),
                h.write.completed.single(),
            )
            assertTrue(h.snapshots.stored.isEmpty())
            assertEquals(listOf("pebble-new" to KarmaReason.PEBBLE_CREATED), h.write.karmaReads)
            assertEquals(listOf("pebble-new"), h.read.resolvedGlyphFor)
            assertEquals(1, h.achievements.fireCheckCount)
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.SUCCESS, flow.step)
            assertEquals(CompleteResult("pebble-new", 5, UiFixtures.pebbleGlyph()), flow.completed)
            assertEquals("Swim", h.viewModel.uiState.value.momentName)
        }

    @Test
    fun `a completion does not leave an autosave behind, even one debounced before it`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS / 2)
            h.viewModel.complete()
            advanceUntilIdle()
            assertTrue(h.snapshots.stored.isEmpty())
        }

    @Test
    fun `a missing glyph or karma row still reaches success`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.glyph = null
            h.write.karma = null
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.viewModel.complete()
            advanceUntilIdle()
            assertEquals(CompleteResult("pebble-new", null, null), h.viewModel.uiState.value.flow.completed)
        }

    @Test
    fun `a refusal stays on privacy with its copy and keeps the autosave`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
            h.write.failOnComplete = postgrestException("collection_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.complete()
            advanceUntilIdle()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.PRIVACY, flow.step)
            assertEquals(R.string.pebble_error_collection_gone, flow.errorRes)
            assertEquals(
                "e-1",
                h.snapshots.stored["m-1"]
                    ?.second
                    ?.emotionId,
            )
            assertEquals(0, h.achievements.fireCheckCount)
        }

    @Test
    fun `answers autosave per moment after the debounce`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.machine().goTo(CompleteStep.EMOTION)
            h.viewModel.machine().selectEmotion("e-1")
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS - 1)
            assertNull(h.snapshots.stored["m-1"])
            advanceTimeBy(2)
            assertEquals("user-1" to CompleteSnapshot(emotionId = "e-1", visibility = Visibility.SECRET), h.snapshots.stored["m-1"])
        }

    @Test
    fun `reopening restores silently at the first gap, without vanished souls`() =
        runTest {
            val h = Harness(backgroundScope)
            h.refs.souls = listOf(UiFixtures.soul(id = "s-1"))
            h.snapshots.save(
                "m-1",
                "user-1",
                CompleteSnapshot.from(
                    PebbleAnswers(valence = Valence.HIGHLIGHT_SMALL, emotionId = "e-1", soulIds = listOf("s-1", "s-gone")),
                ),
            )
            h.viewModel.start("m-1")
            advanceUntilIdle()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.DOMAIN, flow.step)
            assertEquals(listOf("s-1"), flow.answers.soulIds)
        }

    @Test
    fun `closing keeps the answers and dismisses without asking`() =
        runTest {
            val h = Harness(backgroundScope)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.machine().goTo(CompleteStep.EMOTION)
            h.viewModel.machine().selectEmotion("e-1")
            h.viewModel.onClose()
            advanceUntilIdle()
            assertEquals(
                "e-1",
                h.snapshots.stored["m-1"]
                    ?.second
                    ?.emotionId,
            )
            assertTrue(CompleteFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `back on the first step closes`() =
        runTest {
            val h = Harness(backgroundScope)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.viewModel.onSystemBack()
            advanceUntilIdle()
            assertTrue(CompleteFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `start is guarded so a rotation does not reload or restore`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertEquals(listOf("m-1"), h.read.loadedMomentIds)
        }
}
