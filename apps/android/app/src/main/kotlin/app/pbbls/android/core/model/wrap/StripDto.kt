package app.pbbls.android.core.model.wrap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `path_strip(p_tz)` (#1207), as the server sends it. Mapped to [Strip] by [toModel]. */
@Serializable
data class StripDto(
    val items: List<StripItemDto> = emptyList(),
)

/** A week (`kind = "week"`) or a month (`kind = "month"`); the other kind's keys are absent. */
@Serializable
data class StripItemDto(
    val kind: String = "",
    @SerialName("week_start")
    val weekStart: String? = null,
    @SerialName("iso_week")
    val isoWeek: Int = 0,
    @SerialName("iso_year")
    val isoYear: Int = 0,
    val count: Int = 0,
    val focusable: Boolean = false,
    val live: Boolean = false,
    /** Seven `[moments, strongest intensity]` pairs, Monday first. */
    val days: List<List<Int>> = emptyList(),
    val month: String? = null,
)
