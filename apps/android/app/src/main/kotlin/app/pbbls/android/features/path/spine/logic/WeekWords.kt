package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.common.NumberWords
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

/** The week's title in words: "the fifth to the eleventh of October", or across two months. */
sealed interface WeekTitle {
    data class SameMonth(
        val from: String,
        val to: String,
        val month: String,
    ) : WeekTitle

    data class CrossMonth(
        val from: String,
        val fromMonth: String,
        val to: String,
        val toMonth: String,
    ) : WeekTitle
}

/**
 * The words a week opens and closes with: its ordinal, its title in words, and
 * the week after it. The string resources set the sentence; these are the
 * arguments.
 */
data class WeekWords(
    val number: Int,
    val year: Int,
    /** "forty-first", "quarante et unième". */
    val ordinal: String,
    val title: WeekTitle,
    /**
     * The calendar's neighbours, as the web's `words.previous.number` / `words.next.number`.
     * Kept against the web vectors; the Spine's links name the pager's neighbour instead
     * ([app.pbbls.android.features.path.spine.WeekNeighbour]).
     */
    val previousNumber: Int,
    val nextNumber: Int,
    val nextOrdinal: String,
    val nextMonday: String,
)

/**
 * Port of `week-opening.ts` (#1052, spec §Type ranks of the opening, §i18n).
 * A week is feminine in French ("la quarante et unième semaine").
 */
object WeekWordsBuilder {
    private const val DAYS_IN_WEEK = 7L

    private fun isFrench(locale: Locale) = locale.language == "fr"

    private fun monthName(
        date: LocalDate,
        locale: Locale,
    ): String = date.month.getDisplayName(TextStyle.FULL, locale)

    private fun weekNumber(monday: LocalDate): Int = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun of(
        weekStart: LocalDate,
        locale: Locale,
    ): WeekWords {
        val sunday = weekStart.plusDays(DAYS_IN_WEEK - 1)
        val nextMonday = weekStart.plusDays(DAYS_IN_WEEK)
        val number = weekNumber(weekStart)
        val nextNumber = weekNumber(nextMonday)
        val from = NumberWords.dayOfMonth(weekStart.dayOfMonth, locale)
        val to = NumberWords.dayOfMonth(sunday.dayOfMonth, locale)
        val title =
            if (weekStart.month == sunday.month) {
                WeekTitle.SameMonth(from = from, to = to, month = monthName(sunday, locale))
            } else {
                WeekTitle.CrossMonth(
                    from = from,
                    fromMonth = monthName(weekStart, locale),
                    to = to,
                    toMonth = monthName(sunday, locale),
                )
            }
        return WeekWords(
            number = number,
            year = weekStart.get(IsoFields.WEEK_BASED_YEAR),
            ordinal = NumberWords.ordinal(number, locale, NumberWords.Gender.FEMININE),
            title = title,
            previousNumber = weekNumber(weekStart.minusDays(DAYS_IN_WEEK)),
            nextNumber = nextNumber,
            nextOrdinal = NumberWords.ordinal(nextNumber, locale, NumberWords.Gender.FEMININE),
            nextMonday = NumberWords.dayOfMonth(nextMonday.dayOfMonth, locale),
        )
    }

    /**
     * The summary's numbers in words, as (pebbles, people): pebbles are
     * masculine in French ("un galet"), people feminine ("une personne").
     */
    fun summary(
        pebbles: Int,
        people: Int,
        locale: Locale,
    ): Pair<String, String> =
        NumberWords.cardinal(pebbles, locale, NumberWords.Gender.MASCULINE) to
            NumberWords.cardinal(people, locale, NumberWords.Gender.FEMININE)

    /** "eleven pebbles…" → "Eleven pebbles…": the summary opens a sentence. */
    fun sentenceCase(
        text: String,
        locale: Locale,
    ): String = if (text.isEmpty()) text else text.substring(0, 1).uppercase(locale) + text.substring(1)

    /** "m t w t f s s", "l m m j v s d": the persons' day header, Monday first. */
    fun weekdayLetters(
        weekStart: LocalDate,
        locale: Locale,
    ): List<String> =
        (0 until DAYS_IN_WEEK).map { offset ->
            weekStart
                .plusDays(offset)
                .dayOfWeek
                .getDisplayName(TextStyle.NARROW, locale)
                .lowercase(locale)
        }

    /**
     * The days a person's dots mark, as a screen reader hears them, with their
     * count: "Monday 5, Tuesday 6, and Thursday 8". Composed rather than
     * formatted, because English `{ weekday, day }` formats as "5 Monday".
     */
    fun dotDays(
        days: BooleanArray,
        weekStart: LocalDate,
        locale: Locale,
    ): Pair<Int, String> {
        val named =
            days.withIndex().filter { it.value }.map { (index, _) ->
                val date = weekStart.plusDays(index.toLong())
                "${date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)} ${date.dayOfMonth}"
            }
        return named.size to joinList(named, locale)
    }

    /**
     * The web's `Intl.ListFormat`, by hand: `android.icu` is off-limits in pure
     * JVM code. English keeps the serial comma ("A, B, and C"); French has none
     * ("A, B et C").
     */
    private fun joinList(
        items: List<String>,
        locale: Locale,
    ): String {
        val and = if (isFrench(locale)) " et " else " and "
        return when (items.size) {
            0 -> ""
            1 -> items.single()
            2 -> items[0] + and + items[1]
            else -> {
                val last = if (isFrench(locale)) and else ",$and"
                items.dropLast(1).joinToString(", ") + last + items.last()
            }
        }
    }
}
