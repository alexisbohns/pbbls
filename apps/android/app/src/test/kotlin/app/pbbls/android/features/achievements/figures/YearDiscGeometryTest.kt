package app.pbbls.android.features.achievements.figures

import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.MonthDone
import app.pbbls.android.core.model.achievements.MonthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The docked year disc (#1307): the board's g/112 base, and each month placed by the server's level. */
class YearDiscGeometryTest {
    private val g = YearDiscGeometry

    private fun month(
        level: Int,
        progress: Float,
        state: MonthState? = MonthState.KEPT,
        family: String? = "joy",
        m: Int = 1,
    ) = AchievementMonth(m, state, family, 0, 0, 0, 0, level, progress, MonthDone(false, false, false))

    @Test
    fun `the base sits where the board's g 112 svg draws it`() {
        // Numeral II at (168.0, 39.4) is r 96 at 30°, baseline 2.5 under the centre.
        val ii = g.point(g.NUMERAL_R, g.angle(2))
        assertEquals(168f, ii.x, 0.05f)
        assertEquals(36.9f, ii.y, 0.05f)
        // Month I's inner notch is the segment (117, 70)–(123, 70).
        val (a, b) = g.notch(1, g.RADII[1])
        assertEquals(117f, a.x, 1e-3f)
        assertEquals(70f, a.y, 1e-3f)
        assertEquals(123f, b.x, 1e-3f)
        assertEquals(70f, b.y, 1e-3f)
    }

    @Test
    fun `a rim month docks on the rim with its whole climb in its hue`() {
        val mark = g.mark(month(2, 0f))
        assertEquals(80f, mark.counterR)
        assertTrue(mark.rim)
        assertEquals(56f..73f, mark.climb)
        assertTrue(mark.climbInHue)
        assertFalse(mark.goalNotch)
    }

    @Test
    fun `an inner-disc month climbs toward the rim in its hue by its progress`() {
        val mark = g.mark(month(1, 0.5f))
        assertEquals(50f, mark.counterR)
        assertEquals(56f..(56f + 0.5f * 18f), mark.climb)
        assertTrue(mark.climbInHue)
    }

    @Test
    fun `a month below the inner disc climbs in ink and shows its goal, unless muted`() {
        val begun = g.mark(month(0, 0.25f))
        assertEquals(30f, begun.counterR)
        assertEquals(36f..(36f + 0.25f * 8f), begun.climb)
        assertFalse(begun.climbInHue)
        assertTrue(begun.goalNotch)
        val quiet = g.mark(month(0, 0.25f, MonthState.QUIET, family = null))
        assertTrue(quiet.muted)
        assertFalse(quiet.goalNotch)
        val untouched = g.mark(month(0, 0f))
        assertNull(untouched.climb)
        assertTrue(g.mark(month(0, 0f, MonthState.UPCOMING, family = null)).upcoming)
    }

    @Test
    fun `a quiet month on the inner circle climbs in ink, and an unknown state draws as quiet`() {
        val momentsOnly = g.mark(month(1, 0.5f, MonthState.QUIET, family = null))
        assertTrue(momentsOnly.muted)
        assertFalse(momentsOnly.climbInHue)
        val unknown = g.mark(month(0, 0f, state = null, family = null))
        assertTrue(unknown.muted)
        assertFalse(unknown.upcoming)
    }

    @Test
    fun `the disc reads as the server's lists`() {
        val months =
            listOf(month(2, 0f, m = 8), month(1, 0f, m = 3), month(0, 0.3f, m = 11), month(0, 0f, MonthState.UPCOMING, null, m = 12))
        assertEquals(listOf(8), g.onRim(months))
        assertEquals(listOf(3), g.onInner(months))
        assertEquals(listOf(11), g.onTheirWay(months))
    }
}
