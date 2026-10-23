package app.pbbls.android.features.path.wrap.copy

import android.content.res.Resources
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.model.wrap.WeekTile
import app.pbbls.android.features.path.spine.logic.WeekWordsBuilder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

    /** A local decimal hour as the locale writes a time: 21.5 → "9:30 PM" / "21:30". */
    fun time(hour: Double): String {
        val minutes = floor(hour * 60).toInt().coerceIn(0, 24 * 60 - 1)
        return LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
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
