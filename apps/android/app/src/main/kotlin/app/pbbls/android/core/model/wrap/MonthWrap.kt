package app.pbbls.android.core.model.wrap

import app.pbbls.android.core.model.ValencePolarity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * One month's page (#1207), decided by the server: [sections] are the thresholds' verdict in render
 * order, and a section's data is non-null exactly when the section is present ([cover] whenever the
 * month holds a pebble). Days are 0-based indices into the month.
 */
data class MonthWrap(
    val month: YearMonth,
    val daysInMonth: Int,
    val firstWeekday: DayOfWeek,
    val pebbles: Int,
    val people: Int,
    val sections: List<MonthSection>,
    val cover: MonthCover?,
    val weeks: List<MonthWeek>?,
    val milestones: List<String>?,
    val persons: List<PersonStat>?,
    val previousMonth: PreviousMonth?,
    val gem: Gem?,
    val fineEmotions: List<FineEmotionStat>?,
    val domains: List<DomainStat>?,
    val tone: Tone?,
    val hours: MonthHours?,
)

enum class MonthSection(
    val wire: String,
) {
    WEEKS("weeks"),
    MILESTONES("milestones"),
    PERSONS("persons"),
    GEM("gem"),
    DOMAINS("domains"),
    TONE("tone"),
    HOURS("hours"),
    ;

    companion object {
        fun fromWire(wire: String): MonthSection? = entries.firstOrNull { it.wire == wire }
    }
}

/** The garland's days (only those holding a pebble), the souls in order of appearance, and the colophon's inks and papers. */
data class MonthCover(
    val days: List<CoverDay>,
    val soulsInOrder: List<String>,
    val inks: List<String>,
    val papers: List<String>,
)

data class CoverDay(
    val day: Int,
    val pebbles: List<CoverPebble>,
)

data class CoverPebble(
    val momentId: String,
    val emotionId: String?,
    val intensity: Int,
    val polarity: ValencePolarity,
)

/** An ISO week of the month, its in-month range [from]..[to] (day indices), and its cairn. */
data class MonthWeek(
    val weekStart: LocalDate,
    val from: Int,
    val to: Int,
    val pebbles: Int,
    val soulIds: List<String>,
    val days: List<DayWeight>,
)

data class PreviousMonth(
    val pebbles: Int,
    val soulIds: List<String>,
)

/** The emotions gem: [facets] maps the 28 data facets to a family slug (null when uncoloured), already rotated. */
data class Gem(
    val worths: Map<String, Double>,
    val shares: Map<String, Double>,
    val main: String?,
    val rotation: Int,
    val facets: Map<String, String?>,
    val lead: Lead?,
)

data class FineEmotionStat(
    val family: String,
    val emotionId: String,
    val count: Int,
    val days: DaySet,
)

data class Tone(
    val days: List<ToneDay>,
    val verdict: ToneVerdict,
    val first: ValencePolarity?,
    val last: ValencePolarity?,
)

/** A day's summed intensities of bright ([up]), even and heavy ([down]) pebbles. */
data class ToneDay(
    val up: Int,
    val even: Int,
    val down: Int,
)

enum class ToneVerdict(
    val wire: String,
) {
    BRIGHT_MORE_THAN_TWO_TO_ONE("bright_more_than_two_to_one"),
    BRIGHT_TWO_TO_ONE("bright_two_to_one"),
    BALANCE("balance"),
    HEAVY_OUTWEIGHED("heavy_outweighed"),
    NOTHING_HEAVY("nothing_heavy"),
    NOTHING_BRIGHT("nothing_bright"),
    ALL_EVEN("all_even"),

    /** A verdict this client does not know yet; the lead sentence falls back to neutral wording. */
    UNKNOWN(""),
    ;

    companion object {
        fun fromWire(wire: String): ToneVerdict = entries.firstOrNull { it.wire == wire && it != UNKNOWN } ?: UNKNOWN
    }
}

data class MonthHours(
    val items: List<HourMark>,
    val busiest: BusiestWindow?,
)

/** The four-hour window starting at [start] o'clock that holds the most collections. */
data class BusiestWindow(
    val start: Int,
    val count: Int,
)

internal fun polarityOf(wire: Int): ValencePolarity =
    when {
        wire < 0 -> ValencePolarity.LOWLIGHT
        wire == 0 -> ValencePolarity.NEUTRAL
        else -> ValencePolarity.HIGHLIGHT
    }

/** Unknown sections are dropped and an unknown verdict maps to [ToneVerdict.UNKNOWN]. */
fun MonthWrapDto.toModel(): MonthWrap {
    val length = daysInMonth
    return MonthWrap(
        month = YearMonth.from(LocalDate.parse(month)),
        daysInMonth = daysInMonth,
        firstWeekday = DayOfWeek.of(firstWeekday.coerceIn(0, 6) + 1),
        pebbles = count,
        people = peopleCount,
        sections = sections.mapNotNull(MonthSection::fromWire),
        cover =
            cover?.let { c ->
                MonthCover(
                    days =
                        c.days.map { d ->
                            CoverDay(d.day, d.pebbles.map { CoverPebble(it.momentId, it.emotionId, it.intensity, polarityOf(it.polarity)) })
                        },
                    soulsInOrder = c.soulsInOrder,
                    inks = c.inks,
                    papers = c.papers,
                )
            },
        weeks = weeks?.map { MonthWeek(LocalDate.parse(it.weekStart), it.from, it.to, it.count, it.soulIds, it.days.toDayWeights()) },
        milestones = milestones,
        persons = persons?.map { it.toModel(length) },
        previousMonth = previousMonth?.let { PreviousMonth(it.count, it.soulIds) },
        gem = gem?.let { Gem(it.worths, it.shares, it.main, it.rotation, it.facets, it.lead?.toModel()) },
        fineEmotions = fineEmotions?.map { FineEmotionStat(it.family, it.emotionId, it.count, it.days.toDaySet(length)) },
        domains = domains?.map { it.toModel(length) },
        tone =
            tone?.let { t ->
                Tone(
                    days = t.days.map { ToneDay(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }, it.getOrElse(2) { 0 }) },
                    verdict = ToneVerdict.fromWire(t.verdict),
                    first = t.first?.let(::polarityOf),
                    last = t.last?.let(::polarityOf),
                )
            },
        hours = hours?.let { h -> MonthHours(h.items.map { it.toModel() }, h.busiest?.let { BusiestWindow(it.start, it.count) }) },
    )
}
