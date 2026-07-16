package app.pbbls.android.features.pebblemedia

import app.pbbls.android.features.pebblemedia.models.AttachedSnap
import app.pbbls.android.features.pebblemedia.models.FormSnap
import app.pbbls.android.services.SnapWriteRepositing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * [SnapUploadCoordinator] — mirrors the iOS coordinator suite (design D3):
 * upload success/retry/failure transitions, id-guarded state writes,
 * compensating deletes on every abandon path, and the payload gate.
 * `runTest` virtual time makes the 2s retry delay free.
 */
class SnapUploadCoordinatorTest {
    private val processed = ProcessedImage(original = ByteArray(10), thumb = ByteArray(4))
    private val userId = "11111111-1111-1111-1111-111111111111"

    private class FakeRepo(
        var failuresBeforeSuccess: Int = 0,
    ) : SnapWriteRepositing {
        var uploadCount = 0
        var uploadGate: CompletableDeferred<Unit>? = null
        val deletedSnapIds = mutableListOf<String>()
        val deletedPrefixes = mutableListOf<String>()
        var deletePebbleMediaCalls = 0
        var deletePebbleMediaError: Exception? = null
        var deletePebbleMediaResult = "user/snap"

        override suspend fun uploadProcessed(
            processed: ProcessedImage,
            snapId: String,
            userId: String,
        ) {
            uploadCount += 1
            uploadGate?.await()
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess -= 1
                throw IOException("upload failed")
            }
        }

        override suspend fun deleteFiles(
            snapId: String,
            userId: String,
        ) {
            deletedSnapIds += snapId
        }

        override suspend fun deleteFiles(storagePrefix: String) {
            deletedPrefixes += storagePrefix
        }

        override suspend fun deletePebbleMedia(snapId: String): String {
            deletePebbleMediaCalls += 1
            deletePebbleMediaError?.let { throw it }
            return deletePebbleMediaResult
        }
    }

    private fun coordinator(
        repo: FakeRepo,
        initial: FormSnap? = null,
    ) = SnapUploadCoordinator(repo = repo, initialSnap = initial, retryDelayMillis = 0L, onLog = { _, _ -> })

    private fun SnapUploadCoordinator.pendingState(): AttachedSnap.UploadState? = (formSnap as? FormSnap.Pending)?.snap?.state

    @Test
    fun `attach uploads once and lands on UPLOADED`() =
        runTest {
            val repo = FakeRepo()
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            assertEquals(AttachedSnap.UploadState.UPLOADED, coordinator.pendingState())
            assertEquals(1, repo.uploadCount)
            assertFalse(coordinator.isBlocking)
            assertNotNull(coordinator.pendingSnapForPayload())
        }

    @Test
    fun `one transient failure auto-retries to UPLOADED`() =
        runTest {
            val repo = FakeRepo(failuresBeforeSuccess = 1)
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            assertEquals(AttachedSnap.UploadState.UPLOADED, coordinator.pendingState())
            assertEquals(2, repo.uploadCount)
        }

    @Test
    fun `two failures land on FAILED with bytes retained for retry`() =
        runTest {
            val repo = FakeRepo(failuresBeforeSuccess = 2)
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            assertEquals(AttachedSnap.UploadState.FAILED, coordinator.pendingState())
            assertEquals(2, repo.uploadCount)
            assertTrue(coordinator.hasFailed)
            assertTrue(coordinator.isBlocking)
            assertNull(coordinator.pendingSnapForPayload())
            assertNotNull(coordinator.processedForRetry)
        }

    @Test
    fun `user-tapped retry after FAILED succeeds`() =
        runTest {
            val repo = FakeRepo(failuresBeforeSuccess = 2)
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            coordinator.retryCurrent(userId)
            assertEquals(AttachedSnap.UploadState.UPLOADED, coordinator.pendingState())
            assertEquals(3, repo.uploadCount)
        }

    @Test
    fun `removePending clears state and fires the compensating delete`() =
        runTest {
            val repo = FakeRepo()
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            val snapId = (coordinator.formSnap as FormSnap.Pending).snap.id
            coordinator.removePending(userId)
            assertNull(coordinator.formSnap)
            assertNull(coordinator.processedForRetry)
            assertEquals(listOf(snapId), repo.deletedSnapIds)
        }

    @Test
    fun `cancelAndCleanup with a pending snap clears and deletes`() =
        runTest {
            val repo = FakeRepo()
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            coordinator.cancelAndCleanup(userId)
            assertNull(coordinator.formSnap)
            assertEquals(1, repo.deletedSnapIds.size)
        }

    @Test
    fun `cancelAndCleanup with no snap is a clean no-op`() =
        runTest {
            val repo = FakeRepo()
            val coordinator = coordinator(repo)
            coordinator.cancelAndCleanup(userId)
            assertNull(coordinator.formSnap)
            assertTrue(repo.deletedSnapIds.isEmpty())
        }

    @Test
    fun `handleSaveFailure deletes files but keeps the form snap`() =
        runTest {
            val repo = FakeRepo()
            val coordinator = coordinator(repo)
            coordinator.attach(processed, userId)
            coordinator.handleSaveFailure(userId)
            assertNotNull(coordinator.formSnap)
            assertEquals(1, repo.deletedSnapIds.size)
        }

    @Test
    fun `removeExisting calls the RPC then cleans storage by returned path`() =
        runTest {
            val repo = FakeRepo()
            repo.deletePebbleMediaResult = "uid/snapid"
            val coordinator = coordinator(repo, initial = FormSnap.Existing(id = "snapid", storagePath = "uid/snapid"))
            coordinator.removeExisting()
            assertEquals(1, repo.deletePebbleMediaCalls)
            assertEquals(listOf("uid/snapid"), repo.deletedPrefixes)
            assertNull(coordinator.formSnap)
        }

    @Test
    fun `removeExisting RPC failure propagates and keeps the snap`() =
        runTest {
            val repo = FakeRepo()
            repo.deletePebbleMediaError = IOException("rpc down")
            val coordinator = coordinator(repo, initial = FormSnap.Existing(id = "snapid", storagePath = "uid/snapid"))
            var thrown = false
            try {
                coordinator.removeExisting()
            } catch (_: IOException) {
                thrown = true
            }
            assertTrue(thrown)
            assertNotNull(coordinator.formSnap)
            assertTrue(repo.deletedPrefixes.isEmpty())
        }

    @Test
    fun `a removal mid-upload is not resurrected by the late completion`() =
        runTest {
            val repo = FakeRepo()
            val gate = CompletableDeferred<Unit>()
            repo.uploadGate = gate
            val coordinator = coordinator(repo)
            val upload = launch { coordinator.attach(processed, userId) }
            runCurrent()
            assertTrue(coordinator.isUploading)
            coordinator.removePending(userId)
            gate.complete(Unit)
            upload.join()
            // The id-guarded state write must not bring the chip back.
            assertNull(coordinator.formSnap)
        }

    @Test
    fun `seedExisting replaces the form snap`() =
        runTest {
            val coordinator = coordinator(FakeRepo())
            coordinator.seedExisting(FormSnap.Existing(id = "s", storagePath = "u/s"))
            assertTrue(coordinator.formSnap is FormSnap.Existing)
        }

    @Test
    fun `storage prefix helper lowercases both segments`() {
        val snap = AttachedSnap(id = "ABC-DEF", localThumb = ByteArray(0), state = AttachedSnap.UploadState.UPLOADED)
        assertEquals("user-id/abc-def", snap.storagePrefix(userId = "USER-ID"))
    }
}
