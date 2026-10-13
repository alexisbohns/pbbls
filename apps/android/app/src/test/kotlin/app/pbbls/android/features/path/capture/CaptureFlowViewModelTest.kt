package app.pbbls.android.features.path.capture

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.R
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.features.path.PathFocusRequests
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime

/**
 * Capture's orchestration (A-2, A-3, R2, R14). Under Robolectric because a
 * photo pick takes a `Uri`, which cannot be built on the plain JVM (#857).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CaptureFlowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        handle: SavedStateHandle = SavedStateHandle(),
    ) {
        val writes = FakeMomentWriteService()
        val snapRepo = FakeSnapWriteRepository()
        val media = FakeComposerMedia().apply { processed = ProcessedImage(original = byteArrayOf(1), thumb = byteArrayOf(2)) }
        val focusRequests = PathFocusRequests()
        val viewModel =
            CaptureFlowViewModel(
                savedState = handle,
                writes = writes,
                supabase = FakeSupabaseService(session = testSession("user-1")),
                media = media,
                focusRequests = focusRequests,
                snapRepo = snapRepo,
            )
    }

    private val pick: Uri = Uri.parse("content://media/picked/1")

    /** The coordinator's photo state reaches the flow through `snapshotFlow`, which waits for an apply. */
    private fun TestScope.settle() {
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Test
    fun `saving sends a whole-second save_moment and dismisses`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().setHappenedAt(OffsetDateTime.parse("2026-10-12T08:40:00.5+02:00"))
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Swim at dawn")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertEquals(
                MomentSavePayload(name = "Swim at dawn", happenedAt = OffsetDateTime.parse("2026-10-12T08:40:00.5+02:00")),
                h.writes.saved.single(),
            )
            assertEquals(listOf(CaptureFlowEffect.Saved), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `make it a pebble now saves, then hands over the new moment id`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.writes.nextMomentId = "m-9"
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = true)
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.SavedThenComplete("m-9")), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `a saved capture asks the path to focus its week`() =
        runTest {
            val h = Harness()
            h.writes.nextMomentId = "M-9"
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertEquals("m-9", h.focusRequests.consume())
        }

    @Test
    fun `a failed save asks the path for nothing`() =
        runTest {
            val h = Harness()
            h.writes.failNext = postgrestException("moment_empty")
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertNull(h.focusRequests.consume())
            assertEquals(R.string.moment_error_empty, h.viewModel.uiState.value.flow.errorRes)
            assertEquals(CaptureStep.NAME, h.viewModel.uiState.value.flow.step)
        }

    @Test
    fun `a photo's capture date seeds when and says so`() =
        runTest {
            val h = Harness()
            h.media.captureDateResult = OffsetDateTime.parse("2026-10-11T18:05:27Z")
            h.viewModel.onPhotoPicked(pick)
            settle()
            val state = h.viewModel.uiState.value
            assertEquals(OffsetDateTime.parse("2026-10-11T18:05:27Z"), state.flow.draft.happenedAt)
            assertTrue(state.seededFromPhoto)
            assertTrue(state.flow.hasSnap)
        }

    @Test
    fun `a photo-only moment sends its uploaded snap and no name`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            val saved = h.writes.saved.single()
            assertNull(saved.name)
            val snapId = h.snapRepo.uploaded.single()
            assertEquals(listOf(PebbleSnapPayload(snapId, "user-1/$snapId", 0)), saved.snaps)
            // The files now belong to the moment.
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `a failed save keeps the uploaded files for the retry`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.writes.failNext = postgrestException("media_quota_exceeded")
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertEquals(R.string.moment_error_media_quota, h.viewModel.uiState.value.flow.errorRes)
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `discarding asks first when there is input, and deletes the uploaded files`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.isCloseConfirmPresented)
            // A photo alone is savable, so the dialog offers Save moment too (R2).
            assertTrue(h.viewModel.uiState.value.closeOffersSave)
            h.viewModel.onDiscard()
            advanceUntilIdle()
            assertEquals(1, h.snapRepo.deletedSnapIds.size)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            assertTrue(h.writes.saved.isEmpty())
            effects.stop()
        }

    @Test
    fun `keep going closes the dialog and stays`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().setName("Walk")
            h.viewModel.onCloseRequested()
            h.viewModel.onKeepGoing()
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertTrue(effects.values.none { it is CaptureFlowEffect.Dismiss })
            effects.stop()
        }

    @Test
    fun `saving from the close dialog saves the moment`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.closeOffersSave)
            h.viewModel.onSaveFromClose()
            advanceUntilIdle()
            assertEquals("Walk", h.writes.saved.single().name)
            assertTrue(CaptureFlowEffect.Saved in effects.values)
            effects.stop()
        }

    @Test
    fun `an upload in flight keeps Save moment out of the close dialog`() =
        runTest {
            val h = Harness()
            h.snapRepo.uploadGate = CompletableDeferred()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertFalse(h.viewModel.uiState.value.closeOffersSave)
        }

    @Test
    fun `closing an empty capture does not ask`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onCloseRequested()
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `back walks the steps and closes from the photo`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().goTo(CaptureStep.WHEN)
            h.viewModel.onSystemBack()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
            h.viewModel.onSystemBack()
            advanceUntilIdle()
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `an upload in flight blocks the save with its copy`() =
        runTest {
            val h = Harness()
            h.snapRepo.uploadGate = CompletableDeferred()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().goTo(CaptureStep.NAME)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            runCurrent()
            assertEquals(R.string.pebble_save_error_photo_uploading, h.viewModel.uiState.value.flow.errorRes)
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `name, date and step survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.machine().setName("Walk")
            first.viewModel.machine().setHappenedAt(OffsetDateTime.parse("2026-10-12T06:40:00Z"))
            first.viewModel.machine().goTo(CaptureStep.NAME)
            advanceUntilIdle()
            val second = Harness(handle)
            val state = second.viewModel.uiState.value.flow
            assertEquals("Walk", state.draft.name)
            assertEquals(OffsetDateTime.parse("2026-10-12T06:40:00Z").toInstant(), state.draft.happenedAt.toInstant())
            assertEquals(CaptureStep.NAME, state.step)
        }
}
