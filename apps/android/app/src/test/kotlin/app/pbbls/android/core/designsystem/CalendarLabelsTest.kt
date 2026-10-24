package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** Moved with the labels from `WeekWordsTest` (the web's `week-opening.test.ts` vectors), plus the short weekday. */
class CalendarLabelsTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH
    private val w41 = LocalDate.of(2026, 10, 5)

    @Test
    fun `weekday letters run Monday first, lowercase`() {
        assertEquals(listOf("m", "t", "w", "t", "f", "s", "s"), CalendarLabels.weekdayLetters(w41, en))
        assertEquals(listOf("l", "m", "m", "j", "v", "s", "d"), CalendarLabels.weekdayLetters(w41, fr))
    }

    @Test
    fun `short weekdays are lowercase, without the abbreviation point`() {
        assertEquals("mon", CalendarLabels.shortWeekday(DayOfWeek.MONDAY, en))
        assertEquals("lun", CalendarLabels.shortWeekday(DayOfWeek.MONDAY, fr))
    }
}
