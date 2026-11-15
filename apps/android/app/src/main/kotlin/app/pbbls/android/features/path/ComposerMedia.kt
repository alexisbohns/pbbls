package app.pbbls.android.features.path

import android.content.Context
import android.net.Uri
import app.pbbls.android.core.data.CropSource
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.data.SnapProcessor
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.features.path.valence.ValencePrewarmer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The composers' Android-only work, behind an interface (#849).
 *
 * Every method here needs a `Context`, and a `Context` in a `ViewModel` is what
 * makes it untestable on the JVM — not because holding one leaks (the
 * application context does not), but because a test cannot produce one. Pushing
 * the calls behind this seam is what lets the edit and flow ViewModel tests run
 * with no device at all.
 *
 * The photo methods take a `Uri` and a `Bitmap`, which only exist under
 * Robolectric; the fake refuses them loudly until a test arms it, rather than
 * returning a plausible-looking nothing.
 */
interface ComposerMedia {
    /**
     * A pick, read for the crop editor (#1659): the working image and its EXIF capture date, read
     * before the decode drops the metadata. Throws when the photo does not decode.
     */
    suspend fun cropSource(uri: Uri): CropSource

    /** Done in the crop editor: what the window shows, as the upload renditions. */
    suspend fun processCropped(
        source: CropSource,
        geometry: CropGeometry,
    ): ProcessedImage

    /**
     * Warm the valence fan's eighteen wobbled assets. Process-wide caches, so a
     * second flow pays nothing. Only Complete draws the fan; Capture inherits
     * the method and never calls it, which is cheaper than a second interface
     * for one method.
     */
    suspend fun prewarmValence()
}

/** [ComposerMedia] over the real processors, holding the one application context. */
@Singleton
class AndroidComposerMedia
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val snapProcessor: SnapProcessor,
        private val valencePrewarmer: ValencePrewarmer,
    ) : ComposerMedia {
        override suspend fun cropSource(uri: Uri): CropSource = snapProcessor.cropSource(context, uri)

        override suspend fun processCropped(
            source: CropSource,
            geometry: CropGeometry,
        ): ProcessedImage = snapProcessor.processCropped(source, geometry)

        override suspend fun prewarmValence() = valencePrewarmer.prewarm(context)
    }
