package app.pbbls.android.features.home.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.data.SnapProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "home-camera"

/** The `cache/` sub-folder `res/xml/camera_paths.xml` shares with the camera app. */
private const val CAMERA_DIR = "camera"

/**
 * The camera moment's Android-only work, behind an interface so [CameraMoment] runs on the JVM
 * (the `ComposerMedia` pattern, #849). A shot is a file path under `cache/camera/`; the camera app
 * writes into it through a `FileProvider` URI (authority `${applicationId}.camera`, the manifest's
 * provider), so the app needs no `CAMERA` permission: the system camera holds it (#1383 D7).
 */
interface CameraPhotos {
    /** A camera and an app that answers `ACTION_IMAGE_CAPTURE`; false shows "No camera yet". */
    fun isAvailable(): Boolean

    /** A fresh empty file for the next shot, as a path. Throws when the cache cannot take one. */
    fun newShot(): String

    /** The content URI the camera app writes [shot] through. */
    fun uriFor(shot: String): Uri

    /** Decode, downscale and re-encode the shot into the upload renditions (as Capture's pick). */
    suspend fun process(shot: String): ProcessedImage

    /** Removes the shot's file. Never throws: a file left behind is logged, and the cache is the OS's to clear. */
    fun delete(shot: String)
}

/** [CameraPhotos] over the real file system, package manager and [SnapProcessor]. */
@Singleton
class AndroidCameraPhotos
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val snapProcessor: SnapProcessor,
    ) : CameraPhotos {
        override fun isAvailable(): Boolean {
            val packages = context.packageManager
            // The `<queries>` entry in the manifest is what makes a camera app visible to this lookup.
            return packages.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packages) != null
        }

        override fun newShot(): String {
            val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            check(file.createNewFile()) { "camera shot file already exists" }
            return file.absolutePath
        }

        override fun uriFor(shot: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", File(shot))

        override suspend fun process(shot: String): ProcessedImage = snapProcessor.process(context, Uri.fromFile(File(shot)))

        override fun delete(shot: String) {
            val file = File(shot)
            if (!file.delete() && file.exists()) Log.w(TAG, "could not delete a camera shot")
        }
    }
