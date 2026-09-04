package app.pbbls.android.features.path.valence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The highlight gradient's one load-bearing property: **hue range**.
 *
 * Every sample sits between 3° and 41°, which is what makes a saturated ink
 * affordable — a gradient this narrow reads as ember when you push it, where a
 * full-hue-wheel one reads as a clown's palette at the same saturation and as
 * mud if you darken it instead. Two rounds were spent tuning saturation and
 * lightness before the reference itself changed, so the invariant is asserted
 * rather than left to the eye.
 */
class ValenceMeshTest {
    /** The warm band the design brief fixes, with a degree of slack on each side. */
    private val warmest = 2f
    private val coolest = 42f

    private val palettes =
        mapOf(
            "wash" to ValenceMesh.washHexes,
            "ink" to ValenceMesh.inkHexes,
            "selected" to ValenceMesh.selectedWashHexes,
        )

    @Test
    fun `each palette carries one colour per control point`() {
        assertEquals(16, ValenceMesh.points.size)
        palettes.forEach { (name, hexes) -> assertEquals(name, 16, hexes.size) }
    }

    @Test
    fun `control points stay inside the unit square`() {
        ValenceMesh.points.forEach { (x, y) ->
            assertTrue("($x, $y) escapes the unit square", x in 0f..1f && y in 0f..1f)
        }
    }

    @Test
    fun `every baked pixel stays in the warm band`() {
        palettes.forEach { (name, hexes) ->
            ValenceMesh.bake(hexes, resolution = 16).forEach { pixel ->
                val hue = hueOf(pixel)
                assertTrue("$name baked a pixel at hue $hue, outside the warm band", hue in warmest..coolest)
                assertEquals("$name baked a translucent pixel", 0xFF, (pixel ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun `baking is deterministic`() {
        val first = ValenceMesh.bake(ValenceMesh.washHexes, resolution = 8)
        val second = ValenceMesh.bake(ValenceMesh.washHexes, resolution = 8)
        assertTrue(first.contentEquals(second))
        assertEquals(8 * 8, first.size)
    }

    @Test
    fun `a control point's own colour survives the resample`() {
        // Shepard weighting is exact at the control points, which is what keeps
        // the corners of the gradient the colours they were sampled as. The
        // top-left point sits at (0, 0), so pixel (0, 0) of a fine grid is
        // almost exactly it.
        val pixels = ValenceMesh.bake(ValenceMesh.washHexes, resolution = 64)
        val corner = pixels[0] and 0xFFFFFF
        val expected = 0xE7928B
        assertTrue(
            "top-left corner drifted: ${corner.toString(16)}",
            channelDistance(corner, expected) <= 12,
        )
    }

    private fun channelDistance(
        lhs: Int,
        rhs: Int,
    ): Int =
        maxOf(
            abs(((lhs shr 16) and 0xFF) - ((rhs shr 16) and 0xFF)),
            abs(((lhs shr 8) and 0xFF) - ((rhs shr 8) and 0xFF)),
            abs((lhs and 0xFF) - (rhs and 0xFF)),
        )

    /** Hue in degrees, the one channel this palette is defined by. */
    private fun hueOf(argb: Int): Float {
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val high = max(red, max(green, blue))
        val low = min(red, min(green, blue))
        val delta = high - low
        if (delta == 0f) return 0f
        val hue =
            when (high) {
                red -> 60f * (((green - blue) / delta) % 6f)
                green -> 60f * (((blue - red) / delta) + 2f)
                else -> 60f * (((red - green) / delta) + 4f)
            }
        return if (hue < 0f) hue + 360f else hue
    }
}
