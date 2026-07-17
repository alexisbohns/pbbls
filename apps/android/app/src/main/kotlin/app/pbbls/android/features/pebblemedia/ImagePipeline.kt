package app.pbbls.android.features.pebblemedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Decode, resize, and re-encode a picked image as the two upload renditions —
 * the Android half of iOS `ImagePipeline.swift` (the pure ladder lives in
 * `ImagePipelineCore.kt`). `ImageDecoder` bakes EXIF orientation into the
 * pixels and downsamples at decode (never full-res in memory — design risk 1);
 * `Bitmap.compress` writes no metadata, so EXIF/GPS are stripped inherently
 * (design D2). Throws on undecodable input or busted budgets; the caller logs
 * and drops the pick (iOS parity).
 */
object ImagePipeline {
    fun process(
        context: Context,
        uri: Uri,
    ): ProcessedImage =
        ProcessedImage(
            original =
                renderJpeg(
                    context = context,
                    uri = uri,
                    maxEdgePx = SnapBudgets.ORIGINAL_MAX_EDGE_PX,
                    startQuality = SnapBudgets.ORIGINAL_START_QUALITY,
                    byteCap = SnapBudgets.ORIGINAL_MAX_BYTES,
                ),
            thumb =
                renderJpeg(
                    context = context,
                    uri = uri,
                    maxEdgePx = SnapBudgets.THUMB_MAX_EDGE_PX,
                    startQuality = SnapBudgets.THUMB_START_QUALITY,
                    byteCap = SnapBudgets.THUMB_MAX_BYTES,
                ),
        )

    private fun renderJpeg(
        context: Context,
        uri: Uri,
        maxEdgePx: Int,
        startQuality: Int,
        byteCap: Int,
    ): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap =
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Software allocation: Bitmap.compress can't read HARDWARE bitmaps.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > maxEdgePx) {
                    val scale = maxEdgePx.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        try {
            return encodeWithinBudget(startQuality = startQuality, byteCap = byteCap) { quality ->
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
