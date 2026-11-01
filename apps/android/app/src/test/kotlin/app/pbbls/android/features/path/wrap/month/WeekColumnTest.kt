package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** iOS `MonthCardsTests.weekColumns` / `.weekColumnShape` (#1241 D17), on the server's own October payload. */
class WeekColumnTest {
    private val october =
        WrapJson.decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText()).toModel()

    @Test
    fun `a week's column places each day by its date - outside the month blank, then empty or its pebbles`() {
        val columns = WeekColumn.columns(october)
        assertEquals(5, columns.size)
        // The week of 28 September: Monday to Wednesday are September's.
        val first = columns.first()
        assertTrue(first.days.take(3).all { it == WeekColumnDay.Outside })
        assertNotEquals(WeekColumnDay.Outside, first.days[3])
        // The week of 26 October: its Sunday is November's.
        val last = columns.last()
        assertEquals(WeekColumnDay.Outside, last.days[6])
        assertNotEquals(WeekColumnDay.Outside, last.days[5])
        // The 1st carries the cover's pebbles for day 0.
        val october1 =
            october.cover
                ?.days
                ?.firstOrNull { it.day == 0 }
                ?.pebbles
                .orEmpty()
        assertEquals(if (october1.isEmpty()) WeekColumnDay.Empty else WeekColumnDay.Pebbles(october1), first.days[3])
    }

    @Test
    fun `a column's intro shows three pebbles at most a day, and its cairn's stones`() {
        val pebble = CoverPebble("m", null, 2, ValencePolarity.NEUTRAL)
        val quiet = DayWeight(0, 0)
        val days = listOf(DayWeight(5, 2)) + List(6) { quiet }
        val week = MonthWeek(LocalDate.of(2026, 10, 5), 4, 10, 5, emptyList(), days)
        val column = WeekColumn(week, listOf(WeekColumnDay.Pebbles(List(5) { pebble })) + List(6) { WeekColumnDay.Empty })
        assertEquals(3, column.shape.days.first())
        assertTrue(
            column.shape.days
                .drop(1)
                .all { it == 0 },
        )
        assertEquals(1, column.shape.stones)
        // Moments without a pebble: the column draws the dotted stone, and the intro plays it as one.
        assertEquals(0, WeekColumn(week.copy(pebbles = 0), column.days).shape.stones)
    }

    /**
     * Below one pebble `path_month_wrap` sends no `weeks` and no `cover`, only the month's frame: 1 November 2026
     * is a Sunday (`first_weekday` 6, Monday = 0) and the month has 30 days.
     */
    private val emptyNovember =
        WrapJson
            .decodeFromString<MonthWrapDto>(
                """{"month":"2026-11-01","days_in_month":30,"first_weekday":6,"count":0,"people_count":0,"sections":[]}""",
            ).toModel()

    @Test
    fun `home lays an empty month out from the served frame - a column per monday, every week empty`() {
        val columns = WeekColumn.columns(emptyNovember, layOutEmptyMonth = true)
        assertEquals(6, columns.size)
        assertEquals(LocalDate.of(2026, 10, 26), columns.first().week.weekStart)
        assertEquals(LocalDate.of(2026, 11, 30), columns.last().week.weekStart)
        // The first column holds only the 1st, a Sunday.
        val first = columns.first()
        assertTrue(first.days.take(6).all { it == WeekColumnDay.Outside })
        assertEquals(WeekColumnDay.Empty, first.days[6])
        assertEquals(0, first.week.from)
        assertEquals(0, first.week.to)
        // The last column holds only the 30th, a Monday.
        val last = columns.last()
        assertEquals(WeekColumnDay.Empty, last.days[0])
        assertTrue(last.days.drop(1).all { it == WeekColumnDay.Outside })
        assertEquals(29, last.week.from)
        assertEquals(29, last.week.to)
        // Every week is empty: no pebble, no soul, the dotted cairn.
        assertTrue(columns.all { it.week.pebbles == 0 && it.week.soulIds.isEmpty() && it.shape.stones == 0 })
        assertTrue(columns.flatMap { it.days }.none { it is WeekColumnDay.Pebbles })
    }

    @Test
    fun `the month card keeps an empty month without columns`() {
        assertTrue(WeekColumn.columns(emptyNovember).isEmpty())
    }

    @Test
    fun `a month starting on a monday begins its first column on the 1st`() {
        val june =
            WrapJson
                .decodeFromString<MonthWrapDto>(
                    """{"month":"2026-06-01","days_in_month":30,"first_weekday":0,"count":0,"people_count":0,"sections":[]}""",
                ).toModel()
        val columns = WeekColumn.columns(june, layOutEmptyMonth = true)
        assertEquals(5, columns.size)
        assertEquals(LocalDate.of(2026, 6, 1), columns.first().week.weekStart)
        assertEquals(listOf(0 to 6, 7 to 13, 14 to 20, 21 to 27, 28 to 29), columns.map { it.week.from to it.week.to })
    }
}
