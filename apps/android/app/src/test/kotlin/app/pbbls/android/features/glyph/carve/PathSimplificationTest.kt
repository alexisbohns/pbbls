package app.pbbls.android.features.glyph.carve

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PathSimplification] — the iOS RDP port (M43 D1): clamped-segment distance,
 * strict epsilon split, max point in both halves, `<= 2` passthrough.
 */
class PathSimplificationTest {
    private fun p(
        x: Double,
        y: Double,
    ) = CarvePoint(x, y)

    @Test
    fun `two or fewer points pass through unchanged`() {
        assertEquals(emptyList<CarvePoint>(), PathSimplification.simplify(emptyList(), 1.5))
        val one = listOf(p(1.0, 1.0))
        assertEquals(one, PathSimplification.simplify(one, 1.5))
        val two = listOf(p(0.0, 0.0), p(5.0, 5.0))
        assertEquals(two, PathSimplification.simplify(two, 1.5))
    }

    @Test
    fun `collinear interior points collapse to the endpoints`() {
        val line = listOf(p(0.0, 0.0), p(1.0, 1.0), p(2.0, 2.0), p(3.0, 3.0))
        assertEquals(listOf(p(0.0, 0.0), p(3.0, 3.0)), PathSimplification.simplify(line, 1.5))
    }

    @Test
    fun `a point beyond epsilon survives`() {
        val bent = listOf(p(0.0, 0.0), p(5.0, 4.0), p(10.0, 0.0))
        assertEquals(bent, PathSimplification.simplify(bent, 1.5))
    }

    @Test
    fun `a point within epsilon is dropped`() {
        val nearlyStraight = listOf(p(0.0, 0.0), p(5.0, 1.0), p(10.0, 0.0))
        assertEquals(listOf(p(0.0, 0.0), p(10.0, 0.0)), PathSimplification.simplify(nearlyStraight, 1.5))
    }

    @Test
    fun `the split is strict — exactly epsilon collapses`() {
        val atEpsilon = listOf(p(0.0, 0.0), p(5.0, 1.5), p(10.0, 0.0))
        assertEquals(listOf(p(0.0, 0.0), p(10.0, 0.0)), PathSimplification.simplify(atEpsilon, 1.5))
    }

    @Test
    fun `distance clamps to the segment, not the infinite line`() {
        // The interior point projects BEYOND the segment end; infinite-line RDP
        // would measure ~0 and drop it, clamped-segment RDP keeps it.
        val hook = listOf(p(0.0, 0.0), p(14.0, 0.1), p(10.0, 0.0))
        assertEquals(hook, PathSimplification.simplify(hook, 1.5))
    }

    @Test
    fun `recursive split keeps the max point exactly once`() {
        val zigzag = listOf(p(0.0, 0.0), p(2.0, 8.0), p(4.0, 0.0), p(6.0, 8.0), p(8.0, 0.0))
        assertEquals(zigzag, PathSimplification.simplify(zigzag, 1.5))
    }
}
