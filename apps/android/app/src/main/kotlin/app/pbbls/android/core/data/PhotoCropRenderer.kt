package app.pbbls.android.core.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.core.model.crop.CropSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the crop window's content from the working image (#1659, iOS `PhotoCropRenderer`, spec
 * "Rendering on Done"). Pure: no logging, no Context. The output is at most [MAX_OUTPUT_EDGE] px on
 * the long edge, never more pixels than the window covers in the source, and in the source's own
 * colour space where a bitmap can carry it.
 */
object PhotoCropRenderer {
    const val MAX_OUTPUT_EDGE = 1024.0

    /**
     * The photo is drawn this much larger than the geometry says, about the window centre: it
     * overfills the window by a hair so antialiasing never blends an edge pixel with the empty
     * bitmap behind it.
     */
    const val OVERFILL = 1.002

    /** Output width and height in pixels. */
    fun outputSize(g: CropGeometry): Pair<Int, Int> {
        val window = g.windowSize
        val longWindow = max(window.width, window.height)
        val sourcePixels = longWindow / g.scale
        val longEdge = max(1.0, min(MAX_OUTPUT_EDGE, sourcePixels.roundToDouble()))
        val perUnit = longEdge / longWindow
        return max(1, (window.width * perUnit).roundToInt()) to max(1, (window.height * perUnit).roundToInt())
    }

    /** Throws [IllegalArgumentException] when [image] is not the size [g] was built for. */
    fun render(
        image: Bitmap,
        g: CropGeometry,
    ): Bitmap {
        require(g.imageSize == CropSize(image.width.toDouble(), image.height.toDouble())) {
            "the geometry is for ${g.imageSize}, the image is ${image.width}x${image.height}"
        }
        val (width, height) = outputSize(g)
        val output = createOutput(width, height, image.colorSpace)
        val canvas = Canvas(output)
        // y-down already: the window centre at the origin, in window units.
        canvas.translate(width / 2f, height / 2f)
        // The rounded output is not exactly the window's shape, so x and y scale on their own and the
        // window still fills it edge to edge.
        canvas.scale((width / g.windowSize.width).toFloat(), (height / g.windowSize.height).toFloat())
        // Same chain as the canvas: offset, turn, scale.
        canvas.translate(g.windowOffset.x.toFloat(), g.windowOffset.y.toFloat())
        canvas.rotate(Math.toDegrees(g.theta).toFloat())
        canvas.scale(g.scale.toFloat(), g.scale.toFloat())
        canvas.translate(g.focus.x.toFloat(), g.focus.y.toFloat())
        canvas.scale(OVERFILL.toFloat(), OVERFILL.toFloat())
        canvas.translate(-g.focus.x.toFloat(), -g.focus.y.toFloat())
        // Image pixels from the image centre.
        canvas.translate(-image.width / 2f, -image.height / 2f)
        canvas.drawBitmap(image, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return output
    }

    /** An opaque bitmap in [colorSpace] when an 8-bit bitmap can carry it (not HDR curves), else sRGB. */
    private fun createOutput(
        width: Int,
        height: Int,
        colorSpace: ColorSpace?,
    ): Bitmap {
        val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
        val space = (colorSpace as? ColorSpace.Rgb)?.takeIf { it.transferParameters != null } ?: srgb
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false, space)
    }

    private fun Double.roundToDouble(): Double = Math.round(this).toDouble()
}
