package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverDay
import app.pbbls.android.core.model.wrap.CoverPebble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreCoverTests` (#1241 §Choreography, the cover). */
class IntroScoreCoverTest {
    private fun pebble(intensity: Int) = CoverPebble("m", null, intensity, ValencePolarity.HIGHLIGHT)

    private fun List<IntroBeat>.of(key: IntroKey) = first { it.key == key }

    @Test
    fun `one beat per day from the 1st, an empty day faint, a seal by its intensity, a stacked seal a step later`() {
        val days = listOf(CoverDay(0, listOf(pebble(3))), CoverDay(4, listOf(pebble(1), pebble(2))))
        val beats = IntroScore.cover(days, daysInMonth = 31, numbered = listOf(0, 4, 30), souls = 0, lines = emptyList())
        val slots = (0 until 31).map { beats.of(IntroKey.Slot(it)) }
        assertTrue(slots.zipWithNext { a, b -> b.at > a.at }.all { it })
        assertEquals(IntroScore.GARLAND_AT, slots.first().at, 0.0)
        assertEquals(IntroTick.seal(3), slots[0].tick)
        assertEquals(IntroTick.faint, slots[1].tick)
        assertEquals(IntroScore.STACK_GAP, beats.of(IntroKey.Seal(4, 1)).at - slots[4].at, 1e-12)
        val numerals = beats.mapNotNull { (it.key as? IntroKey.Numeral)?.day }
        assertEquals(listOf(0, 4, 30), numerals)
        assertEquals(slots[4].at, beats.of(IntroKey.Numeral(4)).at, 0.0)
    }

    @Test
    fun `a stacked seal on the 1st ticks on its own, the slot keeping its first seal's`() {
        val beats = IntroScore.cover(listOf(CoverDay(0, listOf(pebble(1), pebble(3)))), 31, emptyList(), 0, emptyList())
        assertEquals(IntroTick.seal(1), beats.of(IntroKey.Slot(0)).tick)
        assertEquals(IntroTick.seal(3), beats.of(IntroKey.Seal(0, 1)).tick)
    }

    @Test
    fun `a stacked seal mid-garland, too close to the next day, is silent, its day ticks its strongest seal`() {
        val beats = IntroScore.cover(listOf(CoverDay(15, listOf(pebble(1), pebble(3)))), 31, emptyList(), 0, emptyList())
        val slot = beats.of(IntroKey.Slot(15))
        val stacked = beats.of(IntroKey.Seal(15, 1))
        assertNull(stacked.tick)
        assertEquals(IntroScore.STACK_GAP, stacked.at - slot.at, 1e-12)
        assertEquals(IntroTick.seal(3), slot.tick)
    }

    @Test
    fun `the name before the garland, the leaf ties it, then the souls and the lines, in order`() {
        val beats = IntroScore.cover(emptyList(), 30, emptyList(), souls = 2, lines = listOf(IntroKey.Summary, IntroKey.Colophon))
        val name = beats.of(IntroKey.MonthName)
        assertEquals(IntroTick.light, name.tick)
        assertTrue(name.at < IntroScore.GARLAND_AT)
        val leaf = beats.of(IntroKey.Leaf)
        assertEquals(IntroTick.knot, leaf.tick)
        assertTrue(leaf.at > beats.of(IntroKey.Slot(29)).at)
        assertEquals(
            listOf(IntroKey.Soul(0), IntroKey.Soul(1), IntroKey.Summary, IntroKey.Colophon),
            beats.filter { it.at > leaf.at }.map { it.key },
        )
        assertFalse(beats.any { it.key == IntroKey.Names })
        assertEquals(beats.last().at + IntroMotion.RISE, IntroScore.duration(beats), 1e-12)
    }
}
