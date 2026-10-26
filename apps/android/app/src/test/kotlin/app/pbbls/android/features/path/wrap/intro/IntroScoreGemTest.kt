package app.pbbls.android.features.path.wrap.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreGemTests`: ta-ta-ta-ta-ta · ta-ta-ta-ta · ta-ta-ta. */
class IntroScoreGemTest {
    @Test
    fun `a run that crosses the top sweeps clockwise through it, from after its widest gap`() {
        val facets = listOf("kite_0" to 0.0, "girdle_1" to 0.25, "star_6" to 6.5, "girdle_0" to -0.25)
        assertEquals(listOf("star_6", "girdle_0", "kite_0", "girdle_1"), IntroScore.sweep(facets))
        assertEquals(listOf("kite_3"), IntroScore.sweep(listOf("kite_3" to 3.0)))
    }

    @Test
    fun `of two equal widest gaps the sweep starts after the first, as Swift's max(by) does`() {
        // Two facets half a turn apart: both gaps are 3.5 sevenths, exactly, so the first (before the lowest) wins.
        assertEquals(listOf("a", "b"), IntroScore.sweep(listOf("b" to 3.5, "a" to 0.0)))
    }

    @Test
    fun `the families that own facets, in the lead order, each with exactly its facets`() {
        val facets = mapOf("kite_0" to "joy", "star_0" to "joy", "kite_3" to "calm", "star_3" to null)
        val runs = IntroScore.gemRuns(listOf("calm", "anger", "joy"), facets)
        assertEquals(listOf("calm", "joy"), runs.map { it.slug })
        assertEquals(listOf("kite_3"), runs[0].facets)
        // Swept clockwise: kite_0 sits at theta 0, star_0 at 0.5.
        assertEquals(listOf("kite_0", "star_0"), runs[1].facets)
    }

    @Test
    fun `the count first, then facets 45 ms apart, a pause between families, each name with its first facet`() {
        val runs = listOf(IntroGemRun("joy", listOf("a", "b", "c")), IntroGemRun("calm", listOf("d", "e")))
        val beats = IntroScore.gem(runs, pebbles = 12)

        fun at(key: IntroKey) = beats.first { it.key == key }.at
        assertTrue(at(IntroKey.Lead) > at(IntroKey.Header))
        assertTrue(at(IntroKey.Blank) < at(IntroKey.Count))
        val count = beats.first { it.key == IntroKey.Count }
        assertEquals(count.at + count.duration + IntroScore.COUNT_AFTER, at(IntroKey.Facet("a")), 1e-12)
        assertEquals(IntroScore.FACET_GAP, at(IntroKey.Facet("b")) - at(IntroKey.Facet("a")), 1e-12)
        assertEquals(IntroScore.FACET_GAP + IntroScore.FAMILY_PAUSE, at(IntroKey.Facet("d")) - at(IntroKey.Facet("c")), 1e-12)
        assertEquals(at(IntroKey.Facet("a")), at(IntroKey.Label("joy")), 0.0)
        assertEquals(at(IntroKey.Facet("d")), at(IntroKey.Label("calm")), 0.0)
        assertEquals(at(IntroKey.Facet("e")), at(IntroKey.More), 0.0)
        assertTrue(beats.filter { it.key is IntroKey.Facet }.all { it.tick == IntroTick.facet })
    }

    @Test
    fun `the count rises to the month's pebbles slow-fast-slow, a tick per pebble, landing firm`() {
        val beats = IntroScore.gem(emptyList(), pebbles = 24)
        val count = beats.first { it.key == IntroKey.Count }
        assertEquals(IntroScore.COUNT_AT, count.at, 0.0)
        assertEquals(IntroScore.countSpan(24), count.duration, 0.0)
        val tallies = (1..24).mapNotNull { t -> beats.firstOrNull { it.key == IntroKey.Tally(t) } }
        assertEquals(24, tallies.size)
        assertTrue(tallies.dropLast(1).all { it.tick == IntroTick.light })
        assertEquals(IntroTick.firm, tallies.last().tick)
        assertEquals(count.at + count.duration, tallies.last().at, 1e-12)
        // Accelerating, then landing slowly: the middle ticks are closer than the first and the last.
        val gaps = tallies.zipWithNext { a, b -> b.at - a.at }
        assertTrue(gaps[11] < gaps[0])
        assertTrue(gaps[11] < gaps.last())
        // Each tick is the moment the shown count reaches its pebble.
        tallies.forEach { tally ->
            val pebble = (tally.key as IntroKey.Tally).pebble
            assertEquals(pebble, IntroScore.counted(24, (tally.at - count.at) / count.duration))
        }
        assertEquals(0, IntroScore.counted(24, 0.0))
    }

    @Test
    fun `a fuller month counts for longer, within bounds - an empty one has no tally`() {
        assertEquals(0.6, IntroScore.countSpan(0), 0.0)
        assertTrue(IntroScore.countSpan(50) > IntroScore.countSpan(10))
        assertEquals(1.2, IntroScore.countSpan(1000), 0.0)
        assertFalse(IntroScore.gem(emptyList(), 0).any { it.key is IntroKey.Tally })
    }
}
