package app.pbbls.android.testing

import android.net.Uri
import app.pbbls.android.core.data.ProcessedImage
import app.pbbls.android.features.path.ComposerMedia
import java.time.OffsetDateTime

/**
 * [ComposerMedia] for a JVM test (#849).
 *
 * The two photo methods throw rather than returning a plausible nothing: they
 * take a `Uri`, which cannot be constructed off device, so any test reaching
 * them has wandered into territory only Robolectric can cover (#857). Failing
 * loudly is the point — a fake that quietly returned null here would let such a
 * test pass while asserting nothing. A Robolectric test that does build a `Uri`
 * arms [processed] to get a pick through [process].
 */
class FakeComposerMedia : ComposerMedia {
    var prewarmCount = 0
        private set

    /** What [process] returns once armed; unarmed, it throws as above. */
    var processed: ProcessedImage? = null

    /** What [captureDate] returns once [processed] is armed: the pick's EXIF date, or none. */
    var captureDateResult: OffsetDateTime? = null

    override suspend fun captureDate(uri: Uri): OffsetDateTime? =
        if (processed != null) captureDateResult else error("the photo path needs a real Uri — cover it under Robolectric (#857)")

    override suspend fun process(uri: Uri): ProcessedImage =
        processed ?: error("the photo path needs a real Uri — cover it under Robolectric (#857)")

    override suspend fun prewarmValence() {
        prewarmCount += 1
    }
}
