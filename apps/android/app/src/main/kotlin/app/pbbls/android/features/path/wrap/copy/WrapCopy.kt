package app.pbbls.android.features.path.wrap.copy

import android.content.res.Resources
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.wrap.ToneVerdict
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/**
 * The wraps' sentences (#1207, spec §1 and §8): the server sends counts, slugs and semantic values,
 * and this is the one place they become words. Plain functions over [Resources], so a test reads
 * the real `strings.xml` in English and French. Names (families, domains, moments) arrive already
 * localised; the sentences only arrange them.
 */
class WrapCopy(
    private val resources: Resources,
    private val locale: Locale,
) {
    /** "Eleven pebbles, five people." / "…, five people so far." / "Nothing yet." / "No pebble yet." */
    fun line(
        pebbles: Int,
        people: Int,
        live: Boolean,
        hasMoments: Boolean,
    ): String {
        if (pebbles == 0) return resources.getString(if (hasMoments) R.string.path_wrap_line_no_pebble else R.string.path_wrap_line_nothing)
        val pebbleWords = words(pebbles)
        val peopleWords = words(people, NumberWords.Gender.FEMININE)
        val pebblePart = resources.getQuantityString(R.plurals.path_spine_summary_pebbles, pebbles).format(locale, pebbleWords)
        val peoplePart =
            if (people ==
                0
            ) {
                ""
            } else {
                resources.getQuantityString(R.plurals.path_spine_summary_people, people).format(locale, peopleWords)
            }
        val soFar = if (live) " " + soFar() else ""
        return WeekWordsBuilder.sentenceCase("$pebblePart$peoplePart$soFar.", locale)
    }

    fun soFar(): String = resources.getString(R.string.path_wrap_so_far)

    fun weightCaption(): String = resources.getString(R.string.path_wrap_weight_caption)

    fun tile(tile: WeekTile): String =
        resources.getString(
            when (tile) {
                WeekTile.CONTENTS -> R.string.path_wrap_tile_contents
                WeekTile.PERSONS -> R.string.path_wrap_tile_persons
                WeekTile.EMOTIONS -> R.string.path_wrap_tile_emotions
                WeekTile.DOMAINS -> R.string.path_wrap_tile_domains
                WeekTile.WEIGHT -> R.string.path_wrap_tile_weight
                WeekTile.HOURS -> R.string.path_wrap_tile_hours
                WeekTile.KEPT -> R.string.path_wrap_tile_kept
            },
        )

    /** A tile's one node: "contents: thirteen. Opens the list." */
    fun tileDescription(
        tile: WeekTile,
        body: String,
    ): String = resources.getString(R.string.path_wrap_tile_a11y, tile(tile), body)

    /** The families in the server's lead order: "Mostly joy, then peace." */
    fun emotionsLead(families: List<String>): String =
        when (families.size) {
            0 -> ""
            1 -> resources.getString(R.string.path_wrap_lead_mostly, families[0].lowercase(locale))
            else -> resources.getString(R.string.path_wrap_lead_mostly_then, families[0].lowercase(locale), families[1].lowercase(locale))
        }

    /** The domains in the server's rank order: "Friends first, then identity and family." */
    fun domainsLead(domains: List<String>): String =
        when (domains.size) {
            0 -> ""
            1 -> resources.getString(R.string.path_wrap_domains_first, domains[0])
            else ->
                resources.getString(
                    R.string.path_wrap_domains_first_then,
                    domains[0],
                    and(domains.drop(1).take(2).map { it.lowercase(locale) }),
                )
        }

    /** "Six bright, four even, three heavy." */
    fun weightLead(
        bright: Int,
        even: Int,
        heavy: Int,
    ): String =
        WeekWordsBuilder.sentenceCase(resources.getString(R.string.path_wrap_weight_lead, words(bright), words(even), words(heavy)), locale)

    /** "From 08:02 to 22:30." over two local decimal hours. */
    fun hoursLead(
        from: Double,
        to: Double,
    ): String = resources.getString(R.string.path_wrap_hours_lead, time(from), time(to))

    fun hoursRange(
        from: Double,
        to: Double,
    ): String = resources.getString(R.string.path_wrap_hours_range, time(from), time(to))

    /** "Was someone there for the flag march, the tooth or the rent?", the long form inviting a soul. */
    fun invitation(
        moments: List<String>,
        long: Boolean,
    ): String {
        val ask = resources.getString(R.string.path_wrap_invite, or(moments))
        return if (long) ask + " " + resources.getString(R.string.path_wrap_invite_more) else ask
    }

    fun photos(n: Int): String = resources.getQuantityString(R.plurals.path_wrap_photos, n, words(n, NumberWords.Gender.FEMININE))

    fun cards(n: Int): String = resources.getQuantityString(R.plurals.path_wrap_cards, n, words(n, NumberWords.Gender.FEMININE))

    fun cardsShort(n: Int): String = resources.getQuantityString(R.plurals.path_wrap_cards_short, n, n)

    // MARK: - The month page

    /** "part the tenth" / "dixième partie": the month as a chapter of the year. */
    fun monthKicker(month: YearMonth): String =
        resources.getString(R.string.path_month_kicker, NumberWords.ordinal(month.monthValue, locale, NumberWords.Gender.FEMININE))

    fun monthName(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar {
            it.titlecase(locale)
        }

    private fun monthShort(month: YearMonth): String =
        month.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar {
            it.titlecase(locale)
        }

    fun personsInOrderLabel(): String = resources.getString(R.string.path_month_persons_label)

    /**
     * "Printed in joy, peace & pride, with anger, sadness & fear, on family, identity & friends." The
     * inks are the server's ranked families (three, then the rest), the papers its top domains.
     */
    fun colophon(
        inks: List<String>,
        papers: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        if (inks.isNotEmpty()) parts += resources.getString(R.string.path_month_printed, amp(inks.take(3)))
        if (inks.size > 3) parts += resources.getString(R.string.path_month_printed_with, amp(inks.drop(3)))
        if (papers.isNotEmpty()) parts += resources.getString(R.string.path_month_printed_on, amp(papers))
        return WeekWordsBuilder.sentenceCase(parts.joinToString(", ") + ".", locale)
    }

    fun weeksHeader(n: Int): String = resources.getQuantityString(R.plurals.path_month_weeks, n, words(n, NumberWords.Gender.FEMININE))

    fun weeksHint(month: YearMonth): String = resources.getString(R.string.path_month_by_week, monthName(month))

    fun milestonesHeader(n: Int): String = resources.getQuantityString(R.plurals.path_month_milestones, n, words(n))

    fun milestonesHint(): String = resources.getString(R.string.path_month_largest)

    fun clockwiseHint(): String = resources.getString(R.string.path_month_clockwise)

    /** The *more* link: "all six persons" when the list folds, "the persons, in detail" when it does not. */
    fun morePersons(total: Int): String =
        if (total >
            SHOWN
        ) {
            resources.getString(R.string.path_month_more_persons, words(total, NumberWords.Gender.FEMININE))
        } else {
            resources.getString(R.string.path_month_persons_detail)
        }

    fun moreDomains(total: Int): String =
        if (total >
            SHOWN
        ) {
            resources.getString(R.string.path_month_more_domains, words(total))
        } else {
            resources.getString(R.string.path_month_domains_detail)
        }

    fun moreDescription(link: String): String = resources.getString(R.string.path_month_more_a11y, link)

    fun newSince(previous: YearMonth): String = resources.getString(R.string.path_month_new_since, monthInSentence(previous))

    /** A month's name inside a sentence: "September" in English, "septembre" in French, as each language writes it. */
    private fun monthInSentence(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

    fun nobody(): String = resources.getString(R.string.path_month_nobody)

    /** "5–11 Oct": a week's days inside the month, from the server's 0-based indices. */
    fun range(
        month: YearMonth,
        from: Int,
        to: Int,
    ): String = resources.getString(R.string.path_month_range, from + 1, to + 1, monthShort(month))

    /** "Mon 5 Oct". */
    fun dateline(date: LocalDate): String =
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')} ${date.dayOfMonth} ${monthShort(YearMonth.from(date))}"

    fun viewCircle(): String = resources.getString(R.string.path_month_view_circle)

    fun viewCalendar(): String = resources.getString(R.string.path_month_view_calendar)

    /** "Mara five times, Nadia three. Inès and Jules are new since September." over the server's ranking. */
    fun personsLead(
        top: List<Pair<String, Int>>,
        fresh: List<String>,
        previous: YearMonth,
    ): String {
        val counts = top.take(2).map { (name, n) -> resources.getQuantityString(R.plurals.path_month_times, n, name, words(n)) }
        val first = if (counts.isEmpty()) "" else counts.joinToString(", ") + "."
        val second =
            if (fresh.isEmpty()) {
                ""
            } else {
                " " +
                    resources.getString(R.string.path_month_new_names, and(fresh), monthInSentence(previous))
            }
        return (first + second).trim()
    }

    fun emotionsLead(
        emotions: Int,
        families: Int,
    ): String =
        WeekWordsBuilder.sentenceCase(
            resources.getString(
                R.string.path_month_emotions_lead,
                words(emotions, NumberWords.Gender.FEMININE),
                words(families, NumberWords.Gender.FEMININE),
            ),
            locale,
        )

    fun domainsLead(
        domains: Int,
        top: String,
        topCount: Int,
    ): String =
        WeekWordsBuilder.sentenceCase(
            resources.getQuantityString(
                R.plurals.path_month_domains_lead,
                topCount,
                words(domains),
                top.lowercase(locale),
                words(topCount),
            ),
            locale,
        )

    private fun amp(items: List<String>): String =
        when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> resources.getString(R.string.path_month_amp, items.dropLast(1).joinToString(", "), items.last())
        }

    // MARK: - The insights

    fun insights(): String = resources.getString(R.string.path_insights)

    fun insightsHint(): String = resources.getString(R.string.path_insights_hint)

    fun toneTitle(): String = resources.getString(R.string.path_tone_title)

    fun toneHint(): String = resources.getString(R.string.path_tone_hint)

    /** "Bright outweighed heavy two to one; it opened heavy and closed bright." from the server's verdict. */
    fun toneLine(
        verdict: ToneVerdict,
        first: ValencePolarity?,
        last: ValencePolarity?,
    ): String {
        val lead =
            when (verdict) {
                ToneVerdict.BRIGHT_MORE_THAN_TWO_TO_ONE -> R.string.path_tone_bright_more_than_two_to_one
                ToneVerdict.BRIGHT_TWO_TO_ONE -> R.string.path_tone_bright_two_to_one
                ToneVerdict.BALANCE -> R.string.path_tone_balance
                ToneVerdict.HEAVY_OUTWEIGHED -> R.string.path_tone_heavy_outweighed
                ToneVerdict.NOTHING_HEAVY -> R.string.path_tone_nothing_heavy
                ToneVerdict.NOTHING_BRIGHT -> R.string.path_tone_nothing_bright
                ToneVerdict.ALL_EVEN -> R.string.path_tone_all_even
                ToneVerdict.UNKNOWN -> null
            }?.let(resources::getString)
        val arc =
            if (first != null &&
                last != null
            ) {
                resources.getString(R.string.path_tone_opened_closed, polarityWord(first), polarityWord(last))
            } else {
                null
            }
        return WeekWordsBuilder.sentenceCase(listOfNotNull(lead, arc).joinToString("; ") + ".", locale)
    }

    fun polarityWord(polarity: ValencePolarity): String =
        resources.getString(
            when (polarity) {
                ValencePolarity.HIGHLIGHT -> R.string.path_wrap_bright
                ValencePolarity.NEUTRAL -> R.string.path_wrap_even
                ValencePolarity.LOWLIGHT -> R.string.path_wrap_heavy
            },
        )

    fun hoursTitle(): String = resources.getString(R.string.path_hours_title)

    fun hoursHint(): String = resources.getString(R.string.path_hours_hint)

    fun hoursSub(): String = resources.getString(R.string.path_hours_sub)

    fun hoursOf(total: Int): String = resources.getQuantityString(R.plurals.path_hours_of, total, total)

    /** The busiest four hours: "20:00 – midnight". */
    fun hoursWindow(start: Int): String = resources.getString(R.string.path_hours_window, hourMark(start), hourMark(start + 4))

    fun hoursDescription(
        count: Int,
        total: Int,
        start: Int,
    ): String = resources.getQuantityString(R.plurals.path_hours_a11y, total, count, total, hourMark(start), hourMark(start + 4))

    private fun hourMark(hour: Int): String =
        if (hour % 24 ==
            0
        ) {
            resources.getString(R.string.path_hours_midnight)
        } else {
            time((hour % 24).toDouble())
        }

    // MARK: - The emotions gem

    fun gemHint(): String = resources.getString(R.string.path_gem_hint)

    /**
     * "Joy first, then peace; nothing in shame." over the server's lead: [order] by worth with the main
     * family first, [zero] the families with nothing; [level] when the first two weigh the same.
     */
    fun gemLead(
        order: List<String>,
        zero: List<String>,
        level: Boolean,
    ): String {
        if (order.isEmpty()) return ""
        val head =
            when {
                order.size == 1 -> resources.getString(R.string.path_gem_only, order[0])
                level -> resources.getString(R.string.path_gem_level, order[0], order[1])
                else -> resources.getString(R.string.path_gem_first_then, order[0], order[1])
            }
        val tail =
            when {
                zero.isEmpty() -> ""
                zero.size <= 2 -> resources.getString(R.string.path_gem_nothing_in, or(zero))
                else -> resources.getString(R.string.path_gem_nothing_in_others, words(zero.size, NumberWords.Gender.FEMININE))
            }
        return WeekWordsBuilder.sentenceCase("$head$tail.", locale)
    }

    fun gemPebbles(n: Int): String = resources.getQuantityString(R.plurals.path_gem_pebbles, n)

    fun moreEmotions(fine: Int): String =
        if (fine >
            3
        ) {
            resources.getString(R.string.path_gem_more, words(fine, NumberWords.Gender.FEMININE))
        } else {
            resources.getString(R.string.path_gem_detail)
        }

    /** The gem said in words, main family first: "The emotions as a gem: joy 40 %, peace 20 %…". */
    fun gemDescription(shares: List<Pair<String, Int>>): String =
        resources.getString(R.string.path_gem_a11y, shares.joinToString(", ") { (name, pct) -> "$name $pct %" })

    /** A local decimal hour as the locale writes a time: 21.5 → "9:30 PM" / "21:30". */
    fun time(hour: Double): String {
        val minutes = floor(hour * 60).toInt().coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    }

    private companion object {
        /** Rows a month section shows before its *more* link (spec §3.5: "shows 5, then more"). */
        const val SHOWN = 5
    }

    private fun words(
        n: Int,
        gender: NumberWords.Gender = NumberWords.Gender.MASCULINE,
    ): String = NumberWords.cardinal(n, locale, gender)

    /** "a", "a and b", "a, b and c". */
    private fun and(items: List<String>): String = joined(items, R.string.path_wrap_list_and)

    private fun or(items: List<String>): String = joined(items, R.string.path_wrap_list_or)

    private fun joined(
        items: List<String>,
        last: Int,
    ): String =
        when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> resources.getString(last, items.dropLast(1).joinToString(", "), items.last())
        }
}
