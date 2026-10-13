package app.pbbls.android.features.path

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.testing.FakeComposerMedia
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.testSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edit moment's photo swap (R6). Under Robolectric only because a pick takes a
 * `Uri`, which cannot be built on the plain JVM (#857); the ViewModel itself is
 * driven exactly as in [EditMomentViewModelTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EditMomentPhotoSwapTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class Harness(
        val read: FakeMomentReadService = FakeMomentReadService(),
        val writes: FakeMomentWriteService = FakeMomentWriteService(),
        val supabase: FakeSupabaseService = FakeSupabaseService(session = testSession("user-1")),
        val snapRepo: FakeSnapWriteRepository = FakeSnapWriteRepository(),
        val media: FakeComposerMedia = FakeComposerMedia(),
    ) {
        val viewModel =
            EditMomentViewModel(moments = read, writes = writes, supabase = supabase, snapFiles = snapRepo, media = media)
    }

    /** Loads a moment holding photo A, detaches it, picks photo B and lets B upload; returns B's snap id. */
    private fun TestScope.swapToNewPhoto(h: Harness): String {
        h.read.moments =
            mapOf("moment-1" to UiFixtures.momentDetail(snaps = listOf(MomentSnap("snap-a", "user-1/snap-a", 0))))
        h.media.processed = ProcessedImage(original = byteArrayOf(1), thumb = byteArrayOf(2))
        h.viewModel.start("moment-1")
        advanceUntilIdle()
        h.viewModel.onRemoveExistingSnap()
        h.viewModel.onPhotoPicked(Uri.parse("content://media/picked/1"))
        advanceUntilIdle()
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
        return h.snapRepo.uploaded.single()
    }

    @Test
    fun `replacing the saved photo sends the new one and deletes the old files only after the save`() =
        runTest {
            val h = Harness()
            val snapB = swapToNewPhoto(h)
            val gate = CompletableDeferred<Unit>()
            h.writes.gate = gate

            h.viewModel.save()
            runCurrent()
            assertTrue(h.snapRepo.deletedPrefixes.isEmpty())

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(PebbleSnapPayload(snapB, "user-1/$snapB", 0)),
                h.writes.saved
                    .single()
                    .snaps,
            )
            assertEquals(listOf("user-1/snap-a"), h.snapRepo.deletedPrefixes)
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `removing the new photo during a save keeps the files the save points to`() =
        runTest {
            val h = Harness()
            val snapB = swapToNewPhoto(h)
            val gate = CompletableDeferred<Unit>()
            h.writes.gate = gate

            h.viewModel.save()
            runCurrent()
            h.viewModel.onRemovePhoto()
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(PebbleSnapPayload(snapB, "user-1/$snapB", 0)),
                h.writes.saved
                    .single()
                    .snaps,
            )
            assertTrue(h.snapRepo.deletedSnapIds.isEmpty())
            assertEquals(listOf("user-1/snap-a"), h.snapRepo.deletedPrefixes)
        }
}
