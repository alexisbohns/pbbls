package app.pbbls.android.core.model.wrap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `path_month_wrap(p_month, p_tz)` (#1207), as the server sends it. An absent section's keys are
 * omitted by the server, so each section defaults to null here. Mapped to [MonthWrap] by [toModel].
 */
@Serializable
data class MonthWrapDto(
    val month: String = "",
    @SerialName("days_in_month")
    val daysInMonth: Int = 0,
    @SerialName("first_weekday")
    val firstWeekday: Int = 0,
    val count: Int = 0,
    @SerialName("people_count")
    val peopleCount: Int = 0,
    val sections: List<String> = emptyList(),
    val cover: CoverDto? = null,
    val weeks: List<MonthWeekDto>? = null,
    val milestones: List<String>? = null,
    val persons: List<PersonDto>? = null,
    @SerialName("previous_month")
    val previousMonth: PreviousMonthDto? = null,
    val gem: GemDto? = null,
    @SerialName("fine_emotions")
    val fineEmotions: List<FineEmotionDto>? = null,
    val domains: List<DomainDto>? = null,
    val tone: ToneDto? = null,
    val hours: MonthHoursDto? = null,
)

@Serializable
data class CoverDto(
    val days: List<CoverDayDto> = emptyList(),
    @SerialName("souls_in_order")
    val soulsInOrder: List<String> = emptyList(),
    val inks: List<String> = emptyList(),
    val papers: List<String> = emptyList(),
)

@Serializable
data class CoverDayDto(
    val day: Int = 0,
    val pebbles: List<CoverPebbleDto> = emptyList(),
)

@Serializable
data class CoverPebbleDto(
    @SerialName("moment_id")
    val momentId: String = "",
    @SerialName("emotion_id")
    val emotionId: String? = null,
    val intensity: Int = 0,
    val polarity: Int = 0,
)

@Serializable
data class MonthWeekDto(
    @SerialName("week_start")
    val weekStart: String = "",
    val from: Int = 0,
    val to: Int = 0,
    val count: Int = 0,
    @SerialName("soul_ids")
    val soulIds: List<String> = emptyList(),
    val days: List<List<Int>> = emptyList(),
)

@Serializable
data class PreviousMonthDto(
    val count: Int = 0,
    @SerialName("soul_ids")
    val soulIds: List<String> = emptyList(),
)

/** `facets` maps all 28 data facets to a family slug, or null for an uncoloured facet. */
@Serializable
data class GemDto(
    val worths: Map<String, Double> = emptyMap(),
    val shares: Map<String, Double> = emptyMap(),
    val main: String? = null,
    val rotation: Int = 0,
    val facets: Map<String, String?> = emptyMap(),
    val lead: LeadDto? = null,
)

@Serializable
data class FineEmotionDto(
    val family: String = "",
    @SerialName("emotion_id")
    val emotionId: String = "",
    val count: Int = 0,
    val days: List<Int> = emptyList(),
)

/** `days[i]` is `[up, even, down]`: the day's summed intensities of bright, even and heavy pebbles. */
@Serializable
data class ToneDto(
    val days: List<List<Int>> = emptyList(),
    val verdict: String = "",
    val first: Int? = null,
    val last: Int? = null,
)

@Serializable
data class MonthHoursDto(
    val items: List<HourDto> = emptyList(),
    val busiest: BusiestDto? = null,
)

@Serializable
data class BusiestDto(
    val start: Int = 0,
    val count: Int = 0,
)
