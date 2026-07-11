package app.pbbls.android.features.path

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Pure formatting for the week header pill — mirrors iOS
 * `WeekHeaderView.formatRange`: "May 4 · May 10" (en) / "4 mai · 10 mai"
 * (fr), with a " · <weekYear>" suffix only when the week-based year differs
 * from today's. Locale is injected so tests stay hermetic; production passes
 * the active locale. The caller uppercases the result (meta styling).
 */
object WeekHeaderFormatting {
    fun formatRange(
        weekStart: LocalDate,
        today: LocalDate,
        locale: Locale,
    ): String {
        val formatter = DateTimeFormatter.ofPattern(monthDayPattern(locale), locale)
        val startLabel = weekStart.format(formatter)
        val endLabel = weekStart.plusDays(6).format(formatter)

        val weekYear = weekStart.get(IsoFields.WEEK_BASED_YEAR)
        val todayYear = today.get(IsoFields.WEEK_BASED_YEAR)

        return if (weekYear != todayYear) {
            "$startLabel · $endLabel · $weekYear"
        } else {
            "$startLabel · $endLabel"
        }
    }

    /**
     * Locale-ordered month+day pattern (the `.month(.wide).day()` analog):
     * derives the locale's LONG date pattern and strips the year tokens plus
     * any separator they leave behind — "MMMM d, y" → "MMMM d" (en),
     * "d MMMM y" → "d MMMM" (fr).
     */
    internal fun monthDayPattern(locale: Locale): String {
        val longPattern =
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.LONG,
                null,
                IsoChronology.INSTANCE,
                locale,
            )
        return longPattern
            .replace(Regex("[,\\s]*[yY]+[,\\s]*"), " ")
            .trim()
            .ifEmpty { "MMMM d" }
    }
}
