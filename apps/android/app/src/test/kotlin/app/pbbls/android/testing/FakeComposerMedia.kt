package app.pbbls.android.testing

import android.graphics.Bitmap
import android.net.Uri
import app.pbbls.android.core.data.CropSource
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.features.path.ComposerMedia
import kotlinx.coroutines.CompletableDeferred
import java.time.OffsetDateTime

/**
 * [ComposerMedia] for a ViewModel test (#849).
 *
 * The photo methods throw until [processed] is armed rather than returning a plausible nothing: a test
 * that reaches them unarmed has wandered into territory it does not assert on. Armed, a pick reads as a
 * small Robolectric bitmap (so the crop editor opens on it), and Done returns [processed].
 */
class FakeComposerMedia : ComposerMedia {
    var prewarmCount = 0
        private set

    /** What [processCropped] returns once armed; unarmed, both photo methods throw as above. */
    var processed: ProcessedImage? = null

    /** Holds [cropSource] until completed, for "a pick still being read" cases. */
    var loadGate: CompletableDeferred<Unit>? = null

    /** Gates taken one per [cropSource] call, first come first served, before [loadGate]: picks read out of order. */
    val loadGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** Holds [processCropped] until completed, for "a cropped photo still being processed" cases. */
    var processGate: CompletableDeferred<Unit>? = null

    /** Gates taken one per [processCropped] call, first come first served, before [processGate]. */
    val processGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The pick's EXIF date, carried by the crop source; none by default. */
    var captureDateResult: OffsetDateTime? = null

    /** Thrown by the next [cropSource], then cleared: a photo that does not decode. */
    var failNextRead: Exception? = null

    /** The geometry each Done rendered, in order. */
    val renderedGeometries = mutableListOf<CropGeometry>()

    override suspend fun cropSource(uri: Uri): CropSource {
        if (processed == null) error("the photo path is not armed: set processed first")
        (loadGates.removeFirstOrNull() ?: loadGate)?.await()
        failNextRead?.let {
            failNextRead = null
            throw it
        }
        return CropSource(image = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888), captureDate = captureDateResult)
    }

    override suspend fun processCropped(
        source: CropSource,
        geometry: CropGeometry,
    ): ProcessedImage {
        val armed = processed ?: error("the photo path is not armed: set processed first")
        renderedGeometries += geometry
        (processGates.removeFirstOrNull() ?: processGate)?.await()
        return armed
    }

    override suspend fun prewarmValence() {
        prewarmCount += 1
    }
}
