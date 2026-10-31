package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.NextLevel
import app.pbbls.android.core.model.achievements.StackLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The skyline (#1307): stages read off the served levels and next, and the board's g/92 paths. */
class SkylineGeometryTest {
    private fun stack(
        count: Int,
        reached: List<Int>,
        next: NextLevel?,
        ladder: List<Int> = listOf(1, 10, 25, 50, 100, 250, 500, 1000),
    ) = CountingSection(count, reached.map { StackLevel(it, null, null, false) }, next, ladder, thenEvery = null, since = null)

    @Test
    fun `three pebbles - one stage stands, the second rises by the server's progress`() {
        val fill = stack(3, listOf(1), NextLevel(10, 7, 0.222f)).skylineFill()
        assertEquals(SkylineFill(listOf(true) + List(7) { false }, rising = 1, partial = 0.222f), fill)
        assertEquals(8, fill.stages)
        assertEquals(1, fill.full)
    }

    @Test
    fun `each listed level stands on its own - a skipped rung stays empty`() {
        val fill = stack(30, listOf(1, 25), NextLevel(50, 20, 0.2f)).skylineFill()
        assertEquals(listOf(true, false, true, false, false, false, false, false), fill.lit)
        assertEquals(2, fill.full)
        assertEquals(3, fill.rising)
        assertEquals(0.2f, fill.partial)
        assertFalse(fill.done)
    }

    @Test
    fun `a next step the server lists as reached never rises`() {
        val fill = stack(12, listOf(1, 10), NextLevel(10, 0, 0.5f)).skylineFill()
        assertEquals(null, fill.rising)
        assertEquals(0f, fill.partial)
    }

    @Test
    fun `glyphs read five stages, a top-of-ladder stack stands whole`() {
        val sales = stack(60, listOf(1, 5, 10, 25, 50), null, ladder = listOf(1, 5, 10, 25, 50)).skylineFill()
        assertEquals(SkylineFill(List(5) { true }, rising = null, partial = 0f), sales)
        assertTrue(sales.done)
    }

    @Test
    fun `past the ladder the skyline stands whole while the next level is beyond it`() {
        val big = stack(1200, listOf(1, 10, 25, 50, 100, 250, 500, 1000), NextLevel(1500, 300, 0.4f)).skylineFill()
        assertEquals(8, big.full)
        assertEquals(null, big.rising)
        assertEquals(0f, big.partial)
        assertTrue(big.done)
        assertFalse(stack(3, listOf(1), NextLevel(10, 7, 0.222f)).skylineFill().done)
    }

    @Test
    fun `the contour runs from 16 along the stages to the end`() {
        assertEquals(
            "M16 148 L23 148 C23 143.3 29 143.3 29 148 L32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 L126 148",
            SkylineGeometry.contour(stages = 2, end = 126f),
        )
    }

    @Test
    fun `a partial stage is clipped from its foot by its fraction`() {
        val clip = SkylineGeometry.partialClip(stage = 1, fraction = 0.5f)
        assertEquals(30.5f, clip.left, 1e-4f)
        assertEquals(51.5f, clip.right, 1e-4f)
        assertEquals(148f - (148f - 133.2f) * 0.5f, clip.top, 1e-4f)
        assertEquals(149f, clip.bottom, 1e-4f)
    }
}
