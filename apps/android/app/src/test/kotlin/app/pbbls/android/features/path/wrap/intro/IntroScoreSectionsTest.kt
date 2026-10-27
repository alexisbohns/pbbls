package app.pbbls.android.features.path.wrap.intro

import app.pbbls.android.core.model.wrap.DaySet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `IntroScoreSectionsTests.arrival`, `.persons`, `.nobody` and `IntroScoreInsightsTests.insights`' head. */
class IntroScoreSectionsTest {
    @Test
    fun `a section's head is drawn from the start, its beat the arrival's tick alone`() {
        val header = IntroScore.head(hasLead = false, insights = false).first { it.key == IntroKey.Header }
        assertEquals(0.0, header.at, 0.0)
        assertEquals(0.0, header.duration, 0.0)
        assertEquals(IntroTick.arrive, header.tick)
    }

    @Test
    fun `leading the insights, their head ticks the arrival and the section's own head is silent, later`() {
        val beats = IntroScore.head(hasLead = true, insights = true)
        assertEquals(IntroKey.Insights, beats.first().key)
        assertEquals(IntroTick.arrive, beats.first().tick)
        val header = beats.first { it.key == IntroKey.Header }
        assertEquals(IntroScore.INSIGHTS_SHIFT, header.at, 0.0)
        assertNull(header.tick)
        assertEquals(IntroScore.INSIGHTS_SHIFT + IntroScore.LEAD_AT, beats.first { it.key == IntroKey.Lead }.at, 1e-12)
    }

    @Test
    fun `the rest of a section shifts only under the insights head`() {
        assertEquals(0.0, IntroScore.shift(insights = false), 0.0)
        assertEquals(IntroScore.INSIGHTS_SHIFT, IntroScore.shift(insights = true), 0.0)
    }

    @Test
    fun `a person - glyph, name, then the ring, a dot as the line reaches each day, the next row after`() {
        val rings = listOf(DaySet(31, setOf(0, 15, 30)), DaySet(31, setOf(3)))
        val beats = IntroScore.persons(rings, hasLead = false)

        fun of(key: IntroKey) = beats.first { it.key == key }
        val glyph = of(IntroKey.Glyph(0))
        val name = of(IntroKey.Name(0))
        val ring = of(IntroKey.Ring(0))
        assertEquals(IntroScore.ROWS_AT, glyph.at, 0.0)
        assertEquals(IntroScore.PART_GAP, name.at - glyph.at, 1e-12)
        assertEquals(IntroScore.PART_GAP, ring.at - name.at, 1e-12)
        assertEquals(IntroScore.RING_SPAN, ring.duration, 0.0)
        assertNull(ring.tick)
        val dots = listOf(0, 15, 30).map { of(IntroKey.Dot(0, it)) }
        assertTrue(dots.zipWithNext { a, b -> b.at > a.at }.all { it })
        // The middle day sits at the ring's middle: the eased line reaches it half way through.
        assertEquals(ring.at + IntroScore.RING_SPAN / 2, dots[1].at, 1e-9)
        assertTrue(dots.all { it.tick == IntroTick.light })
        assertEquals(IntroScore.NEXT_ROW, of(IntroKey.Glyph(1)).at - (ring.at + IntroScore.RING_SPAN), 1e-12)
        assertTrue(beats.any { it.key == IntroKey.Dot(1, 3) })
        // The head (no lead), then per row glyph + name + ring + its days, then the invitation to see more.
        assertEquals(1 + rings.sumOf { 3 + it.days.size } + 1, beats.size)
        assertEquals(of(IntroKey.Ring(1)).let { it.at + it.duration }, of(IntroKey.More).at, 1e-12)
    }

    @Test
    fun `a ring's days beat in day order, whatever the set's own order`() {
        val beats = IntroScore.persons(listOf(DaySet(30, linkedSetOf(20, 4, 11))), hasLead = false)
        val dots = beats.mapNotNull { (it.key as? IntroKey.Dot)?.day }
        assertEquals(listOf(4, 11, 20), dots)
    }

    @Test
    fun `nobody named - the head and the invitation, nothing else`() {
        assertEquals(listOf(IntroKey.Header, IntroKey.Lead), IntroScore.persons(emptyList(), hasLead = true).map { it.key })
    }

    @Test
    fun `a day sits in the middle of its slot, the ring opening before the 1st`() {
        assertEquals(0.5 / 31, IntroScore.dotPlace(0, 31), 1e-12)
        assertEquals(0.5, IntroScore.dotPlace(15, 31), 1e-12)
        assertEquals(0.0, IntroScore.dotPlace(3, 0), 0.0)
    }
}
