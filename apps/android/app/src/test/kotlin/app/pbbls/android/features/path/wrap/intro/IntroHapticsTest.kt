package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroHapticsTests` (#1241 D5, D15). */
class IntroHapticsTest {
    @Test
    fun `each ticking beat plays at its time, one closer than 25 ms to the last kept is dropped`() {
        val beats =
            listOf(
                IntroBeat(IntroKey.Row(1), 0.05, 0.28, IntroTick.facet),
                IntroBeat(IntroKey.Header, 0.0, 0.28, IntroTick.light),
                IntroBeat(IntroKey.Lead, 0.01, 0.28),
                IntroBeat(IntroKey.Row(0), 0.02, 0.28, IntroTick.light),
            )
        assertEquals(
            listOf(IntroHapticEvent(0.0, IntroTick.light), IntroHapticEvent(0.05, IntroTick.facet)),
            IntroHaptics.events(beats),
        )
    }

    @Test
    fun `a swelling beat plays as a swell over its duration, never as a tick, and falls silent a floor early`() {
        val swell = IntroSwell(0.15f, 0.75f, 0.3f)
        val beats =
            listOf(
                IntroBeat(IntroKey.Row(0), 0.32, 0.28, IntroTick.light),
                IntroBeat(IntroKey.Gauge(0), 0.41, 0.7, swell = swell),
                IntroBeat(IntroKey.Full(0), 1.11, 0.0, IntroTick.light),
            )
        val swells = IntroHaptics.swells(beats)
        assertEquals(listOf(IntroHapticSwell(0.41, 0.7, swell)), swells)
        assertEquals(IntroHaptics.FLOOR, swells[0].duration - swells[0].sounding, 1e-12)
        assertEquals(listOf(0.32, 1.11), IntroHaptics.events(beats).map { it.at })
        // A beat with nothing to swell over plays no swell.
        assertTrue(IntroHaptics.swells(listOf(IntroBeat(IntroKey.Gauge(0), 0.0, 0.0, swell = swell))).isEmpty())
    }

    @Test
    fun `a 31-day garland keeps its name, every day and its leaf`() {
        val beats = IntroScore.cover(emptyList(), 31, emptyList(), 0, emptyList())
        assertEquals(33, IntroHaptics.events(beats).size)
    }

    @Test
    fun `a 31-day garland with two pebbles every day still ticks every day, its name and its leaf`() {
        val pebble = { i: Int -> CoverPebble("m", null, i, ValencePolarity.HIGHLIGHT) }
        val days = (0 until 31).map { CoverDay(it, listOf(pebble(1), pebble(3))) }
        val beats = IntroScore.cover(days, 31, emptyList(), 0, emptyList())
        val ticked = IntroHaptics.events(beats).map { it.at }.toSet()
        ((0 until 31).map { IntroKey.Slot(it) } + listOf(IntroKey.MonthName, IntroKey.Leaf)).forEach { key ->
            assertTrue("$key lost its tick", beats.first { it.key == key }.at in ticked)
        }
    }

    @Test
    fun `the view tier fires nothing before the first tick's time`() {
        val events = listOf(IntroHapticEvent(0.05, IntroTick.light), IntroHapticEvent(0.1, IntroTick.firm))
        assertEquals(null to 0, IntroHaptics.crossed(events, next = 0, elapsed = -0.05))
        assertEquals(null to 0, IntroHaptics.crossed(events, next = 0, elapsed = 0.04))
    }

    @Test
    fun `a frame fires only the last tick it crossed, and moves past every one`() {
        val events =
            listOf(
                IntroHapticEvent(0.0, IntroTick.faint),
                IntroHapticEvent(0.05, IntroTick.light),
                IntroHapticEvent(0.1, IntroTick.firm),
                IntroHapticEvent(0.2, IntroTick.knot),
            )
        assertEquals(events[2] to 3, IntroHaptics.crossed(events, next = 0, elapsed = 0.12))
        // A tick at the frame's time is crossed.
        assertEquals(events[0] to 1, IntroHaptics.crossed(events, next = 0, elapsed = 0.0))
    }

    @Test
    fun `a frame crossing nothing new fires nothing, and the last tick fires once`() {
        val events = listOf(IntroHapticEvent(0.0, IntroTick.light), IntroHapticEvent(0.2, IntroTick.firm))
        assertEquals(null to 1, IntroHaptics.crossed(events, next = 1, elapsed = 0.15))
        assertEquals(events[1] to 2, IntroHaptics.crossed(events, next = 1, elapsed = 0.2))
        assertEquals(null to 2, IntroHaptics.crossed(events, next = 2, elapsed = 5.0))
    }
}
