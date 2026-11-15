package app.pbbls.android.core.data

import android.content.Context
import android.net.Uri
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.di.DefaultDispatcher
import app.pbbls.android.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The picked-photo work, off the main thread, with the dispatcher injected
 * rather than named at the call site (#848).
 *
 * The three composers used to write `withContext(Dispatchers.IO) { … }` inline.
 * A composable cannot be injected, so the dispatcher had nowhere to come from;
 * moving the work here gives it an owner and leaves the call sites with a plain
 * `suspend fun`. [ImagePipeline] and [ExifCaptureDate] stay pure objects — this
 * only decides where they run.
 *
 * Order matters at the call sites and the reason is not obvious: read the EXIF
 * date BEFORE processing. [ImagePipeline] re-encodes with `Bitmap.compress`,
 * which writes no metadata at all, so the capture date is gone by the time
 * processed bytes exist (M42 D7).
 */
@Singleton
class SnapProcessor
    @Inject
    constructor(
        @IoDispatcher private val io: CoroutineDispatcher,
        @DefaultDispatcher private val default: CoroutineDispatcher,
    ) {
        /** The photo's EXIF capture date, or null — see [ExifCaptureDate.from]. */
        suspend fun captureDate(
            context: Context,
            uri: Uri,
        ): OffsetDateTime? = withContext(io) { ExifCaptureDate.from(context, uri) }

        /** Both renditions, re-encoded to the snap budgets — see [ImagePipeline.process]. */
        suspend fun process(
            context: Context,
            uri: Uri,
        ): ProcessedImage = withContext(io) { ImagePipeline.process(context, uri) }

        /**
         * The crop editor's working image and the EXIF date. The date is read from the original bytes
         * first, since the decode keeps no metadata (#1659, spec D8). Throws when the photo does not decode.
         */
        suspend fun cropSource(
            context: Context,
            uri: Uri,
        ): CropSource =
            withContext(io) {
                val date = ExifCaptureDate.from(context, uri)
                CropSource(ImagePipeline.decodeUpright(context, uri, CropSource.WORKING_MAX_EDGE_PX), date)
            }

        /** Done in the editor: what the window shows, as both upload renditions. CPU work, so on Default. */
        suspend fun processCropped(
            source: CropSource,
            geometry: CropGeometry,
        ): ProcessedImage =
            withContext(default) {
                val cropped = PhotoCropRenderer.render(source.image, geometry)
                try {
                    ImagePipeline.process(cropped)
                } finally {
                    cropped.recycle()
                }
            }
    }
