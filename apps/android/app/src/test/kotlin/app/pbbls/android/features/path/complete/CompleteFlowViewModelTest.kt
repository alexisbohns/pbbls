package app.pbbls.android.features.path.complete

import app.pbbls.android.R
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.KarmaNotificationService
import app.pbbls.android.core.data.TapHaptic
import app.pbbls.android.core.model.CompleteSnapshot
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.PebbleAnswers
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.PathFocusRequests
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCompleteSnapshotStore
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeRecordFlowMemory
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.inMemorySettings
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val focusRequests = PathFocusRequests()
        val memory = FakeRecordFlowMemory()
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
                focusRequests = focusRequests,
                haptics = HapticsPreference(inMemorySettings()),
                memory = memory,
            )

        fun answerEverything(valence: Valence = Valence.LOWLIGHT_MEDIUM) {
            val m = viewModel.machine()
            m.selectValence(valence)
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
                PebbleCompletePayload("m-1", "e-1", 2, -1, Visibility.PRIVATE, listOf("d-1"), emptyList(), emptyList()),
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
    fun `a high-distress completion still succeeds but checks no achievements`() =
        runTest {
            val h = Harness(backgroundScope)
            h.write.karma = 5
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything(Valence.LOWLIGHT_LARGE)
            h.viewModel.complete()
            advanceUntilIdle()

            // The quiet flag reached the model: no success buzz.
            assertTrue(CompleteFlowEffect.Haptic(TapHaptic.SUCCESS) !in effects.values)
            effects.stop()

            assertEquals(0, h.achievements.fireCheckCount)
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CompleteStep.SUCCESS, flow.step)
            // The server still granted the karma; only the client celebration is withheld.
            assertEquals(5, flow.completed?.karmaDelta)
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
    fun `a completion asks the path to show the moment's week`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.viewModel.complete()
            advanceUntilIdle()
            assertEquals("m-1", h.focusRequests.consume())
        }

    @Test
    fun `a refused completion asks the path for nothing`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            h.write.failOnComplete = postgrestException("collection_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.complete()
            advanceUntilIdle()
            assertNull(h.focusRequests.consume())
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
    fun `a refusal right after the last pick still keeps that pick on the device`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            advanceUntilIdle()
            h.answerEverything()
            // The write outlasts the debounce, which skips while completing, so
            // the privacy pick is only in memory when the write fails.
            val gate = CompletableDeferred<Unit>()
            h.write.gate = gate
            h.write.failOnComplete = postgrestException("collection_not_owned", sqlState = "42501", status = HttpStatusCode.Forbidden)
            h.viewModel.complete()
            advanceTimeBy(AUTOSAVE_DEBOUNCE_MS + 1)
            assertEquals(
                null,
                h.snapshots.stored["m-1"]
                    ?.second
                    ?.visibility,
            )
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                Visibility.PRIVATE,
                h.snapshots.stored["m-1"]
                    ?.second
                    ?.visibility,
            )
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
    fun `a restore never lands over a pick made while it waited`() =
        runTest {
            val h = Harness(backgroundScope)
            h.snapshots.save("m-1", "user-1", CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_SMALL, emotionId = "e-1")))
            h.viewModel.start("m-1")
            h.viewModel.machine().selectValence(Valence.LOWLIGHT_LARGE)
            advanceUntilIdle()
            assertEquals(Valence.LOWLIGHT_LARGE, h.viewModel.uiState.value.flow.answers.valence)
            assertNull(h.viewModel.uiState.value.flow.answers.emotionId)
        }

    @Test
    fun `a restored snapshot with a valence restores it, one without shows none`() =
        runTest {
            val withValence = Harness(backgroundScope)
            withValence.snapshots.save("m-1", "user-1", CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_SMALL)))
            withValence.viewModel.start("m-1")
            advanceUntilIdle()
            assertEquals(Valence.HIGHLIGHT_SMALL, withValence.viewModel.uiState.value.flow.answers.valence)
            assertEquals(CompleteStep.EMOTION, withValence.viewModel.uiState.value.flow.step)

            val without = Harness(backgroundScope)
            without.snapshots.save("m-1", "user-1", CompleteSnapshot.from(PebbleAnswers(soulIds = listOf("s-1"))))
            without.refs.souls = listOf(UiFixtures.soul(id = "s-1"))
            without.viewModel.start("m-1")
            advanceUntilIdle()
            assertNull(without.viewModel.uiState.value.flow.answers.valence)
            assertEquals(CompleteStep.VALENCE, without.viewModel.uiState.value.flow.step)
        }

    @Test
    fun `a first pebble opens the help once, marks it seen on open, and a back-out never reopens it`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.start("m-1")
            // Not yet: the read waits behind the restore, and nothing is open until the step asks.
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
            advanceUntilIdle()
            assertEquals(1, h.read.loadPathCount)
            assertTrue(h.viewModel.uiState.value.isFirstPebble)
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
            assertFalse(h.memory.isValenceHelpSeen("user-1"))

            h.viewModel.showFirstPebbleHelp()
            assertTrue(h.viewModel.uiState.value.valenceHelpOpen)
            assertTrue(h.memory.isValenceHelpSeen("user-1"))

            h.viewModel.closeValenceHelp()
            h.viewModel.showFirstPebbleHelp()
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
        }

    @Test
    fun `a completed pebble on the account keeps the help closed`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.path = listOf(UiFixtures.pathMoment(id = "old", name = "Old"))
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
            h.viewModel.showFirstPebbleHelp()
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
            assertFalse(h.memory.isValenceHelpSeen("user-1"))
        }

    @Test
    fun `a failed read keeps the help closed and unmarked`() =
        runTest {
            val h = Harness(backgroundScope)
            h.read.failPath = java.io.IOException("offline")
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
            h.viewModel.showFirstPebbleHelp()
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
            assertFalse(h.memory.isValenceHelpSeen("user-1"))
        }

    @Test
    fun `no session means no read and no help`() =
        runTest {
            val h = Harness(backgroundScope)
            h.supabase.session = null
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertEquals(0, h.read.loadPathCount)
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
            h.viewModel.showFirstPebbleHelp()
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
        }

    @Test
    fun `a seen flag means no read at all`() =
        runTest {
            val h = Harness(backgroundScope)
            h.memory.markValenceHelpSeen("user-1")
            h.viewModel.start("m-1")
            advanceUntilIdle()
            assertEquals(0, h.read.loadPathCount)
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
        }

    @Test
    fun `a slow read never holds the restore up`() =
        runTest {
            val h = Harness(backgroundScope)
            h.snapshots.save("m-1", "user-1", CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_SMALL)))
            val gate = CompletableDeferred<Unit>()
            h.read.pathGate = gate
            h.viewModel.start("m-1")
            advanceUntilIdle()
            // The read is parked, the draft is already back.
            assertEquals(Valence.HIGHLIGHT_SMALL, h.viewModel.uiState.value.flow.answers.valence)
            assertFalse(h.viewModel.uiState.value.isFirstPebble)
            gate.complete(Unit)
            advanceUntilIdle()
            assertTrue(h.viewModel.uiState.value.isFirstPebble)
        }

    @Test
    fun `the callout opens the help without touching the flag`() =
        runTest {
            val h = Harness(backgroundScope)
            h.viewModel.openValenceHelp()
            assertTrue(h.viewModel.uiState.value.valenceHelpOpen)
            assertFalse(h.memory.isValenceHelpSeen("user-1"))
            h.viewModel.closeValenceHelp()
            assertFalse(h.viewModel.uiState.value.valenceHelpOpen)
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
