package app.pbbls.android.features.path

import android.content.Context
import android.net.Uri
import app.pbbls.android.features.path.valence.ValencePrewarmer
import app.pbbls.android.features.pebblemedia.ProcessedImage
import app.pbbls.android.features.pebblemedia.SnapProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The composers' Android-only work, behind an interface (#849).
 *
 * Every method here needs a `Context`, and a `Context` in a `ViewModel` is what
 * makes it untestable on the JVM — not because holding one leaks (the
 * application context does not), but because a test cannot produce one. Pushing
 * the three calls behind this seam is what lets `RecordFlowViewModelTest` drive
 * publish, resume and the draft lifecycle with no device at all.
 *
 * The photo methods stay untested even so: they take a `Uri`, which cannot be
 * constructed off device either. They are covered when Robolectric lands (#857);
 * the fake refuses them loudly in the meantime rather than returning a
 * plausible-looking nothing.
 */
interface ComposerMedia {
    /** EXIF capture date, or null when the pick carries no metadata. */
    suspend fun captureDate(uri: Uri): OffsetDateTime?

    /** Decode, downscale and re-encode a pick into upload renditions. */
    suspend fun process(uri: Uri): ProcessedImage

    /**
     * Warm the valence fan's eighteen wobbled assets. Process-wide caches, so a
     * second flow pays nothing. Only the record flow draws the fan; the other
     * composers inherit the method and never call it, which is cheaper than a
     * second interface for one method.
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
        override suspend fun captureDate(uri: Uri): OffsetDateTime? = snapProcessor.captureDate(context, uri)

        override suspend fun process(uri: Uri): ProcessedImage = snapProcessor.process(context, uri)

        override suspend fun prewarmValence() = valencePrewarmer.prewarm(context)
    }
