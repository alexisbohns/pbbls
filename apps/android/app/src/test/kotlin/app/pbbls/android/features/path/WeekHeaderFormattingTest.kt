package app.pbbls.android.features.path

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WeekHeaderFormattingTest {
    // Monday 2026-05-04 → Sunday 2026-05-10; "today" in the same week-based year.
    private val weekStart = LocalDate.of(2026, 5, 4)
    private val today = LocalDate.of(2026, 7, 11)

    @Test
    fun `formats an english month-day range`() {
        assertEquals(
            "May 4 · May 10",
            WeekHeaderFormatting.formatRange(weekStart, today, Locale.ENGLISH),
        )
    }

    @Test
    fun `formats a french day-month range`() {
        assertEquals(
            "4 mai · 10 mai",
            WeekHeaderFormatting.formatRange(weekStart, today, Locale.FRENCH),
        )
    }

    @Test
    fun `appends the week-based year only when it differs from today's`() {
        val lastYear = LocalDate.of(2025, 5, 5)
        assertEquals(
            "May 5 · May 11 · 2025",
            WeekHeaderFormatting.formatRange(lastYear, today, Locale.ENGLISH),
        )
    }

    @Test
    fun `week 1 spanning the calendar boundary carries no year suffix`() {
        // Monday 2025-12-29 belongs to week-based-year 2026 — same as "today"
        // (2026-01-02), so no suffix despite the calendar year differing.
        assertEquals(
            "December 29 · January 4",
            WeekHeaderFormatting.formatRange(
                LocalDate.of(2025, 12, 29),
                LocalDate.of(2026, 1, 2),
                Locale.ENGLISH,
            ),
        )
    }

    @Test
    fun `month-day pattern strips year tokens for both locales`() {
        assertEquals("MMMM d", WeekHeaderFormatting.monthDayPattern(Locale.ENGLISH))
        assertEquals("d MMMM", WeekHeaderFormatting.monthDayPattern(Locale.FRENCH))
    }
}
