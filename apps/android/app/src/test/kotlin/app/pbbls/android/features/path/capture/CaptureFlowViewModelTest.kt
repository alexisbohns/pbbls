package app.pbbls.android.features.path.capture

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.pbbls.android.R
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.features.path.PathFocusRequests
import app.pbbls.android.testing.FakeCameraPhotos
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.inMemorySettings
import app.pbbls.android.testing.postgrestException
import app.pbbls.android.testing.recordEffects
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
        val photos = FakeCameraPhotos()
        val viewModel =
            CaptureFlowViewModel(
                savedState = handle,
                writes = writes,
                supabase = FakeSupabaseService(session = testSession("user-1")),
                media = media,
                focusRequests = focusRequests,
                snapRepo = snapRepo,
                haptics = HapticsPreference(inMemorySettings()),
                photos = photos,
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = true)
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.SavedThenComplete("m-9")), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `a plain save by default does not chain into complete`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start(chainsComplete = false)
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = h.viewModel.uiState.value.chainsComplete)
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.Saved), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `opened as Home's new pebble, the primary save chains into complete`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.writes.nextMomentId = "m-7"
            h.viewModel.start(chainsComplete = true)
            assertTrue(h.viewModel.uiState.value.chainsComplete)
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = h.viewModel.uiState.value.chainsComplete)
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.SavedThenComplete("m-7")), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `opened as Home's new pebble, the close dialog's save is still a plain save`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start(chainsComplete = true)
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.onCloseRequested()
            h.viewModel.onSaveFromClose()
            advanceUntilIdle()
            assertEquals(listOf(CaptureFlowEffect.Saved), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            effects.stop()
        }

    @Test
    fun `a saved capture asks the path to focus its week`() =
        runTest {
            val h = Harness()
            h.writes.nextMomentId = "M-9"
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertNull(h.focusRequests.consume())
            assertEquals(R.string.moment_error_empty, h.viewModel.uiState.value.flow.errorRes)
            assertEquals(CaptureStep.MOMENT, h.viewModel.uiState.value.flow.step)
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
            assertEquals(WhenSource.PHOTO, state.flow.whenSource)
            assertTrue(state.flow.hasSnap)
        }

    @Test
    fun `a photo-only moment sends its uploaded snap and no name`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.closeOffersSave)
            h.viewModel.onSaveFromClose()
            advanceUntilIdle()
            assertEquals(
                "Walk",
                h.writes.saved
                    .single()
                    .name,
            )
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
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
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            runCurrent()
            assertEquals(R.string.pebble_save_error_photo_uploading, h.viewModel.uiState.value.flow.errorRes)
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `a photo-only capture can be saved only once its photo is up`() =
        runTest {
            val h = Harness()
            val gate = CompletableDeferred<Unit>()
            h.snapRepo.uploadGate = gate
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            settle()
            assertFalse(h.viewModel.uiState.value.canSaveNow)
            gate.complete(Unit)
            settle()
            assertTrue(h.viewModel.uiState.value.canSaveNow)
        }

    @Test
    fun `a photo that failed to upload does not make a nameless capture savable`() =
        runTest {
            val h = Harness()
            h.snapRepo.failNext = IllegalStateException("upload refused")
            h.viewModel.onPhotoPicked(pick)
            settle()
            // The coordinator retries once after a pause; that one fails too.
            h.snapRepo.failNext = IllegalStateException("upload refused again")
            advanceUntilIdle()
            settle()
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            settle()
            assertEquals(R.string.pebble_save_error_photo_failed, h.viewModel.uiState.value.snapBlockedMessageRes)
            assertFalse(h.viewModel.uiState.value.canSaveNow)
        }

    @Test
    fun `a save from the close dialog that fails lands on the page to be seen`() =
        runTest {
            val h = Harness()
            h.writes.failNext = postgrestException("moment_empty")
            h.viewModel.machine().setName("Walk")
            h.viewModel.onCloseRequested()
            h.viewModel.onSaveFromClose()
            advanceUntilIdle()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CaptureStep.MOMENT, flow.step)
            assertEquals(R.string.moment_error_empty, flow.errorRes)
        }

    @Test
    fun `a save refused before the write still lands on the page`() =
        runTest {
            val h = Harness()
            h.snapRepo.uploadGate = CompletableDeferred()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().goTo(CaptureStep.PHOTO)
            h.viewModel.save(thenComplete = false)
            runCurrent()
            val flow = h.viewModel.uiState.value.flow
            assertEquals(CaptureStep.MOMENT, flow.step)
            assertEquals(R.string.pebble_save_error_photo_uploading, flow.errorRes)
        }

    @Test
    fun `a saved capture takes no second save, close, back or photo`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.canSaveNow)

            h.viewModel.save(thenComplete = false)
            h.viewModel.save(thenComplete = true)
            h.viewModel.onCloseRequested()
            h.viewModel.onSystemBack()
            h.viewModel.machine().back()
            h.viewModel.onPhotoPicked(pick)
            advanceUntilIdle()
            settle()

            assertEquals(1, h.writes.saved.size)
            assertEquals(listOf(CaptureFlowEffect.Saved), effects.values.filterNot { it is CaptureFlowEffect.Haptic })
            assertFalse(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertEquals(CaptureStep.MOMENT, h.viewModel.uiState.value.flow.step)
            assertTrue(h.snapRepo.uploaded.isEmpty())
            effects.stop()
        }

    @Test
    fun `discarding during an upload cancels it, so nothing lands after the cleanup`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            val gate = CompletableDeferred<Unit>()
            h.snapRepo.uploadGate = gate
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.onCloseRequested()
            h.viewModel.onDiscard()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()
            settle()
            assertTrue(h.snapRepo.uploaded.isEmpty())
            assertEquals(1, h.snapRepo.deletedSnapIds.size)
            assertNull(h.viewModel.uiState.value.snap)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `a pick still being read is something to keep, and discarding drops it`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            val read = CompletableDeferred<Unit>()
            h.media.processGate = read
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertFalse(h.viewModel.uiState.value.closeOffersSave)
            h.viewModel.onDiscard()
            advanceUntilIdle()
            read.complete(Unit)
            advanceUntilIdle()
            settle()
            assertTrue(h.snapRepo.uploaded.isEmpty())
            assertNull(h.viewModel.uiState.value.snap)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `a pick still being read blocks the save like an upload`() =
        runTest {
            val h = Harness()
            h.media.processGate = CompletableDeferred()
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setName("Walk")
            h.viewModel.onPhotoPicked(pick)
            settle()
            assertEquals(R.string.pebble_save_error_photo_uploading, h.viewModel.uiState.value.snapBlockedMessageRes)
            assertFalse(h.viewModel.uiState.value.canSaveNow)
            h.viewModel.save(thenComplete = false)
            runCurrent()
            assertTrue(h.writes.saved.isEmpty())
        }

    @Test
    fun `name, words, date, its source and step survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.machine().setName("Walk")
            first.viewModel.machine().setDescription("Along the canal.")
            first.viewModel.machine().setHappenedAt(OffsetDateTime.parse("2026-10-12T06:40:00Z"))
            first.viewModel.machine().goTo(CaptureStep.MOMENT)
            advanceUntilIdle()
            val second = Harness(handle)
            val state = second.viewModel.uiState.value.flow
            assertEquals("Walk", state.draft.name)
            assertEquals("Along the canal.", state.draft.description)
            assertEquals(OffsetDateTime.parse("2026-10-12T06:40:00Z").toInstant(), state.draft.happenedAt.toInstant())
            assertEquals(WhenSource.EDITED, state.whenSource)
            assertEquals(CaptureStep.MOMENT, state.step)
        }

    @Test
    fun `a step saved by an older build resolves to the photo`() =
        runTest {
            val handle = SavedStateHandle(mapOf("capture-step" to "WHEN"))
            assertEquals(
                CaptureStep.PHOTO,
                Harness(handle)
                    .viewModel.uiState.value.flow.step,
            )
        }

    @Test
    fun `an uploaded photo survives process death as a pending upload, on the page, with no network`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.onPhotoPicked(pick)
            settle()
            val snapId = first.snapRepo.uploaded.single()
            assertEquals(snapId, handle.get<String>("capture-snap-id"))

            val second = Harness(handle)
            // Straight away: no collector has run, nothing was awaited.
            val ui = second.viewModel.uiState.value
            assertEquals(CaptureStep.MOMENT, ui.flow.step)
            assertTrue(ui.flow.hasSnap)
            val restored = (ui.snap as FormSnap.Pending).snap
            assertEquals(snapId, restored.id)
            assertEquals(AttachedSnap.UploadState.UPLOADED, restored.state)
            assertTrue(ui.canSaveNow)
        }

    @Test
    fun `a photo still uploading is not kept across a kill`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.snapRepo.uploadGate = CompletableDeferred()
            h.viewModel.onPhotoPicked(pick)
            settle()
            assertNull(handle.get<String>("capture-snap-id"))
        }

    @Test
    fun `discarding a restored photo deletes its files, and nothing is written back`() =
        runTest {
            val handle = SavedStateHandle(mapOf("capture-snap-id" to "snap-restored", "capture-words" to "Hello"))
            val h = Harness(handle)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onCloseRequested()
            h.viewModel.onDiscard()
            advanceUntilIdle()
            settle()
            assertEquals(listOf("snap-restored"), h.snapRepo.deletedSnapIds)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            // A write after the discard must not bring the draft back.
            h.viewModel.machine().setDescription("late")
            h.viewModel.machine().setName("late")
            advanceUntilIdle()
            settle()
            assertNull(handle.get<String>("capture-snap-id"))
            assertNull(handle.get<String>("capture-words"))
            assertNull(handle.get<String>("capture-name"))
            effects.stop()
        }

    @Test
    fun `a saved moment leaves no draft behind`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.machine().setName("Walk")
            h.viewModel.machine().setDescription("Words")
            advanceUntilIdle()
            assertEquals("Words", handle.get<String>("capture-words"))
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertNull(handle.get<String>("capture-words"))
            assertNull(handle.get<String>("capture-name"))
        }

    @Test
    fun `the words are sent with the save, trimmed`() =
        runTest {
            val h = Harness()
            h.viewModel.machine().setName("Walk")
            h.viewModel.machine().setDescription("  Along the canal.  ")
            h.viewModel.save(thenComplete = true)
            advanceUntilIdle()
            assertEquals(
                "Along the canal.",
                h.writes.saved
                    .single()
                    .description,
            )
        }

    @Test
    fun `a words-only draft cannot save, and closing it asks without offering Save`() =
        runTest {
            val h = Harness()
            h.viewModel.machine().goTo(CaptureStep.MOMENT)
            h.viewModel.machine().setDescription("We sat by the canal.")
            settle()
            assertFalse(h.viewModel.uiState.value.canSaveNow)
            h.viewModel.save(thenComplete = false)
            runCurrent()
            assertTrue(h.writes.saved.isEmpty())
            h.viewModel.onCloseRequested()
            assertTrue(h.viewModel.uiState.value.isCloseConfirmPresented)
            assertFalse(h.viewModel.uiState.value.closeOffersSave)
        }

    @Test
    fun `a quick moment opens on the page with no way back to the photo`() =
        runTest {
            val h = Harness()
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.start(chainsComplete = false, startsAtMomentPage = true, promptReason = "remember")
            assertEquals(CaptureStep.MOMENT, h.viewModel.uiState.value.flow.step)
            assertEquals("remember", h.viewModel.uiState.value.promptReason)
            h.viewModel.onSystemBack()
            advanceUntilIdle()
            // Back is close: nothing was typed, so it leaves at once.
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
            effects.stop()
        }

    @Test
    fun `a quick moment sent back to the photo stays there after process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.start(chainsComplete = false, startsAtMomentPage = true)
            first.viewModel.machine().back()
            advanceUntilIdle()
            assertEquals(CaptureStep.PHOTO, first.viewModel.uiState.value.flow.step)

            val second = Harness(handle)
            second.viewModel.start(chainsComplete = false, startsAtMomentPage = true)
            assertEquals(CaptureStep.PHOTO, second.viewModel.uiState.value.flow.step)
            assertEquals(CaptureStep.PHOTO, second.viewModel.machine().step)
        }

    @Test
    fun `a fresh quick moment goes to the page, once`() =
        runTest {
            val h = Harness()
            h.viewModel.start(chainsComplete = false, startsAtMomentPage = true)
            assertEquals(CaptureStep.MOMENT, h.viewModel.uiState.value.flow.step)
            h.viewModel.machine().back()
            // A second start (the key's remember recomputed) must not drag it forward again.
            h.viewModel.start(chainsComplete = false, startsAtMomentPage = true)
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
        }

    @Test
    fun `the name and the words never end on half an emoji`() =
        runTest {
            val h = Harness()
            // A pair is two chars: 39 letters then the pair straddles the limit of 40.
            h.viewModel.machine().setName("a".repeat(39) + "\uD83D\uDE00")
            assertEquals(
                "a".repeat(39),
                h.viewModel
                    .machine()
                    .draft.name,
            )
            h.viewModel.machine().setDescription("b".repeat(279) + "\uD83D\uDE00")
            assertEquals(
                "b".repeat(279),
                h.viewModel
                    .machine()
                    .draft.description,
            )
            h.viewModel.machine().setName("a".repeat(38) + "\uD83D\uDE00")
            assertEquals(
                40,
                h.viewModel
                    .machine()
                    .draft.name.length,
            )
        }

    @Test
    fun `saving from a restored photo sends the original upload's storage path`() =
        runTest {
            val handle = SavedStateHandle()
            val first = Harness(handle)
            first.viewModel.onPhotoPicked(pick)
            settle()
            val snapId = first.snapRepo.uploaded.single()

            val second = Harness(handle)
            second.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            val sent =
                second.writes.saved
                    .single()
                    .snaps
            assertEquals(listOf(PebbleSnapPayload(snapId, "user-1/$snapId", 0)), sent)
        }

    @Test
    fun `a failed save then a discard deletes the files and leaves no draft`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().setName("Walk")
            h.writes.failNext = postgrestException("moment_empty")
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
            h.viewModel.onCloseRequested()
            h.viewModel.onDiscard()
            advanceUntilIdle()
            settle()
            assertEquals(1, h.snapRepo.deletedSnapIds.size)
            assertNull(handle.get<String>("capture-snap-id"))
            assertNull(handle.get<String>("capture-name"))
        }

    @Test
    fun `once saved, the screen keeps the photo it showed`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.save(thenComplete = false)
            advanceUntilIdle()
            settle()
            assertTrue(h.viewModel.uiState.value.snap is FormSnap.Pending)
        }

    // ---- the photo step (#1515 D3): advance on a new snap id, and the camera ----

    @Test
    fun `a new pick advances to the page after the beat`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
            advanceTimeBy(399)
            runCurrent()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
        }

    @Test
    fun `an upload finishing during the beat is not a second advance`() =
        runTest {
            val h = Harness()
            val gate = CompletableDeferred<Unit>()
            h.snapRepo.uploadGate = gate
            h.viewModel.onPhotoPicked(pick)
            settle()
            gate.complete(Unit)
            settle()
            advanceTimeBy(500)
            runCurrent()
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
        }

    @Test
    fun `a restored photo never advances`() =
        runTest {
            val handle = SavedStateHandle(mapOf("capture-snap-id" to "snap-restored", "capture-step" to "PHOTO"))
            val h = Harness(handle)
            h.viewModel.machine().goTo(CaptureStep.PHOTO)
            settle()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
        }

    @Test
    fun `back on the photo step an upload state change stays put, and a new pick advances`() =
        runTest {
            val h = Harness()
            h.snapRepo.failNext = RuntimeException("offline")
            h.viewModel.onPhotoPicked(pick)
            settle()
            advanceUntilIdle()
            settle()
            // The first pick advanced (its upload then failed); Change photo comes back.
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
            h.viewModel.machine().back()
            settle()
            h.viewModel.onRetryPhoto()
            advanceUntilIdle()
            settle()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals("FAILED to UPLOADED is the same snap", CaptureStep.PHOTO, h.viewModel.machine().step)
            val first = (h.viewModel.uiState.value.snap as FormSnap.Pending).snap.id
            h.viewModel.onPhotoPicked(pick)
            advanceUntilIdle()
            settle()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
            // The replaced photo's files go, because attach only overwrites.
            assertEquals(listOf(first), h.snapRepo.deletedSnapIds)
        }

    @Test
    fun `leaving the step during the beat cancels the advance`() =
        runTest {
            val h = Harness()
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().advance() // Just write, within the beat
            settle()
            h.viewModel.machine().back()
            settle()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
        }

    @Test
    fun `a frozen flow does not advance`() =
        runTest {
            val h = Harness()
            h.viewModel.machine().goTo(CaptureStep.PHOTO)
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.machine().beginSave()
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
        }

    @Test
    fun `discarding during the beat stays discarded`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            val effects = recordEffects(h.viewModel.effects)
            h.viewModel.onPhotoPicked(pick)
            settle()
            h.viewModel.onCloseRequested()
            h.viewModel.onDiscard()
            advanceTimeBy(1_000)
            runCurrent()
            settle()
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
            assertNull(handle.get<String>("capture-step"))
            assertEquals(1, h.snapRepo.deletedSnapIds.size)
            assertTrue(CaptureFlowEffect.Dismiss in effects.values)
            effects.stop()
        }

    @Test
    fun `the camera hands out one shot, saved for a process death`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            assertTrue(h.viewModel.uiState.value.cameraAvailable)
            assertEquals("content://app.pbbls.android.camera/camera/shot-1.jpg", h.viewModel.onCameraRequested().toString())
            assertEquals("/cache/camera/shot-1.jpg", handle.get<String>("capture-shot"))
            // A second tap while the camera is out launches nothing.
            assertNull(h.viewModel.onCameraRequested())
            assertEquals(1, h.photos.made)
        }

    @Test
    fun `no camera app, no shot`() =
        runTest {
            val h = Harness()
            h.photos.available = false
            assertNull(h.viewModel.onCameraRequested())
            assertEquals(0, h.photos.made)
        }

    @Test
    fun `a shot taken is the date, an upload and an advance, and the file is deleted`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            settle()
            val state = h.viewModel.uiState.value
            assertTrue(state.flow.hasSnap)
            assertEquals(WhenSource.PHOTO, state.flow.whenSource)
            assertEquals(1, h.snapRepo.uploaded.size)
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertNull(handle.get<String>("capture-shot"))
            assertEquals(CaptureStep.MOMENT, h.viewModel.machine().step)
        }

    @Test
    fun `a shot cancelled is deleted and the step stays`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraResult(taken = false)
            advanceUntilIdle()
            settle()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertNull(handle.get<String>("capture-shot"))
            assertFalse(h.viewModel.uiState.value.flow.hasSnap)
            assertEquals(CaptureStep.PHOTO, h.viewModel.machine().step)
        }

    @Test
    fun `a shot that cannot be read is deleted and nothing is attached`() =
        runTest {
            val h = Harness()
            h.photos.processed = null
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            settle()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertFalse(h.viewModel.uiState.value.flow.hasSnap)
            assertFalse(h.viewModel.uiState.value.snapBlockedMessageRes != null)
        }

    @Test
    fun `the camera's answer reaches a new view model after a process death`() =
        runTest {
            val handle = SavedStateHandle()
            Harness(handle).viewModel.onCameraRequested()
            // The process dies with the camera in front; the registry hands the answer to the new one.
            val second = Harness(handle)
            second.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            settle()
            assertTrue(second.viewModel.uiState.value.flow.hasSnap)
            assertEquals(1, second.snapRepo.uploaded.size)
            assertNull(handle.get<String>("capture-shot"))
        }

    @Test
    fun `a camera result with no pending shot is ignored, and a shot never answered is dropped on resume`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            assertFalse(h.viewModel.uiState.value.flow.hasSnap)
            assertTrue(h.photos.deleted.isEmpty())
            h.viewModel.onCameraRequested()
            h.viewModel.onResumed()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertNull(handle.get<String>("capture-shot"))
            // Not locked out: the camera can be asked for again.
            assertTrue(h.viewModel.onCameraRequested() != null)
        }

    @Test
    fun `a launch that finds no camera app drops the shot`() =
        runTest {
            val handle = SavedStateHandle()
            val h = Harness(handle)
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraLaunchFailed()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertNull(handle.get<String>("capture-shot"))
        }

    @Test
    fun `discarding while a shot is still being read deletes it and attaches nothing`() =
        runTest {
            val h = Harness()
            val gate = CompletableDeferred<Unit>()
            h.photos.processGate = gate
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraResult(taken = true)
            settle()
            h.viewModel.onCloseRequested()
            h.viewModel.onDiscard()
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()
            settle()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertTrue(h.snapRepo.uploaded.isEmpty())
            assertFalse(h.viewModel.uiState.value.flow.hasSnap)
        }

    @Test
    fun `a frozen flow takes no shot and deletes one that comes back`() =
        runTest {
            val h = Harness()
            h.viewModel.onCameraRequested()
            h.viewModel.machine().beginSave()
            h.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            assertEquals(listOf("/cache/camera/shot-1.jpg"), h.photos.deleted)
            assertNull(h.viewModel.onCameraRequested())
        }

    @Test
    fun `a pick over a photo still uploading replaces it and deletes its files`() =
        runTest {
            val h = Harness()
            val gate = CompletableDeferred<Unit>()
            h.snapRepo.uploadGate = gate
            h.viewModel.onPhotoPicked(pick)
            settle()
            val first = (h.viewModel.uiState.value.snap as FormSnap.Pending).snap.id
            h.snapRepo.uploadGate = null
            h.viewModel.onPhotoPicked(pick)
            advanceUntilIdle()
            settle()
            val now = (h.viewModel.uiState.value.snap as FormSnap.Pending).snap
            assertTrue(now.id != first)
            assertEquals(AttachedSnap.UploadState.UPLOADED, now.state)
            assertTrue(first in h.snapRepo.deletedSnapIds)
            assertFalse(h.viewModel.uiState.value.isPicking)
            assertNull(h.viewModel.uiState.value.snapBlockedMessageRes)
        }

    @Test
    fun `a new pick that cannot be read leaves the old photo as it was, uploading`() =
        runTest {
            val h = Harness()
            val gate = CompletableDeferred<Unit>()
            h.snapRepo.uploadGate = gate
            h.viewModel.onPhotoPicked(pick)
            settle()
            val first = (h.viewModel.uiState.value.snap as FormSnap.Pending).snap.id
            // The next pick is a corrupt image.
            h.media.processed = null
            h.viewModel.onPhotoPicked(pick)
            advanceUntilIdle()
            settle()
            assertEquals(first, (h.viewModel.uiState.value.snap as FormSnap.Pending).snap.id)
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
            // The old pick is still in flight, so the tile stays busy; the failed one added nothing.
            assertTrue(h.viewModel.uiState.value.isPicking)
            // Its upload was never cancelled: it lands, and Save is free.
            gate.complete(Unit)
            advanceUntilIdle()
            settle()
            assertFalse(h.viewModel.uiState.value.isPicking)
            assertEquals(AttachedSnap.UploadState.UPLOADED, (h.viewModel.uiState.value.snap as FormSnap.Pending).snap.state)
            assertNull(h.viewModel.uiState.value.snapBlockedMessageRes)
        }

    @Test
    fun `the picking state shows while a pick is read, and the shutter date is whole seconds`() =
        runTest {
            val h = Harness()
            val read = CompletableDeferred<Unit>()
            h.media.processGate = read
            h.viewModel.onPhotoPicked(pick)
            settle()
            assertTrue(h.viewModel.uiState.value.isPicking)
            read.complete(Unit)
            advanceUntilIdle()
            settle()
            assertFalse(h.viewModel.uiState.value.isPicking)
            h.viewModel.onCameraRequested()
            h.viewModel.onCameraResult(taken = true)
            advanceUntilIdle()
            assertEquals(0, h.viewModel.uiState.value.flow.draft.happenedAt.nano)
        }
}
