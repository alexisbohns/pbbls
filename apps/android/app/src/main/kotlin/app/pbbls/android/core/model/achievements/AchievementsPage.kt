package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import java.time.Instant
import java.time.LocalDate

/**
 * The achievements page as Android draws it (#1307, spec §3.3). Every number, order, level, family
 * and flag is the server's. A null section hides its card (spec §4, resilience). Display models
 * only: nothing here is computed from counts.
 *
 * Tolerance, the same on every surface: an item whose enum this client does not know, or whose
 * field is missing or unreadable, is kept with that field null and drawn as a ghost or a fallback,
 * never dropped, so no count, seat or position shifts. A null element inside a list or a map is
 * dropped on its own. A year without exactly twelve months is dropped (the disc has twelve spokes).
 */
data class AchievementsPage(
    val since: LocalDate?,
    val daysKept: Int?,
    val explorer: ExplorerSection?,
    val emotions: EmotionsSection?,
    val domains: DomainsSection?,
    val pebbles: CountingSection?,
    val glyphsCarved: CountingSection?,
    val glyphSales: CountingSection?,
    val years: List<AchievementYear>,
    /** When each card's sheet was last opened (D10); a card never opened is absent. */
    val seen: Map<AchievementCard, Instant>,
    /** The owner's Profile shelf (D18): newest first, private families already removed. */
    val shelf: List<ShelfItem>,
    val shelfCount: Int,
)

/**
 * The crossing pebble's snapshot (D15): enough to draw its seal and open its moment. [momentId]
 * null means there is nothing to open; [happenedAt] null, nothing to date it by.
 */
data class PebbleSnapshot(
    val momentId: String?,
    /** Absent for an unnamed moment. */
    val name: String?,
    val happenedAt: Instant?,
    val emotionId: String?,
    val valence: Valence,
)

/** The first five, in the Home checklist's order. */
enum class FirstStep(
    val slug: String,
) {
    MOMENT("first-moment"),
    PEBBLE("first-pebble"),
    COLLECTION("first-collection"),
    SOUL("first-soul"),
    GLYPH("first-glyph"),
    ;

    companion object {
        fun of(slug: String?): FirstStep? = entries.firstOrNull { it.slug == slug }
    }
}

enum class SubjectKind(
    val key: String,
) {
    MOMENT("moment"),
    PEBBLE("pebble"),
    COLLECTION("collection"),
    SOUL("soul"),
    GLYPH("glyph"),
    ;

    companion object {
        fun of(key: String?): SubjectKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a first-five task made. For a moment or a pebble, [id] is the moment's id. [kind] null is a
 * kind this client does not know; [id] null, nothing to open.
 */
data class FirstSubject(
    val kind: SubjectKind?,
    val id: String?,
    val name: String?,
)

/** [on] is the day its subject was made. [step] null is a task this client does not know: a ghost. */
data class FirstFiveItem(
    val step: FirstStep?,
    val done: Boolean,
    val on: LocalDate?,
    val subject: FirstSubject?,
    val isNew: Boolean,
)

/** The six firsts (D5), in the catalog's order: three intensities, then three valences. */
enum class StoneKind(
    val slug: String,
) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
    LOWLIGHT("lowlight"),
    NEUTRAL("neutral"),
    HIGHLIGHT("highlight"),
    ;

    companion object {
        fun of(slug: String?): StoneKind? = entries.firstOrNull { it.slug == slug }
    }
}

/**
 * Every dated row below carries [on] by one rule: the local day its pebble happened, or the day it
 * was recorded when that pebble is gone. [kind] null is a first this client does not know: a ghost.
 */
data class SixFirst(
    val kind: StoneKind?,
    val done: Boolean,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/** The 25th pebble. [count] is capped at 25 by the server. */
data class TwentyFive(
    val count: Int,
    val done: Boolean,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/** The wheel's five rings, from the centre out (D6). */
enum class ExplorerRing(
    val slug: String,
) {
    FIRST_FIVE("first_five"),
    SIX_FIRSTS("six_firsts"),
    TWENTY_FIVE("twenty_five"),
    DOMAINS("domains"),
    EMOTIONS("emotions"),
    ;

    companion object {
        fun of(slug: String?): ExplorerRing? = entries.firstOrNull { it.slug == slug }
    }
}

/**
 * A set made whole (D9). [ready] is the server's clock, so no client compares times. [pulledAt]
 * set means the ceremony was pulled, from this device or another, or by a pull whose answer was
 * lost. [pebble] is the stone that made the set whole. A time the client cannot read is null.
 */
data class Completion(
    val completedAt: Instant?,
    val offeredFrom: Instant?,
    val ready: Boolean,
    val pulledAt: Instant?,
    val pebble: PebbleSnapshot?,
)

data class ExplorerSection(
    val firstPebble: PebbleSnapshot?,
    val firstFive: List<FirstFiveItem>,
    val sixFirsts: List<SixFirst>,
    val twentyFive: TwentyFive?,
    val ringsDone: Set<ExplorerRing>,
    val completion: Completion?,
)

/**
 * One of the rosace's seats: [family] is a category slug, which may be one Android does not know
 * (drawn in ink). [emotionId] null keeps the seat in its place, drawn as a ghost.
 */
data class EmotionSeat(
    val seat: Int,
    val emotionId: String?,
    val family: String?,
    val met: Boolean,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/**
 * One family of the Emotions sheet, in the server's order (wheel order): [met] by the day met,
 * [notYet] by seat. The seats are the section's own, looked up by their index. [family] null is a
 * group the server did not name, drawn in ink.
 */
data class EmotionsFamilyGroup(
    val family: String?,
    val met: List<EmotionSeat>,
    val notYet: List<EmotionSeat>,
)

/** [seats] are in the server's order (wheel order, then slug); each carries its own index. */
data class EmotionsSection(
    val count: Int,
    val of: Int,
    val seats: List<EmotionSeat>,
    val families: List<EmotionsFamilyGroup>,
    /** The card's "since": the day the first emotion was met. */
    val since: LocalDate?,
    val completion: Completion?,
)

/** D11's tiers; an unknown tier decodes to null and the card shows no sentence. */
enum class Coverage(
    val slug: String,
) {
    NONE("none"),
    FEW("few"),
    THIRD("third"),
    HALF("half"),
    MOST("most"),
    ALL("all"),
    ;

    companion object {
        fun of(slug: String?): Coverage? = entries.firstOrNull { it.slug == slug }
    }
}

/** [domainId] null keeps the entry in the band, drawn as a ghost. */
data class DomainEntry(
    val domainId: String?,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

/**
 * [entered] is newest first, as the server sends it. The band's arrival order is that list
 * reversed, never a sort by date.
 */
data class DomainsSection(
    val count: Int,
    val of: Int,
    val coverage: Coverage?,
    val entered: List<DomainEntry>,
    val notYet: List<String>,
    val completion: Completion?,
)

data class StackLevel(
    val threshold: Int,
    val on: LocalDate?,
    val pebble: PebbleSnapshot?,
    val isNew: Boolean,
)

data class NextLevel(
    val threshold: Int,
    val left: Int,
    val progress: Float,
)

/** Pebbles, glyphs carved or glyph sales. [next] is null at the top of the ladder. */
data class CountingSection(
    val count: Int,
    val levels: List<StackLevel>,
    val next: NextLevel?,
    val ladder: List<Int>,
    val thenEvery: Int?,
    /** The card's "since": the day of its first level. */
    val since: LocalDate?,
)

/** The month's state; [AchievementMonth.state] is null for one this client does not know. */
enum class MonthState(
    val slug: String,
) {
    UPCOMING("upcoming"),
    QUIET("quiet"),
    KEPT("kept"),
    ;

    companion object {
        fun of(slug: String?): MonthState? = entries.firstOrNull { it.slug == slug }
    }
}

/** Which of a month's counters reached their target: the done style, decided by the server. */
data class MonthDone(
    val moments: Boolean,
    val pebbles: Boolean,
    val weeks: Boolean,
)

/**
 * One month of a year disc (D12). [level] is 0..2 and [progress] 0..1, clamped on decode. [state]
 * null is a state this client does not know, drawn as [MonthState.QUIET] (spec §4).
 */
data class AchievementMonth(
    val month: Int,
    val state: MonthState?,
    val family: String?,
    val momentDays: Int,
    val pebbleDays: Int,
    val fullWeeks: Int,
    val weeksFilled: Int,
    val level: Int,
    val progress: Float,
    val done: MonthDone,
)

enum class Needs(
    val slug: String,
) {
    MOMENTS("moments"),
    PEBBLES("pebbles"),
    ;

    companion object {
        fun of(slug: String?): Needs? = entries.firstOrNull { it.slug == slug }
    }
}

/** [needs] null is a need this client does not know: the step is kept, without its sentence. */
data class NextStep(
    val month: Int,
    val needs: Needs?,
    val daysLeft: Int,
    val reachable: Boolean,
)

/**
 * One disc. [months] are always January to December; [listed] is the table, newest first.
 * [daysTarget] is the day count a level asks for (the table's "n/14"); null if the server stops
 * sending it, and then the table shows the count alone.
 */
data class AchievementYear(
    val year: Int,
    val current: Boolean,
    val daysTarget: Int?,
    val months: List<AchievementMonth>,
    val listed: List<Int>,
    val nextStep: NextStep?,
)

/**
 * One row of the Profile shelf (D18). [threshold] is null for a family that counts nothing; a
 * missing [slug] or [family] is null too, and the row draws as a ghost.
 */
data class ShelfItem(
    val slug: String?,
    val family: String?,
    val threshold: Int?,
    val unlockedOn: LocalDate?,
)

/** The cards a sheet marks seen (`mark_achievements_seen`'s `p_card`, and the keys of `seen`). */
enum class AchievementCard(
    val slug: String,
) {
    EXPLORER("explorer"),
    EMOTIONS("emotions"),
    DOMAINS("domains"),
    PEBBLES("pebbles"),
    YEAR("year"),
    ;

    companion object {
        fun of(slug: String?): AchievementCard? = entries.firstOrNull { it.slug == slug }
    }
}

/** A set whose completion is pulled (`pull_ceremony`'s `p_stack`), with its `set_complete` slug. */
enum class CompletionStack(
    val slug: String,
    val catalogSlug: String,
) {
    EMOTIONS("emotions", "complete-emotions"),
    DOMAINS("domains", "complete-domains"),
    SIX_FIRSTS("six_firsts", "complete-six-firsts"),
}
