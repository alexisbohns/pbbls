package app.pbbls.android.core.data

import android.graphics.Bitmap
import java.time.OffsetDateTime
import java.util.UUID

/**
 * What the crop editor opens on (#1659, iOS `CropSource`, spec "Working image"): the photo decoded
 * upright at [WORKING_MAX_EDGE_PX] on the long edge, and the capture date read from the original bytes
 * before anything drops the metadata (spec D8). The output is capped at 1024 px, so this leaves 2× zoom
 * without loss.
 */
data class CropSource(
    val image: Bitmap,
    val captureDate: OffsetDateTime?,
    val id: String = UUID.randomUUID().toString(),
) {
    companion object {
        const val WORKING_MAX_EDGE_PX = 2048
    }
}
