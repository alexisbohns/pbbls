package app.pbbls.android.core.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import app.pbbls.android.core.model.crop.CropGeometry
import app.pbbls.android.core.model.crop.CropPoint
import app.pbbls.android.core.model.crop.CropRatio
import app.pbbls.android.core.model.crop.CropSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Ported from iOS `PhotoCropRendererTests` (#1659): quadrant images land where the window says. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoCropRendererTest {
    private enum class Quadrant { RED, GREEN, BLUE, YELLOW }

    /** Upright quadrants: red top-left, green top-right, blue bottom-right, yellow bottom-left. */
    private fun quadrants(size: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val h = size / 2f
        val paint = Paint()

        fun fill(
            color: Int,
            left: Float,
            top: Float,
        ) {
            paint.color = color
            canvas.drawRect(left, top, left + h, top + h, paint)
        }
        fill(Color.RED, 0f, 0f)
        fill(Color.GREEN, h, 0f)
        fill(Color.BLUE, h, h)
        fill(Color.YELLOW, 0f, h)
        return bitmap
    }

    private fun colour(
        image: Bitmap,
        x: Int,
        y: Int,
    ): Quadrant? {
        val p = image.getPixel(x, y)
        val r = Color.red(p) > 200
        val g = Color.green(p) > 200
        val b = Color.blue(p) > 200
        return when {
            r && !g && !b -> Quadrant.RED
            !r && g && !b -> Quadrant.GREEN
            !r && !g && b -> Quadrant.BLUE
            r && g && !b -> Quadrant.YELLOW
            else -> null
        }
    }

    private fun corners(image: Bitmap): List<Quadrant?> {
        val w = image.width
        val h = image.height
        val m = 4
        return listOf(colour(image, m, m), colour(image, w - m, m), colour(image, w - m, h - m), colour(image, m, h - m))
    }

    @Test
    fun `an untouched square crop keeps the picture upright`() {
        val out = PhotoCropRenderer.render(quadrants(), CropGeometry.initial(CropSize(400.0, 400.0)))
        assertEquals(400, out.width)
        assertEquals(400, out.height)
        assertEquals(listOf(Quadrant.RED, Quadrant.GREEN, Quadrant.BLUE, Quadrant.YELLOW), corners(out))
    }

    @Test
    fun `a clockwise quarter turn moves top-left to top-right`() {
        val g = CropGeometry.initial(CropSize(400.0, 400.0)).turnedQuarter()
        val out = PhotoCropRenderer.render(quadrants(), g)
        assertEquals(listOf(Quadrant.YELLOW, Quadrant.RED, Quadrant.GREEN, Quadrant.BLUE), corners(out))
    }

    @Test
    fun `a 4-5 crop panned left shows only the left half's colours`() {
        val start = CropGeometry.initial(CropSize(400.0, 400.0)).withRatio(CropRatio.FOUR_FIVE)
        val g = start.copy(scale = start.minScale * 2.5, focus = CropPoint(-100.0, 0.0)).normalized()
        val out = PhotoCropRenderer.render(quadrants(), g)
        assertEquals(0.8, out.width.toDouble() / out.height, 0.01)
        assertEquals(listOf(Quadrant.RED, Quadrant.RED, Quadrant.YELLOW, Quadrant.YELLOW), corners(out))
    }

    @Test
    fun `the output long edge never exceeds 1024 nor the source pixels it covers`() {
        val g = CropGeometry.initial(CropSize(2048.0, 1536.0))
        assertEquals(1024 to 768, PhotoCropRenderer.outputSize(g))
        // The window covers 256×192 source pixels.
        assertEquals(256 to 192, PhotoCropRenderer.outputSize(g.copy(scale = g.minScale * 8)))
    }

    @Test
    fun `every border pixel of a minimum-scale crop is the photo, never the empty bitmap`() {
        val solid = Color.rgb(77, 153, 51)
        for (angle in listOf(0.0, 7.0, 30.0, 45.0, -45.0)) {
            for ((width, height) in listOf(997 to 1013, 1536 to 2048, 811 to 641)) {
                val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(solid) }
                for (ratio in listOf(CropRatio.FOUR_FIVE, CropRatio.SQUARE, CropRatio.FOUR_THREE)) {
                    val tilted = CropGeometry.initial(CropSize(width.toDouble(), height.toDouble())).withRatio(ratio).withAngle(angle)
                    val g = tilted.copy(scale = tilted.minScale)
                    val out = PhotoCropRenderer.render(source, g)
                    var darkest = 255
                    for (y in 0 until out.height) {
                        for (x in 0 until out.width) {
                            if (x != 0 && y != 0 && x != out.width - 1 && y != out.height - 1) continue
                            val p = out.getPixel(x, y)
                            darkest = minOf(darkest, Color.red(p), Color.green(p), Color.blue(p))
                        }
                    }
                    // The solid colour's darkest channel is 51.
                    assertTrue("${width}x$height $ratio $angle: border dipped to $darkest in ${out.width}x${out.height}", darkest >= 49)
                }
            }
        }
    }

    @Test
    fun `a source that does not match the geometry's image size is refused`() {
        val g = CropGeometry.initial(CropSize(300.0, 400.0))
        assertThrows(IllegalArgumentException::class.java) { PhotoCropRenderer.render(quadrants(), g) }
    }

    @Test
    fun `a tiny window still gives at least one pixel`() {
        val g = CropGeometry.initial(CropSize(2048.0, 1536.0)).copy(scale = 100.0)
        val (w, h) = PhotoCropRenderer.outputSize(g)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test
    fun `the output is opaque and keeps the window's ratio after rounding`() {
        val g = CropGeometry.initial(CropSize(997.0, 1013.0)).withRatio(CropRatio.FOUR_FIVE)
        val out = PhotoCropRenderer.render(Bitmap.createBitmap(997, 1013, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }, g)
        assertTrue(!out.hasAlpha())
        assertTrue(abs(out.width.toDouble() / out.height - 0.8) < 0.01)
    }
}
