package app.pbbls.android.features.achievements.figures

import org.junit.Assert.assertEquals
import org.junit.Test

/** The wheel's geometry (#1307): the board's `polar`, radii and spacing, in a 300 box. */
class ExplorerWheelGeometryTest {
    private val g = ExplorerWheelGeometry

    @Test
    fun `polar runs clockwise from twelve o'clock, y down`() {
        val top = g.polar(10f, 0f)
        assertEquals(0f, top.x, 1e-4f)
        assertEquals(-10f, top.y, 1e-4f)
        val right = g.polar(10f, 90f)
        assertEquals(10f, right.x, 1e-4f)
        assertEquals(0f, right.y, 1e-4f)
    }

    @Test
    fun `the rings sit at the board's radii and spacing`() {
        assertEquals(listOf(44f, 68f, 90f, 112f, 134f), listOf(g.FIRST_FIVE_R, g.SIX_R, g.TWENTY_FIVE_R, g.DOMAINS_R, g.EMOTIONS_R))
        assertEquals(144f, g.firstFiveAngle(2), 1e-4f)
        assertEquals(listOf(30f, 90f, 150f, 210f, 270f, 330f), (0 until 6).map { g.sixAngle(it) })
        assertEquals(7.2f, g.twentyFiveAngle(0), 1e-4f)
        assertEquals(10f, g.domainAngle(0, 18), 1e-4f)
        assertEquals(-2.5f * 360f / 38, g.seatAngle(0, 38), 1e-4f)
    }

    @Test
    fun `a family's arc spans its seats less 1 2 degrees at each end, its tick at its start`() {
        val step = 360f / 38
        val arc = g.familyArcs(listOf(FamilyRun("joy", 0, 9, 3)), seats = 38).single()
        val a0 = g.seatAngle(0, 38) - step / 2
        val a1 = g.seatAngle(8, 38) + step / 2
        assertEquals(a0 + 1.2f, arc.startDeg, 1e-3f)
        assertEquals(a1 - a0 - 2.4f, arc.sweepDeg, 1e-3f)
        assertEquals(a0, arc.tickDeg, 1e-3f)
    }
}
