package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.ToneDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreInsightsTests`: the tone's sweep and the hours' stones (#1241 §Choreography). */
class IntroScoreInsightsTest {
    @Test
    fun `the tone sweeps once, ticking only on days with pebbles, as the sweep reaches them`() {
        val days = listOf(ToneDay(1, 0, 0), ToneDay(0, 0, 0), ToneDay(0, 2, 0))
        val beats = IntroScore.tone(days, insights = false)
        val sweep = beats.first { it.key == IntroKey.Sweep }
        assertEquals(IntroScore.SWEEP_AT, sweep.at, 0.0)
        assertEquals(IntroScore.SWEEP_SPAN, sweep.duration, 0.0)
        assertEquals(sweep.at, beats.first { it.key == IntroKey.Day(0) }.at, 0.0)
        assertFalse(beats.any { it.key == IntroKey.Day(1) })
        val last = beats.first { it.key == IntroKey.Day(2) }
        assertEquals(sweep.at + sweep.duration, last.at, 1e-12)
        assertEquals(IntroTick.light, last.tick)
    }

    @Test
    fun `leading the insights, the head comes first and ticks the arrival, and the rest shifts`() {
        val beats = IntroScore.tone(listOf(ToneDay(1, 0, 0)), insights = true)
        assertEquals(IntroKey.Insights, beats.first().key)
        assertEquals(IntroTick.arrive, beats.first().tick)
        val header = beats.first { it.key == IntroKey.Header }
        assertEquals(IntroScore.INSIGHTS_SHIFT, header.at, 0.0)
        // One arrival per chapter: the section's own head stays silent under the insights head.
        assertNull(header.tick)
        val sweep = beats.first { it.key == IntroKey.Sweep }
        assertEquals(IntroScore.INSIGHTS_SHIFT + IntroScore.SWEEP_AT, sweep.at, 1e-12)
    }

    @Test
    fun `the insights head alone is the arrival's tick, drawn from the start`() {
        val beats = IntroScore.insightsHead()
        assertEquals(1, beats.size)
        val head = beats.single()
        assertEquals(IntroKey.Insights, head.key)
        assertEquals(IntroTick.arrive, head.tick)
        assertEquals(0.0, head.duration, 0.0)
    }

    @Test
    fun `the hours - the dial, then one stone per mark, slow-fast-slow`() {
        val beats = IntroScore.hours(stones = 4, insights = false)
        val stones = (0 until 4).mapNotNull { rank -> beats.firstOrNull { it.key == IntroKey.Stone(rank) } }
        assertEquals(4, stones.size)
        assertEquals(IntroScore.STONES_AT, stones.first().at, 0.0)
        assertTrue(stones.zipWithNext().all { (a, b) -> b.at > a.at })
        assertTrue(beats.any { it.key == IntroKey.Dial })
    }

    @Test
    fun `a stone's rank is its time of day from midnight, ties keeping their order`() {
        assertEquals(listOf(3, 0, 2, 1), IntroScore.ranks(listOf(23.5, 0.25, 12.0, 0.25)))
    }

    @Test
    fun `the tone's middle keeps every day's tick - 31 days and the head make 32 haptic events`() {
        val days = List(31) { ToneDay(1, 0, 0) }
        val beats = IntroScore.tone(days, insights = false)
        assertEquals(32, IntroHaptics.events(beats).size)
    }
}
