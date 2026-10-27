package app.pbbls.android.features.path

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.R
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.inMemorySettings
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** Edit moment (R5, R6): one `save_moment`, the photo removal riding it, and nothing else. */
class EditMomentViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        val read: FakeMomentReadService = FakeMomentReadService(),
        val writes: FakeMomentWriteService = FakeMomentWriteService(),
        val supabase: FakeSupabaseService = FakeSupabaseService(session = testSession("user-1")),
        val snapRepo: FakeSnapWriteRepository = FakeSnapWriteRepository(),
        val media: FakeComposerMedia = FakeComposerMedia(),
        val haptics: HapticsPreference = HapticsPreference(inMemorySettings()),
    ) {
        val viewModel =
            EditMomentViewModel(
                moments = read,
                writes = writes,
                supabase = supabase,
                snapFiles = snapRepo,
                media = media,
                haptics = haptics,
            )
    }

    @Test
    fun `saving writes the moment only, with every moment key`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(description = "Old"))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(description = ""))
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(
                MomentSavePayload(
                    id = "moment-1",
                    name = "Morning walk",
                    description = "",
                    happenedAt = OffsetDateTime.parse("2026-10-12T06:40:00Z"),
                    snaps = emptyList(),
                ),
                h.writes.saved.single(),
            )
            assertTrue(h.writes.completed.isEmpty())
            assertTrue(h.writes.karmaReads.isEmpty())
        }

    @Test
    fun `a saved photo is echoed back`() =
        runTest {
            val h = Harness()
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(
                listOf(PebbleSnapPayload("snap-1", "user-1/snap-1", 0)),
                h.writes.saved
                    .single()
                    .snaps,
            )
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
        }

    @Test
    fun `a successful save reports it`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.save()
            advanceUntilIdle()

            assertTrue(effects.values.contains(EditMomentEffect.Saved))
            effects.stop()
        }

    @Test
    fun `a blank name without a photo cannot be saved`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(pebble = null))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(name = "  "))
            assertFalse((h.viewModel.uiState.value as EditMomentUiState.Content).canSave)
            h.viewModel.save()
            advanceUntilIdle()
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `the name is clamped to forty characters`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(name = "x".repeat(60)))

            assertEquals(40, (h.viewModel.uiState.value as EditMomentUiState.Content).draft.name.length)
        }

    /** A legacy name past the clamp survives an edit that leaves the name alone. */
    @Test
    fun `a long saved name is kept when only another field changes`() =
        runTest {
            val h = Harness()
            val legacy = "y".repeat(60)
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail(name = legacy))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(description = "Added later"))
            h.viewModel.save()
            advanceUntilIdle()

            assertEquals(
                legacy,
                h.writes.saved
                    .single()
                    .name,
            )
        }

    @Test
    fun `removing the saved photo sends an empty snap set and deletes the files only after the save`() =
        runTest {
            val h = Harness()
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.onRemoveExistingSnap()
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
            h.viewModel.save()
            advanceUntilIdle()
            assertEquals(
                emptyList<PebbleSnapPayload>(),
                h.writes.saved
                    .single()
                    .snaps,
            )
            assertEquals(listOf("user-1/snap-1"), h.snapRepo.deletedPrefixes)
        }

    /** The form holds still while it saves: the in-flight payload and the files it points to stay as sent. */
    @Test
    fun `removing the saved photo during a save changes neither the payload nor its files`() =
        runTest {
            val h = Harness()
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val gate = CompletableDeferred<Unit>()
            h.writes.gate = gate
            h.viewModel.save()
            runCurrent()

            h.viewModel.onRemoveExistingSnap()
            gate.complete(Unit)
            advanceUntilIdle()
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(
                listOf(PebbleSnapPayload("snap-1", "user-1/snap-1", 0)),
                h.writes.saved
                    .single()
                    .snaps,
            )
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
        }

    @Test
    fun `edits made during a save are ignored`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.writes.gate = CompletableDeferred()
            h.viewModel.save()
            runCurrent()

            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            h.viewModel.onDraftChange(state.draft.copy(name = "Changed mid-save"))

            assertEquals("Morning walk", (h.viewModel.uiState.value as EditMomentUiState.Content).draft.name)
        }

    @Test
    fun `a moment_empty refusal shows its copy and keeps the files`() =
        runTest {
            val h = Harness()
            h.read.moments =
                mapOf("moment-1" to UiFixtures.momentDetail(name = "x", snaps = listOf(MomentSnap("snap-1", "user-1/snap-1", 0))))
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.onRemoveExistingSnap()
            h.writes.failNext = postgrestException("moment_empty")
            h.viewModel.save()
            advanceUntilIdle()
            val state = h.viewModel.uiState.value as EditMomentUiState.Content
            assertEquals(R.string.moment_error_empty, state.saveErrorRes)
            assertFalse(state.isSaving)
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())
        }

    /** A rotation re-runs the screen's `LaunchedEffect`; it must not discard edits. */
    @Test
    fun `reopening the same moment does not reload over edits in progress`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            val edited = (h.viewModel.uiState.value as EditMomentUiState.Content).draft.copy(name = "Edited")
            h.viewModel.onDraftChange(edited)

            h.viewModel.start("moment-1")
            advanceUntilIdle()

            assertEquals(listOf("moment-1"), h.read.loadedMomentIds)
            assertEquals("Edited", (h.viewModel.uiState.value as EditMomentUiState.Content).draft.name)
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            h.read.failNext = IOException("offline")
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            assertEquals(EditMomentUiState.Error, h.viewModel.uiState.value)

            h.viewModel.retry()
            advanceUntilIdle()
            assertTrue(h.viewModel.uiState.value is EditMomentUiState.Content)
        }

    @Test
    fun `dismissing reports it and writes nothing`() =
        runTest {
            val h = Harness()
            h.read.moments = mapOf("moment-1" to UiFixtures.momentDetail())
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start("moment-1")
            advanceUntilIdle()
            h.viewModel.dismiss()
            advanceUntilIdle()

            assertTrue(effects.values.contains(EditMomentEffect.Dismissed))
            assertTrue(h.writes.saved.isEmpty())
            effects.stop()
        }

    @Test
    fun `the form's pickers read Pebbles' haptics switch at each ask`() {
        val h = Harness()
        assertTrue(h.viewModel.hapticsOn)
        // Switched off in Settings while the edit is open: the next buzz asks and hears off.
        h.haptics.setOn(false)
        assertFalse(h.viewModel.hapticsOn)
    }
}
