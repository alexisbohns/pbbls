package app.pbbls.android.testing

import android.net.Uri
import app.pbbls.android.features.path.record.RecordFlowMedia
import app.pbbls.android.features.pebblemedia.ProcessedImage
import java.time.OffsetDateTime

/**
 * [RecordFlowMedia] for a JVM test (#849).
 *
 * The two photo methods throw rather than returning a plausible nothing: they
 * take a `Uri`, which cannot be constructed off device, so any test reaching
 * them has wandered into territory only Robolectric can cover (#857). Failing
 * loudly is the point — a fake that quietly returned null here would let such a
 * test pass while asserting nothing.
 */
class FakeRecordFlowMedia : RecordFlowMedia {
    var prewarmCount = 0
        private set

    override suspend fun captureDate(uri: Uri): OffsetDateTime? =
        error("the photo path needs a real Uri — cover it under Robolectric (#857)")

    override suspend fun process(uri: Uri): ProcessedImage = error("the photo path needs a real Uri — cover it under Robolectric (#857)")

    override suspend fun prewarmValence() {
        prewarmCount += 1
    }
}
