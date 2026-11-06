package app.pbbls.android.features.path.flow.steps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The end screen's week: Monday first, the marked day where the pebble landed. */
class RicherWeekRowTest {
    @Test
    fun `the week around a Wednesday runs Monday to Sunday and marks the Wednesday`() {
        // 2026-10-14 is a Wednesday (checked with `date`).
        val days = richerWeek(LocalDate.of(2026, 10, 14))

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 12), days.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), days.last().date)
        assertEquals(listOf(2), days.indices.filter { days[it].isMarked })
        assertEquals(LocalDate.of(2026, 10, 14), days[2].date)
    }

    @Test
    fun `a Monday marks the first day and a Sunday the last`() {
        assertEquals(0, richerWeek(LocalDate.of(2026, 10, 12)).indexOfFirst { it.isMarked })
        val sunday = richerWeek(LocalDate.of(2026, 10, 18))
        assertEquals(LocalDate.of(2026, 10, 12), sunday.first().date)
        assertEquals(6, sunday.indexOfFirst { it.isMarked })
    }

    @Test
    fun `a week that crosses a month keeps its seven consecutive days`() {
        // 2026-11-01 is a Sunday: the week starts on 2026-10-26.
        val days = richerWeek(LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 10, 26), days.first().date)
        assertEquals((0L..6L).map { LocalDate.of(2026, 10, 26).plusDays(it) }, days.map { it.date })
    }
}
