package app.pbbls.android.features.pebblemedia

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
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
    }

/**
 * CompositionLocal for [SnapProcessor]. Temporary, like every `Local…` in this
 * app — #849 replaces it with a ViewModel dependency.
 */
val LocalSnapProcessor =
    staticCompositionLocalOf<SnapProcessor> {
        error("LocalSnapProcessor not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
