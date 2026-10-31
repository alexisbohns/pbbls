package app.pbbls.android.testing

import android.net.Uri
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.features.home.camera.CameraPhotos
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [CameraPhotos] (#1383 D7): shots are names in a set rather than files, so a test sees
 * exactly which ones were made and which were deleted. [uriFor] needs Robolectric (a `Uri`); the
 * holder's own tests never call it.
 */
class FakeCameraPhotos : CameraPhotos {
    /** Whether a camera and a camera app are there; false is "No camera yet". */
    var available = true

    /** Shots made and not yet deleted: what would be left in `cache/camera/`. */
    val onDisk = mutableSetOf<String>()

    val deleted = mutableListOf<String>()

    var made = 0
        private set

    /** What [process] returns; null makes it throw, as an unreadable photo. */
    var processed: ProcessedImage? = ProcessedImage(original = byteArrayOf(1), thumb = byteArrayOf(2))

    /** Thrown by the next [newShot], then cleared. */
    var failNewShot: Exception? = null

    /** Holds [process] until completed, for "still saving" cases. */
    var processGate: CompletableDeferred<Unit>? = null

    override fun isAvailable(): Boolean = available

    override fun newShot(): String {
        failNewShot?.let {
            failNewShot = null
            throw it
        }
        made += 1
        return "/cache/camera/shot-$made.jpg".also { onDisk += it }
    }

    override fun uriFor(shot: String): Uri = Uri.parse("content://app.pbbls.android.camera/camera/${shot.substringAfterLast('/')}")

    override suspend fun process(shot: String): ProcessedImage {
        processGate?.await()
        return processed ?: error("unreadable photo")
    }

    override fun delete(shot: String) {
        onDisk -= shot
        deleted += shot
    }
}
