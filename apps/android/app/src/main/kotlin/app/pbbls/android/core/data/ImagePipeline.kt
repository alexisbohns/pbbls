package app.pbbls.android.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
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
    /**
     * The photo decoded upright (EXIF orientation baked), at most [maxEdgePx] on the long edge, in a
     * software bitmap: the crop editor's working image (#1659, iOS `decodeOriented`).
     */
    fun decodeUpright(
        context: Context,
        uri: Uri,
        maxEdgePx: Int,
    ): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software allocation: Bitmap.compress and a software Canvas can't read HARDWARE bitmaps.
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
    }

    /**
     * Both renditions of an image already in hand: the crop editor's output (#1659, iOS
     * `process(_ image: CGImage)`). The same caps, quality ladder and metadata-free encode as
     * [process] from a `Uri`; the thumb is downscaled from [image]. [image] is not recycled.
     */
    fun process(image: Bitmap): ProcessedImage =
        ProcessedImage(
            original =
                encodeScaled(
                    image = image,
                    maxEdgePx = SnapBudgets.ORIGINAL_MAX_EDGE_PX,
                    startQuality = SnapBudgets.ORIGINAL_START_QUALITY,
                    byteCap = SnapBudgets.ORIGINAL_MAX_BYTES,
                ),
            thumb =
                encodeScaled(
                    image = image,
                    maxEdgePx = SnapBudgets.THUMB_MAX_EDGE_PX,
                    startQuality = SnapBudgets.THUMB_START_QUALITY,
                    byteCap = SnapBudgets.THUMB_MAX_BYTES,
                ),
        )

    private fun encodeScaled(
        image: Bitmap,
        maxEdgePx: Int,
        startQuality: Int,
        byteCap: Int,
    ): ByteArray {
        val scaled = downscaled(image, maxEdgePx)
        try {
            return encodeJpeg(scaled, startQuality, byteCap)
        } finally {
            if (scaled !== image) scaled.recycle()
        }
    }

    /**
     * [image] at most [maxEdgePx] on the long edge. Halves while more than twice too big, then one
     * last bilinear step: a single bilinear pass from far above skips pixels and shimmers.
     */
    private fun downscaled(
        image: Bitmap,
        maxEdgePx: Int,
    ): Bitmap {
        var current = image
        while (maxOf(current.width, current.height) > maxEdgePx * 2) {
            val next = current.scale((current.width / 2).coerceAtLeast(1), (current.height / 2).coerceAtLeast(1))
            if (current !== image) current.recycle()
            current = next
        }
        val longEdge = maxOf(current.width, current.height)
        if (longEdge <= maxEdgePx) return current
        val scale = maxEdgePx.toDouble() / longEdge
        val last =
            current.scale(
                Math.round(current.width * scale).toInt().coerceIn(1, maxEdgePx),
                Math.round(current.height * scale).toInt().coerceIn(1, maxEdgePx),
            )
        if (current !== image) current.recycle()
        return last
    }

    private fun encodeJpeg(
        bitmap: Bitmap,
        startQuality: Int,
        byteCap: Int,
    ): ByteArray =
        encodeWithinBudget(startQuality = startQuality, byteCap = byteCap) { quality ->
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
        }

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
        val bitmap = decodeUpright(context, uri, maxEdgePx)
        try {
            return encodeJpeg(bitmap, startQuality, byteCap)
        } finally {
            bitmap.recycle()
        }
    }
}
