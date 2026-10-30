package app.pbbls.android.features.achievements.copy

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import app.pbbls.android.R
import app.pbbls.android.core.common.NumberWords
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementMonth
import app.pbbls.android.core.model.achievements.AchievementYear
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.CountingSection
import app.pbbls.android.core.model.achievements.Coverage
import app.pbbls.android.core.model.achievements.DomainsSection
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.core.model.achievements.ExplorerSection
import app.pbbls.android.core.model.achievements.FirstStep
import app.pbbls.android.core.model.achievements.Needs
import app.pbbls.android.core.model.achievements.NextStep
import app.pbbls.android.core.model.achievements.StoneKind
import app.pbbls.android.features.achievements.figures.TWENTY_FIVE_SEATS
import app.pbbls.android.features.achievements.figures.YearDiscGeometry
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The achievements page's words (#1307, spec §4–§5): the one place the payload's numbers, tiers and
 * steps become sentences. The page's sentences are iOS's (`AchievementsCopy.swift`), word for word;
 * the sheets' are Android's first. Plain functions over [Resources], so the tests read the real
 * `strings.xml` in English and French. Choosing a sentence by how many stages stand is wording, not
 * counting: the stages are the ones the server lists.
 */
class AchievementsCopy(
    private val resources: Resources,
    private val locale: Locale,
) {
    private val numbers: NumberFormat = NumberFormat.getIntegerInstance(locale)

    // ---- The header -----------------------------------------------------------------------------

    /** "since Sunday, March 1, 2026": the header's day kept since (the server's `since`). */
    fun since(date: LocalDate): String = resources.getString(R.string.achievements_since, pattern("EEEEdMMMMyyyy").format(date))

    /** "days", or "day" on the first one. */
    fun daysUnit(days: Int): String = resources.getQuantityString(R.plurals.achievements_days_unit, days)

    /** "since March 2": a card's served `since` (the emotions' and each ladder's first day). */
    fun sinceHint(date: LocalDate): String = resources.getString(R.string.achievements_since, pattern("dMMMM").format(date))

    /** "1,500", "1 500": a number with the locale's grouping. */
    fun number(n: Int): String = numbers.format(n.toLong())

    // ---- The cards ------------------------------------------------------------------------------

    fun cardTitle(card: AchievementCard): String =
        resources.getString(
            when (card) {
                AchievementCard.EXPLORER -> R.string.achievements_card_explorer
                AchievementCard.EMOTIONS -> R.string.achievements_card_emotions
                AchievementCard.DOMAINS -> R.string.achievements_card_domains
                AchievementCard.PEBBLES -> R.string.achievements_card_pebbles
                AchievementCard.YEAR -> R.string.achievements_card_year
            },
        )

    fun explorerHint(): String = resources.getString(R.string.achievements_explorer_hint)

    fun domainsHint(): String = resources.getString(R.string.achievements_domains_hint)

    fun domainsRegister(): String = resources.getString(R.string.achievements_domains_register)

    /** "in progress" on the current year; nothing on a past one. */
    fun yearHint(current: Boolean): String? = if (current) resources.getString(R.string.achievements_year_in_progress) else null

    /** What the coverage tier means for the insights (D11); null for a tier this build does not know. */
    fun coverage(coverage: Coverage?): String? =
        coverage?.let {
            resources.getString(
                when (it) {
                    Coverage.NONE -> R.string.achievements_coverage_none
                    Coverage.FEW -> R.string.achievements_coverage_few
                    Coverage.THIRD -> R.string.achievements_coverage_third
                    Coverage.HALF -> R.string.achievements_coverage_half
                    Coverage.MOST -> R.string.achievements_coverage_most
                    Coverage.ALL -> R.string.achievements_coverage_all
                },
            )
        }

    /** "of 38", "sur 38": under the rosace's count. */
    fun rosaceOf(of: Int): String = resources.getString(R.string.achievements_rosace_of, number(of))

    // ---- Pebbles --------------------------------------------------------------------------------

    /** The card's line: "137 pebbles. Five stones stand; the sixth rises toward 250." */
    fun pebblesLine(stack: CountingSection): String =
        resources.getQuantityString(R.plurals.achievements_pebbles_count, stack.count, number(stack.count)) + " " + stones(stack)

    /** "137 pebbles kept.": the Pebbles sheet's lead. */
    fun pebblesKept(count: Int): String = resources.getQuantityString(R.plurals.achievements_pebbles_kept, count, number(count))

    /**
     * The skyline in words: how many of its ladder's steps the server lists as reached, and the one
     * rising toward the served `next`. Stones are feminine in French.
     */
    fun stones(stack: CountingSection): String {
        val next = stack.next ?: return resources.getString(R.string.achievements_stones_top)
        val target = number(next.threshold)
        val reached = stack.levels.map { it.threshold }.toSet()
        val standing = stack.ladder.count { it in reached }
        if (standing >= stack.ladder.size) return resources.getString(R.string.achievements_stones_all, target)
        val rising = NumberWords.ordinal(standing + 1, locale, NumberWords.Gender.FEMININE)
        return when (standing) {
            0 -> resources.getString(R.string.achievements_stones_none, target)
            1 -> resources.getString(R.string.achievements_stones_one, rising, target)
            else ->
                sentenceCase(
                    resources.getString(
                        R.string.achievements_stones_some,
                        NumberWords.cardinal(standing, locale, NumberWords.Gender.FEMININE),
                        rising,
                        target,
                    ),
                )
        }
    }

    fun ofWords(
        n: Int,
        of: Int,
    ): String = resources.getString(R.string.achievements_of_words, NumberWords.cardinal(n, locale), NumberWords.cardinal(of, locale))

    fun countOf(
        n: Int,
        of: Int,
    ): String = resources.getString(R.string.achievements_count_of, number(n), number(of))

    fun toGo(left: Int): String = resources.getQuantityString(R.plurals.achievements_to_go, left, number(left))

    fun thenEvery(step: Int): String = resources.getString(R.string.achievements_then_every, number(step))

    // ---- The year -------------------------------------------------------------------------------

    /**
     * The current month's next step (spec §4): "Twelve more days with a moment, one in each full
     * week, bring November to the inner disc." Out of reach this month, for whichever reason, the
     * sentence names neither (spec §3.3). Null for a need this build does not know.
     */
    fun nextStep(step: NextStep): String? {
        val needs = step.needs ?: return null
        val month = monthInSentence(step.month)
        val moments = needs == Needs.MOMENTS
        return when {
            !step.reachable ->
                sentenceCase(
                    resources.getString(
                        if (moments) R.string.achievements_next_moments_unreachable else R.string.achievements_next_pebbles_unreachable,
                        month,
                    ),
                )
            step.daysLeft <= 0 ->
                resources.getString(
                    if (moments) R.string.achievements_next_moments_keep else R.string.achievements_next_pebbles_keep,
                    month,
                )
            step.daysLeft == 1 ->
                resources.getString(if (moments) R.string.achievements_next_moments_one else R.string.achievements_next_pebbles_one, month)
            else ->
                sentenceCase(
                    resources.getString(
                        if (moments) R.string.achievements_next_moments_some else R.string.achievements_next_pebbles_some,
                        NumberWords.cardinal(step.daysLeft, locale),
                        month,
                    ),
                )
        }
    }

    /** "November" / "novembre": a month inside a sentence. */
    fun monthInSentence(month: Int): String = Month.of(month).getDisplayName(TextStyle.FULL, locale)

    /** "November" / "Novembre": a month heading a row. */
    fun monthHeading(month: Int): String = sentenceCase(Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, locale))

    // ---- The ceremony and the error --------------------------------------------------------------

    fun ceremonyTitle(stack: CompletionStack): String =
        resources.getString(
            when (stack) {
                CompletionStack.EMOTIONS -> R.string.achievements_ceremony_emotions_title
                CompletionStack.DOMAINS -> R.string.achievements_ceremony_domains_title
                CompletionStack.SIX_FIRSTS -> R.string.achievements_ceremony_six_firsts_title
            },
        )

    fun ceremonyBody(stack: CompletionStack): String =
        resources.getString(
            when (stack) {
                CompletionStack.EMOTIONS -> R.string.achievements_ceremony_emotions_body
                CompletionStack.DOMAINS -> R.string.achievements_ceremony_domains_body
                CompletionStack.SIX_FIRSTS -> R.string.achievements_ceremony_six_firsts_body
            },
        )

    fun errorTitle(): String = resources.getString(R.string.achievements_error_title)

    fun errorBody(): String = resources.getString(R.string.achievements_error_body)

    fun retry(): String = resources.getString(R.string.achievements_error_retry)

    // ---- The sheets -----------------------------------------------------------------------------

    /** The register's date: "Apr 21" / "21 avr.". */
    fun shortDate(date: LocalDate): String = pattern("dMMM").format(date)

    /** A first-five task's title; one this build does not know reads as "A first". */
    fun firstFiveTitle(step: FirstStep?): String =
        resources.getString(
            when (step) {
                FirstStep.MOMENT -> R.string.achievement_family_first_moment_title
                FirstStep.PEBBLE -> R.string.achievement_family_first_pebble_title
                FirstStep.COLLECTION -> R.string.achievement_family_first_collection_title
                FirstStep.SOUL -> R.string.achievement_family_first_soul_title
                FirstStep.GLYPH -> R.string.achievement_family_first_glyph_title
                null -> R.string.achievements_unknown_first
            },
        )

    /** The app's own words for the six firsts: Small · Medium · Large · Lowlight · Neutral · Highlight. */
    fun sixFirstName(kind: StoneKind?): String =
        resources.getString(
            when (kind) {
                StoneKind.SMALL -> R.string.achievements_kind_small
                StoneKind.MEDIUM -> R.string.achievements_kind_medium
                StoneKind.LARGE -> R.string.achievements_kind_large
                StoneKind.LOWLIGHT -> R.string.valence_lowlight
                StoneKind.NEUTRAL -> R.string.valence_neutral
                StoneKind.HIGHLIGHT -> R.string.valence_highlight
                null -> R.string.achievements_unknown_first
            },
        )

    /** A moment's name in a row, or "Untitled" for one the server sends without a name. */
    fun momentName(name: String?): String = name ?: resources.getString(R.string.moment_untitled)

    fun string(id: Int): String = resources.getString(id)

    fun registerNumber(n: Int): String = String.format(locale, resources.getString(R.string.achievements_register_number), n)

    fun firstYear(year: Int): String = resources.getString(R.string.achievements_first_year, year)

    // ---- Read aloud (spec §5) -------------------------------------------------------------------

    fun explorerDescription(
        explorer: ExplorerSection,
        emotions: EmotionsSection?,
        domains: DomainsSection?,
    ): String =
        resources.getString(
            R.string.achievements_explorer_a11y,
            countOf(explorer.firstFive.count { it.done }, explorer.firstFive.size),
            countOf(explorer.sixFirsts.count { it.done }, explorer.sixFirsts.size),
            countOf(explorer.twentyFive?.count ?: 0, TWENTY_FIVE_SEATS),
            countOf(domains?.count ?: 0, domains?.of ?: 0),
            countOf(emotions?.count ?: 0, emotions?.of ?: 0),
        )

    fun emotionsDescription(emotions: EmotionsSection): String =
        resources.getString(R.string.achievements_emotions_a11y, countOf(emotions.count, emotions.of))

    fun domainsDescription(domains: DomainsSection): String =
        resources
            .getString(
                R.string.achievements_domains_a11y,
                countOf(domains.count, domains.of),
                coverage(domains.coverage).orEmpty(),
            ).trim()

    fun pebblesDescription(
        pebbles: CountingSection?,
        carved: CountingSection?,
        sales: CountingSection?,
    ): String =
        resources.getString(
            R.string.achievements_pebbles_a11y,
            number(pebbles?.count ?: 0),
            number(carved?.count ?: 0),
            number(sales?.count ?: 0),
        )

    /** "2026. On the rim: August. On the inner circle: March, April. On their way: November." */
    fun discDescription(year: AchievementYear): String {
        fun list(months: List<Int>) =
            if (months.isEmpty()) resources.getString(R.string.achievements_none) else months.joinToString(", ") { monthHeading(it) }
        return resources.getString(
            R.string.achievements_disc_a11y,
            year.year,
            list(YearDiscGeometry.onRim(year.months)),
            list(YearDiscGeometry.onInner(year.months)),
            list(YearDiscGeometry.onTheirWay(year.months)),
        )
    }

    /**
     * A table row read aloud: the served counts as they are, never against a target, each marked
     * "reached" where the server's `done` flag says so (the done style the table draws).
     */
    fun monthDescription(month: AchievementMonth): String {
        fun reached(
            text: String,
            done: Boolean,
        ) = if (done) resources.getString(R.string.achievements_reached, text) else text
        val weeks =
            if (month.fullWeeks > 0) {
                resources.getString(R.string.achievements_weeks_filled, number(month.weeksFilled), number(month.fullWeeks))
            } else {
                resources.getString(R.string.achievements_no_full_week)
            }
        return resources.getString(
            R.string.achievements_month_a11y,
            monthHeading(month.month),
            reached(number(month.momentDays), month.done.moments),
            reached(number(month.pebbleDays), month.done.pebbles),
            reached(weeks, month.done.weeks),
        )
    }

    /** The quiet names read as one: "Not yet: Anxiety, Shame". */
    fun notYetDescription(names: List<String>): String = resources.getString(R.string.achievements_not_yet_a11y, names.joinToString(", "))

    /** A ready mark read aloud, by the ceremony it opens: "The six firsts: complete. Open the ceremony." */
    fun readyDescription(stack: CompletionStack): String = resources.getString(R.string.achievements_ready_a11y, ceremonyTitle(stack))

    /** A row read aloud: its parts, then "new since you last looked" when it is. */
    fun row(
        vararg parts: String?,
        isNew: Boolean = false,
    ): String =
        (
            parts.filterNotNull().filter { it.isNotBlank() } +
                listOfNotNull(if (isNew) resources.getString(R.string.achievements_new_a11y) else null)
        ).joinToString(", ")

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private fun sentenceCase(s: String): String = s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

@Composable
fun rememberAchievementsCopy(): AchievementsCopy {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(resources, locale) { AchievementsCopy(resources, locale) }
}
