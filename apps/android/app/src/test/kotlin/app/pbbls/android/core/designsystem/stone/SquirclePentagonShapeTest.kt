package app.pbbls.android.core.designsystem.stone

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile's squircle pentagon (spec D5), the mock's `pentagon()` from
 * `type/five/c3.html` (2026-10-31 Type Bench): five rounded corners inside the
 * 128 × 122 box, symmetric about its vertical axis.
 */
class SquirclePentagonShapeTest {
    private val size = Size(128f, 122f)
    private val outline = SquirclePentagon.points(size)

    @Test
    fun `the outline sits inside the box`() {
        val overshoot = 2.5f
        assertTrue(outline.minOf { it.x } >= -overshoot)
        assertTrue(outline.maxOf { it.x } <= size.width + overshoot)
        assertTrue(outline.minOf { it.y } >= -overshoot)
        assertTrue(outline.maxOf { it.y } <= size.height + overshoot)
        // and fills most of it
        assertTrue(outline.maxOf { it.x } - outline.minOf { it.x } > 110f)
        assertTrue(outline.maxOf { it.y } - outline.minOf { it.y } > 105f)
    }

    @Test
    fun `the outline is mirrored about the vertical axis`() {
        for (point in outline) {
            val mirrored = Offset(size.width - point.x, point.y)
            val nearest = outline.minOf { (it - mirrored).getDistance() }
            assertEquals("mirror of $point", 0f, nearest, 0.1f)
        }
    }

    @Test
    fun `the outline has five corners`() {
        val centre = Offset(64f, 66f)
        val distances = outline.map { (it - centre).getDistance() }
        val n = distances.size
        val maxima = distances.indices.count { distances[it] > distances[(it - 1 + n) % n] && distances[it] >= distances[(it + 1) % n] }
        val minima = distances.indices.count { distances[it] < distances[(it - 1 + n) % n] && distances[it] <= distances[(it + 1) % n] }
        assertEquals(5, maxima)
        assertEquals(5, minima)
    }

    @Test
    fun `the outline scales with its size`() {
        val half = SquirclePentagon.points(Size(64f, 61f))
        half.zip(outline).forEach { (small, full) ->
            assertEquals(full.x / 2, small.x, 1e-3f)
            assertEquals(full.y / 2, small.y, 1e-3f)
        }
    }
}
