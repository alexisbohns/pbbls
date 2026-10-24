package app.pbbls.android.core.model.wrap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `path_week_wrap(p_week_start, p_tz)` (#1207), as the server sends it. An absent tile's key is
 * omitted by the server, so each tile's data defaults to null here. Mapped to [WeekWrap] by [toModel].
 */
@Serializable
data class WeekWrapDto(
    @SerialName("week_start")
    val weekStart: String = "",
    @SerialName("iso_week")
    val isoWeek: Int = 0,
    @SerialName("iso_year")
    val isoYear: Int = 0,
    val live: Boolean = false,
    val count: Int = 0,
    @SerialName("people_count")
    val peopleCount: Int = 0,
    val tiles: List<String> = emptyList(),
    val persons: List<PersonDto>? = null,
    @SerialName("invite_moment_ids")
    val inviteMomentIds: List<String>? = null,
    val families: List<FamilyDto>? = null,
    val lead: LeadDto? = null,
    val domains: List<DomainDto>? = null,
    val weight: WeightDto? = null,
    val hours: List<HourDto>? = null,
    val kept: KeptDto? = null,
)

@Serializable
data class PersonDto(
    @SerialName("soul_id")
    val soulId: String = "",
    val count: Int = 0,
    val days: List<Int> = emptyList(),
    @SerialName("new_since_previous")
    val newSincePrevious: Boolean = false,
)

@Serializable
data class FamilyDto(
    val slug: String = "",
    val count: Int = 0,
    val worth: Double = 0.0,
    val share: Double = 0.0,
    val days: List<Int> = emptyList(),
)

@Serializable
data class LeadDto(
    val order: List<String> = emptyList(),
    val zero: List<String> = emptyList(),
)

@Serializable
data class DomainDto(
    @SerialName("domain_id")
    val domainId: String = "",
    val count: Int = 0,
    val days: List<Int> = emptyList(),
)

/** Pebble counts by intensity 1..3, per valence. */
@Serializable
data class WeightDto(
    val high: List<Int> = emptyList(),
    val neutral: List<Int> = emptyList(),
    val low: List<Int> = emptyList(),
)

@Serializable
data class HourDto(
    @SerialName("moment_id")
    val momentId: String = "",
    val happened: Double = 0.0,
    val collected: Double = 0.0,
)

@Serializable
data class KeptDto(
    @SerialName("photo_moment_ids")
    val photoMomentIds: List<String> = emptyList(),
    val cards: List<CardCountDto> = emptyList(),
)

@Serializable
data class CardCountDto(
    @SerialName("moment_id")
    val momentId: String = "",
    val count: Int = 0,
)
