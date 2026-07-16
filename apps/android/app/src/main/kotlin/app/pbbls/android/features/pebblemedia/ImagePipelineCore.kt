package app.pbbls.android.features.pebblemedia

/**
 * Output of [ImagePipeline.process] — ports iOS `ProcessedImage`. Both blobs
 * are JPEG bytes ready to upload to Supabase Storage; no further processing
 * required. (Byte arrays compare referentially; the coordinator never needs
 * value equality on them.)
 */
data class ProcessedImage(
    /** JPEG, max 1024px on the long edge, target ≤1 MB. */
    val original: ByteArray,
    /** JPEG, max 420px on the long edge, target ≤300 KB. */
    val thumb: ByteArray,
)

/** Resize + quality ladder could not fit the byte budget — ports `tooLargeAfterResize`. */
class ImageTooLargeException : Exception("image exceeds its byte budget after resize + quality ladder")

/**
 * The iOS `ImagePipeline` constants, verbatim (design D2). Quality is Android's
 * 0–100 integer scale (iOS uses 0–1 floats: 0.85 → 85).
 */
object SnapBudgets {
    const val ORIGINAL_MAX_EDGE_PX = 1024
    const val THUMB_MAX_EDGE_PX = 420
    const val ORIGINAL_MAX_BYTES = 1_048_576
    const val THUMB_MAX_BYTES = 307_200
    const val ORIGINAL_START_QUALITY = 85
    const val THUMB_START_QUALITY = 75
    const val QUALITY_STEP = 10
    const val QUALITY_STEPS = 3
    const val MIN_QUALITY = 10
}

/**
 * The quality ladder — encode at [startQuality], and while the result busts
 * [byteCap], step the quality down by [SnapBudgets.QUALITY_STEP] up to
 * [SnapBudgets.QUALITY_STEPS] times (never below [SnapBudgets.MIN_QUALITY]).
 * Pure over a pluggable [encode] so the ladder is JVM-testable without
 * `android.graphics` (design D2); [ImagePipeline] passes `Bitmap.compress`.
 *
 * Mirrors iOS `renderJPEG`'s loop exactly: up to `steps + 1` encode attempts,
 * then [ImageTooLargeException].
 */
fun encodeWithinBudget(
    startQuality: Int,
    byteCap: Int,
    encode: (quality: Int) -> ByteArray,
): ByteArray {
    var quality = startQuality
    var attempts = 0
    while (attempts <= SnapBudgets.QUALITY_STEPS) {
        val bytes = encode(quality)
        if (bytes.size <= byteCap) return bytes
        attempts += 1
        quality -= SnapBudgets.QUALITY_STEP
        if (quality <= SnapBudgets.MIN_QUALITY) break
    }
    throw ImageTooLargeException()
}
