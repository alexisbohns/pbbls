package app.pbbls.android.features.path.wrap.intro

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
}
