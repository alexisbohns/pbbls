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
}
