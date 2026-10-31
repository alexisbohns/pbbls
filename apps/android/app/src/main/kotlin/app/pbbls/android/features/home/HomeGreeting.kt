package app.pbbls.android.features.home

import android.content.res.Resources
import androidx.annotation.StringRes
import app.pbbls.android.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home's greeting (#1383; iOS `HomeGreeting`): a title, an italic line and a dateline, from the
 * hour band (morning before 12, afternoon 12 to 18, evening from 18) with the Monday-morning and
 * Sunday-evening overrides. The clock and the locale are passed in, so a test or a preview pins
 * every band in both languages; the caller reads the clock in the device's zone.
 */
internal object HomeGreeting {
    data class Lines(
        val title: String,
        val line: String,
        val dateline: String,
    ) {
        /**
         * One TalkBack node, title first, as iOS reads it. A part that already ends in its own
         * punctuation ("What stays with you from today?") takes no extra period.
         */
        val spoken: String get() = listOf(title, line, dateline).joinToString(" ") { it.sentence() }

        private fun String.sentence(): String = if (isEmpty() || last() in ".?!…") this else "$this."
    }

    enum class Band { MORNING, AFTERNOON, EVENING }

    /** The copy a moment of the week gets: a band, or one of the two overrides. */
    enum class Key(
        @StringRes val title: Int,
        @StringRes val line: Int,
    ) {
        MORNING(R.string.home_greeting_morning_title, R.string.home_greeting_morning_line),
        AFTERNOON(R.string.home_greeting_afternoon_title, R.string.home_greeting_afternoon_line),
        EVENING(R.string.home_greeting_evening_title, R.string.home_greeting_evening_line),
        MONDAY_MORNING(R.string.home_greeting_monday_morning_title, R.string.home_greeting_monday_morning_line),
        SUNDAY_EVENING(R.string.home_greeting_sunday_evening_title, R.string.home_greeting_sunday_evening_line),
    }

    /** Day before month in both shipped languages, no year: "Saturday 31 October", "samedi 31 octobre". */
    private const val DATELINE = "EEEE d MMMM"

    fun band(hour: Int): Band =
        when {
            hour < 12 -> Band.MORNING
            hour < 18 -> Band.AFTERNOON
            else -> Band.EVENING
        }

    fun key(now: LocalDateTime): Key {
        val band = band(now.hour)
        return when {
            now.dayOfWeek == DayOfWeek.MONDAY && band == Band.MORNING -> Key.MONDAY_MORNING
            now.dayOfWeek == DayOfWeek.SUNDAY && band == Band.EVENING -> Key.SUNDAY_EVENING
            band == Band.MORNING -> Key.MORNING
            band == Band.AFTERNOON -> Key.AFTERNOON
            else -> Key.EVENING
        }
    }

    fun lines(
        now: LocalDateTime,
        name: String,
        resources: Resources,
        locale: Locale,
    ): Lines {
        val key = key(now)
        val greeting = resources.getString(key.title)
        val trimmed = name.trim()
        val title = if (trimmed.isEmpty()) greeting else resources.getString(R.string.home_greeting_named, greeting, trimmed)
        return Lines(title = title, line = resources.getString(key.line), dateline = dateline(now.toLocalDate(), locale))
    }

    fun dateline(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofPattern(DATELINE, locale).format(date)
}
