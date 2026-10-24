package app.pbbls.android.core.model.wrap

import app.pbbls.android.core.model.ValencePolarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The wraps decoded from the server's own payloads (#1207), captured verbatim by
 * `verify-path-wraps.ts` (PATH_WRAPS_DUMP=1) from the round-2 fixture: the strip of a reader seeded
 * from July to September, the fixture's weeks 40–42 and a live week, and its October, a thin May and
 * a quiet June. The expected values are the ones the harness asserts on the server; this checks the
 * client reads the same payload the same way.
 */
class WrapPayloadDecodingTest {
    private fun payload(name: String): String =
        requireNotNull(javaClass.getResource("/$name.json")) { "missing payload fixture $name.json" }.readText()

    private fun strip() = WrapJson.decodeFromString<StripDto>(payload("strip")).toModel()

    private fun week(name: String) = WrapJson.decodeFromString<WeekWrapDto>(payload(name)).toModel()

    private fun month(name: String) = WrapJson.decodeFromString<MonthWrapDto>(payload(name)).toModel()

    @Test
    fun `the strip runs from the first moment's week to the live week, months after their last week`() {
        val items = strip().items
        val first = items.first() as StripItem.Week
        assertEquals(LocalDate.of(2026, 7, 6), first.weekStart)
        val last = items.last() as StripItem.Week
        assertTrue(last.live && last.focusable)
        val months = items.withIndex().filter { it.value is StripItem.Month }
        assertEquals(
            listOf(YearMonth.of(2026, 7) to 1, YearMonth.of(2026, 8) to 3, YearMonth.of(2026, 9) to 1),
            months.map { (it.value as StripItem.Month).let { m -> m.month to m.pebbles } },
        )
        assertEquals(
            listOf(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 28)),
            months.map { (items[it.index - 1] as StripItem.Week).weekStart },
        )
        val aug10 = items.filterIsInstance<StripItem.Week>().first { it.weekStart == LocalDate.of(2026, 8, 10) }
        assertEquals(DayWeight(2, 3), aug10.days[2])
        assertEquals(DayWeight(1, 0), aug10.days[4])
    }

    @Test
    fun `a full week carries every tile and its data`() {
        val w = week("week-full")
        assertEquals(WeekTile.entries, w.tiles)
        assertEquals(11, w.pebbles)
        assertEquals(5, w.people)
        assertEquals(4, w.persons!!.first().count)
        assertEquals(
            setOf(0, 1, 3, 5),
            w.persons!!
                .first()
                .days.days,
        )
        assertEquals(listOf("joy", "peace", "pride", "anger", "sadness", "fear"), w.families!!.map { it.slug })
        assertEquals(7.0, w.families!!.first().worth, 0.0)
        assertEquals(listOf("shame"), w.lead!!.zero)
        assertEquals(WeightGrid(listOf(0, 2, 3), listOf(2, 1, 0), listOf(1, 2, 0)), w.weight)
        assertEquals(9, w.domains!!.size)
        assertEquals(11, w.hours!!.size)
        assertEquals(10.25, w.hours!!.first().happened, 0.0)
        assertEquals(5, w.kept!!.photoMomentIds.size)
        assertEquals(listOf(2, 1, 1, 3, 1, 2, 1), w.kept!!.cards.map { it.count })
    }

    @Test
    fun `a week with nobody named carries the invitation's moments, and no stats`() {
        val w = week("week-nobody-named")
        assertEquals(listOf(WeekTile.CONTENTS, WeekTile.PERSONS), w.tiles)
        assertEquals(emptyList<PersonStat>(), w.persons)
        assertEquals(3, w.inviteMomentIds!!.size)
        assertNull(w.families)
        assertNull(w.weight)
    }

    @Test
    fun `one pebble gets no tile, and the live week no stats`() {
        val one = week("week-one-pebble")
        assertEquals(emptyList<WeekTile>(), one.tiles)
        assertNull(one.persons)
        val live = week("week-live")
        assertTrue(live.live)
        assertEquals(listOf(WeekTile.CONTENTS, WeekTile.PERSONS), live.tiles)
        assertNull(live.families)
    }

    @Test
    fun `October carries every section`() {
        val m = month("month-full")
        assertEquals(YearMonth.of(2026, 10), m.month)
        assertEquals(31, m.daysInMonth)
        assertEquals(DayOfWeek.THURSDAY, m.firstWeekday)
        assertEquals(24, m.pebbles)
        assertEquals(MonthSection.entries, m.sections)
        assertEquals(19, m.cover!!.days.size)
        assertEquals(
            ValencePolarity.LOWLIGHT,
            m.cover!!
                .days
                .first()
                .pebbles
                .first()
                .polarity,
        )
        assertEquals(listOf(1, 11, 3, 4, 5), m.weeks!!.map { it.pebbles })
        assertEquals(5, m.milestones!!.size)
        assertEquals(listOf(false, false, true, false, true, true), m.persons!!.map { it.newSincePrevious })
        assertEquals(3, m.previousMonth!!.pebbles)
        assertEquals(28, m.gem!!.facets.size)
        assertEquals("joy", m.gem!!.main)
        assertEquals(14.0, m.gem!!.worths.getValue("joy"), 0.0)
        assertEquals(ToneVerdict.BRIGHT_TWO_TO_ONE, m.tone!!.verdict)
        assertEquals(ValencePolarity.LOWLIGHT, m.tone!!.first)
        assertEquals(ValencePolarity.HIGHLIGHT, m.tone!!.last)
        assertEquals(31, m.tone!!.days.size)
        assertEquals(ToneDay(0, 0, 3), m.tone!!.days[0])
        assertEquals(ToneDay(5, 0, 0), m.tone!!.days[4])
        assertEquals(24, m.hours!!.items.size)
        assertEquals(24, m.hours!!.busiest!!.count)
    }

    @Test
    fun `a thin month keeps every section, a quiet month only its cover and weeks`() {
        val thin = month("month-thin")
        assertEquals(MonthSection.entries, thin.sections)
        assertEquals(3, thin.milestones!!.size)
        val quiet = month("month-quiet")
        assertEquals(listOf(MonthSection.WEEKS), quiet.sections)
        assertEquals(2, quiet.cover!!.days.size)
        assertNull(quiet.persons)
        assertNull(quiet.gem)
        assertNull(quiet.tone)
    }
}
