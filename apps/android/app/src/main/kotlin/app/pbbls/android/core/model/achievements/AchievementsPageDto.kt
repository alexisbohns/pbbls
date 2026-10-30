package app.pbbls.android.core.model.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `get_achievements(p_tz)` as the server sends it (#1305, spec §3.3). Every field has a default:
 * an absent section or optional field omits its key (D17), and an explicit null is coerced to the
 * default. A null inside a list or a map is not a field, so nothing coerces it: every element type
 * is nullable, and the mapping drops the nulls. [toModel] maps it to [AchievementsPage] and drops what cannot be drawn.
 */
@Serializable
data class AchievementsPageDto(
    val since: String? = null,
    @SerialName("days_kept")
    val daysKept: Int? = null,
    val explorer: ExplorerDto? = null,
    val emotions: EmotionsDto? = null,
    val domains: DomainsDto? = null,
    val pebbles: CountingDto? = null,
    @SerialName("glyphs_carved")
    val glyphsCarved: CountingDto? = null,
    @SerialName("glyph_sales")
    val glyphSales: CountingDto? = null,
    val years: List<YearDto> = emptyList(),
    /** Card slug → when its sheet was last opened, whole-second UTC (D10). */
    val seen: Map<String, String?> = emptyMap(),
    /** The owner's Profile shelf, private families already removed (D18). */
    val shelf: List<ShelfRowDto> = emptyList(),
    @SerialName("shelf_count")
    val shelfCount: Int = 0,
)

/** `{pebble}`: the crossing pebble's display snapshot (D15). An unnamed moment omits `name`. */
@Serializable
data class PebbleSnapshotDto(
    @SerialName("moment_id")
    val momentId: String? = null,
    val name: String? = null,
    @SerialName("happened_at")
    val happenedAt: String? = null,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val intensity: Int? = null,
    val positiveness: Int? = null,
)

@Serializable
data class SubjectDto(
    val kind: String? = null,
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class FirstFiveDto(
    val slug: String? = null,
    val done: Boolean = false,
    val on: String? = null,
    val subject: SubjectDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class SixFirstDto(
    val kind: String? = null,
    val done: Boolean = false,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class TwentyFiveDto(
    val count: Int = 0,
    val done: Boolean = false,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class CompletionDto(
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("offered_from")
    val offeredFrom: String? = null,
    val ready: Boolean = false,
    @SerialName("pulled_at")
    val pulledAt: String? = null,
    /** The stone that made the set whole. */
    val pebble: PebbleSnapshotDto? = null,
)

@Serializable
data class ExplorerDto(
    @SerialName("first_pebble")
    val firstPebble: PebbleSnapshotDto? = null,
    @SerialName("first_five")
    val firstFive: List<FirstFiveDto> = emptyList(),
    @SerialName("six_firsts")
    val sixFirsts: List<SixFirstDto> = emptyList(),
    @SerialName("twenty_five")
    val twentyFive: TwentyFiveDto? = null,
    @SerialName("rings_done")
    val ringsDone: List<String?> = emptyList(),
    val completion: CompletionDto? = null,
)

@Serializable
data class SeatDto(
    val seat: Int = 0,
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val family: String? = null,
    val met: Boolean = false,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

/** One family of the Emotions sheet: its met seats by the day met, then the rest by seat. */
@Serializable
data class EmotionFamilyDto(
    val family: String? = null,
    val met: List<Int?> = emptyList(),
    @SerialName("not_yet")
    val notYet: List<Int?> = emptyList(),
)

@Serializable
data class EmotionsDto(
    val count: Int = 0,
    val of: Int = 0,
    val seats: List<SeatDto> = emptyList(),
    val families: List<EmotionFamilyDto> = emptyList(),
    val since: String? = null,
    val completion: CompletionDto? = null,
)

@Serializable
data class EnteredDto(
    @SerialName("domain_id")
    val domainId: String? = null,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class DomainsDto(
    val count: Int = 0,
    val of: Int = 0,
    val coverage: String? = null,
    val entered: List<EnteredDto> = emptyList(),
    @SerialName("not_yet")
    val notYet: List<String?> = emptyList(),
    val completion: CompletionDto? = null,
)

@Serializable
data class LevelDto(
    val threshold: Int = 0,
    val on: String? = null,
    val pebble: PebbleSnapshotDto? = null,
    @SerialName("new")
    val isNew: Boolean = false,
)

@Serializable
data class NextLevelDto(
    val threshold: Int = 0,
    val left: Int = 0,
    val progress: Double = 0.0,
)

/** One counting stack: pebbles, glyphs carved or glyph sales (D3). */
@Serializable
data class CountingDto(
    val count: Int = 0,
    val levels: List<LevelDto> = emptyList(),
    val next: NextLevelDto? = null,
    val ladder: List<Int?> = emptyList(),
    @SerialName("then_every")
    val thenEvery: Int? = null,
    val since: String? = null,
)

/** Which of a month's counters reached their target: the done style is the server's call. */
@Serializable
data class MonthDoneDto(
    val moments: Boolean = false,
    val pebbles: Boolean = false,
    val weeks: Boolean = false,
)

@Serializable
data class MonthDto(
    val month: Int = 0,
    val state: String? = null,
    val family: String? = null,
    @SerialName("moment_days")
    val momentDays: Int = 0,
    @SerialName("pebble_days")
    val pebbleDays: Int = 0,
    @SerialName("full_weeks")
    val fullWeeks: Int = 0,
    @SerialName("weeks_filled")
    val weeksFilled: Int = 0,
    val level: Int = 0,
    val progress: Double = 0.0,
    val done: MonthDoneDto = MonthDoneDto(),
)

@Serializable
data class NextStepDto(
    val month: Int = 0,
    val needs: String? = null,
    @SerialName("days_left")
    val daysLeft: Int = 0,
    val reachable: Boolean = false,
)

@Serializable
data class YearDto(
    val year: Int = 0,
    val current: Boolean = false,
    @SerialName("days_target")
    val daysTarget: Int? = null,
    val months: List<MonthDto> = emptyList(),
    val listed: List<Int?> = emptyList(),
    @SerialName("next_step")
    val nextStep: NextStepDto? = null,
)

/**
 * One Profile shelf row (D18). [threshold] is an explicit null for the families that count
 * nothing, the same row `get_public_profile`'s shelf serves.
 */
@Serializable
data class ShelfRowDto(
    val slug: String? = null,
    val family: String? = null,
    val threshold: Int? = null,
    @SerialName("unlocked_on")
    val unlockedOn: String? = null,
)
