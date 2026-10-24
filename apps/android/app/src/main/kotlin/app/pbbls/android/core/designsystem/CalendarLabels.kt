package app.pbbls.android.core.designsystem

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Weekday labels for display. Which day belongs to which week is the server's call (#1207); these only name days. */
object CalendarLabels {
    /** "mon", "lun": the locale's short weekday, lowercase, without the abbreviation point. */
    fun shortWeekday(
        dayOfWeek: DayOfWeek,
        locale: Locale,
    ): String =
        dayOfWeek
            .getDisplayName(TextStyle.SHORT, locale)
            .lowercase(locale)
            .trimEnd('.')

    /** "m t w t f s s", "l m m j v s d": a week's day letters, from its first day. */
    fun weekdayLetters(
        weekStart: LocalDate,
        locale: Locale,
    ): List<String> =
        (0 until DAYS_IN_WEEK).map { offset ->
            weekStart
                .plusDays(offset.toLong())
                .dayOfWeek
                .getDisplayName(TextStyle.NARROW, locale)
                .lowercase(locale)
        }

    private const val DAYS_IN_WEEK = 7
}
