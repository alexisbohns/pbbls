package app.pbbls.android.features.home.camera

import app.pbbls.android.R
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.data.SnapUploadCoordinator
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeCameraPhotos
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeSnapWriteRepository
import app.pbbls.android.testing.postgrestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/**
 * Home's camera moment (#1383 D7; iOS `CameraMomentCoordinatorTests`): every branch, and the shot's
 * file gone after each one.
 */
class CameraMomentTest {
    private val photos = FakeCameraPhotos()
    private val snapRepo = FakeSnapWriteRepository()
    private val writes = FakeMomentWriteService()
    private val achievements = FakeAchievementsService()
    private val landed = mutableListOf<Pair<String, OffsetDateTime>>()
    private val pendingWrites = mutableListOf<String?>()
    private var user: String? = "user-1"
    private val shutterClock = OffsetDateTime.parse("2026-10-31T14:32:07.861+01:00")

    /** Every upload fails, both tries: the coordinator's one automatic retry fails too. */
    private val brokenStorage =
        object : SnapWriteRepositing {
            val deleted = mutableListOf<String>()

            override suspend fun uploadProcessed(
                processed: ProcessedImage,
                snapId: String,
                userId: String,
            ): Unit = throw IOException("offline")

            override suspend fun deleteFiles(
                snapId: String,
                userId: String,
            ) {
                deleted += snapId
            }

            override suspend fun deleteFiles(storagePrefix: String) = Unit
        }

    private fun TestScope.camera(
        repo: SnapWriteRepositing = snapRepo,
        initialPendingShot: String? = null,
    ) = CameraMoment(
        scope = this,
        photos = photos,
        writes = writes,
        achievements = achievements,
        userId = { user },
        makeSnaps = { SnapUploadCoordinator(repo = repo, retryDelayMillis = 10, onLog = { _, _ -> }) },
        onLanded = { id, shutter -> landed += id to shutter },
        initialPendingShot = initialPendingShot,
        onPendingShot = { pendingWrites += it },
        now = { shutterClock },
    )

    @Test
    fun `a photo uploads, then saves a nameless moment at the shutter's whole second`() =
        runTest {
            writes.nextMomentId = "m-1"
            val camera = camera()

            val shot = requireNotNull(camera.start())
            camera.onResult(taken = true)
            advanceUntilIdle()

            val snapId = snapRepo.uploaded.single()
            assertEquals(
                MomentSavePayload(
                    happenedAt = OffsetDateTime.parse("2026-10-31T14:32:07+01:00"),
                    snaps = listOf(PebbleSnapPayload(snapId, "user-1/$snapId", 0)),
                ),
                writes.saved.single(),
            )
            assertNull(writes.saved.single().name)
            assertEquals(listOf("m-1" to OffsetDateTime.parse("2026-10-31T14:32:07+01:00")), landed)
            assertEquals(1, achievements.fireCheckCount)
            // The uploaded files are the moment's: nothing deleted from Storage.
            assertTrue(snapRepo.deletedSnapIds.isEmpty())
            assertEquals(listOf(shot), photos.deleted)
            assertTrue(photos.onDisk.isEmpty())
            assertEquals(CameraMomentUiState(), camera.state.value)
        }

    @Test
    fun `a cancel at the camera saves nothing and deletes the shot`() =
        runTest {
            val camera = camera()

            val shot = requireNotNull(camera.start())
            camera.onResult(taken = false)
            advanceUntilIdle()

            assertTrue(writes.saved.isEmpty())
            assertTrue(snapRepo.uploaded.isEmpty())
            assertEquals(listOf(shot), photos.deleted)
            assertTrue(photos.onDisk.isEmpty())
            assertEquals(CameraMomentUiState(), camera.state.value)
        }

    @Test
    fun `no camera shows the alert and makes no file`() =
        runTest {
            photos.available = false
            val camera = camera()

            assertNull(camera.start())

            assertTrue(camera.state.value.isUnavailable)
            assertEquals(0, photos.made)
            camera.dismissUnavailable()
            assertFalse(camera.state.value.isUnavailable)
        }

    @Test
    fun `a launch the system refuses shows the alert and deletes the shot`() =
        runTest {
            val camera = camera()

            val shot = requireNotNull(camera.start())
            camera.onLaunchFailed()

            assertTrue(camera.state.value.isUnavailable)
            assertEquals(listOf(shot), photos.deleted)
            assertTrue(photos.onDisk.isEmpty())
        }

    @Test
    fun `a failed upload saves nothing, cleans up and says so`() =
        runTest {
            val camera = camera(repo = brokenStorage)

            val shot = requireNotNull(camera.start())
            camera.onResult(taken = true)
            advanceUntilIdle()

            assertTrue(writes.saved.isEmpty())
            assertTrue(landed.isEmpty())
            assertEquals(1, brokenStorage.deleted.size)
            assertEquals(R.string.home_camera_upload_failed, camera.state.value.errorRes)
            assertFalse(camera.state.value.isSaving)
            assertEquals(listOf(shot), photos.deleted)
            assertTrue(photos.onDisk.isEmpty())
            assertEquals(0, achievements.fireCheckCount)
        }

    @Test
    fun `an unreadable photo uploads nothing and says the photo didn't upload`() =
        runTest {
            photos.processed = null
            val camera = camera()

            camera.start()
            camera.onResult(taken = true)
            advanceUntilIdle()

            assertTrue(snapRepo.uploaded.isEmpty())
            assertTrue(writes.saved.isEmpty())
            assertEquals(R.string.home_camera_upload_failed, camera.state.value.errorRes)
            assertTrue(photos.onDisk.isEmpty())
        }

    @Test
    fun `a refused save deletes the uploaded files and the shot`() =
        runTest {
            writes.failNext = postgrestException("boom")
            val camera = camera()

            camera.start()
            camera.onResult(taken = true)
            advanceUntilIdle()

            assertEquals(snapRepo.uploaded, snapRepo.deletedSnapIds)
            assertEquals(1, snapRepo.deletedSnapIds.size)
            assertTrue(landed.isEmpty())
            assertEquals(R.string.home_quick_error, camera.state.value.errorRes)
            assertTrue(photos.onDisk.isEmpty())
            camera.dismissError()
            assertNull(camera.state.value.errorRes)
        }

    @Test
    fun `no session saves nothing and deletes the shot`() =
        runTest {
            user = null
            val camera = camera()

            camera.start()
            camera.onResult(taken = true)
            advanceUntilIdle()

            assertTrue(snapRepo.uploaded.isEmpty())
            assertEquals(R.string.home_quick_error, camera.state.value.errorRes)
            assertTrue(photos.onDisk.isEmpty())
        }

    @Test
    fun `no file for the shot launches nothing and says so`() =
        runTest {
            photos.failNewShot = IOException("disk full")
            val camera = camera()

            assertNull(camera.start())

            assertEquals(R.string.home_quick_error, camera.state.value.errorRes)
        }

    @Test
    fun `a second tap while a shot is out, or while one saves, launches nothing`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            photos.processGate = gate
            val camera = camera()

            assertNotNull(camera.start())
            assertNull(camera.start())
            camera.onResult(taken = true)
            runCurrent()
            assertTrue(camera.state.value.isSaving)
            assertNull(camera.start())
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, photos.made)
            assertEquals(1, writes.saved.size)
            assertFalse(camera.state.value.isSaving)
        }

    @Test
    fun `a shot out across a process death is saved when its answer comes back`() =
        runTest {
            photos.onDisk += "/cache/camera/restored.jpg"
            val camera = camera(initialPendingShot = "/cache/camera/restored.jpg")

            camera.onResult(taken = true)
            advanceUntilIdle()

            assertEquals(1, writes.saved.size)
            assertEquals(listOf("/cache/camera/restored.jpg"), photos.deleted)
            assertEquals(listOf<String?>(null), pendingWrites)
        }

    @Test
    fun `the pending shot is kept for a process death, and cleared with its answer`() =
        runTest {
            val camera = camera()

            val shot = camera.start()
            camera.onResult(taken = false)

            assertEquals(listOf(shot, null), pendingWrites)
        }

    @Test
    fun `a resume with a shot still out drops it, so the camera is not locked out`() =
        runTest {
            val camera = camera()

            val shot = requireNotNull(camera.start())
            camera.onResumed()

            assertEquals(listOf(shot), photos.deleted)
            assertNotNull(camera.start())
            // A late answer for the dropped shot is not mistaken for the new one's.
            camera.onResumed()
            camera.onResult(taken = true)
            advanceUntilIdle()
            assertTrue(writes.saved.isEmpty())
        }

    @Test
    fun `an answer with no shot out does nothing`() =
        runTest {
            val camera = camera()

            camera.onResult(taken = true)
            advanceUntilIdle()

            assertTrue(writes.saved.isEmpty())
            assertTrue(photos.deleted.isEmpty())
        }
}
