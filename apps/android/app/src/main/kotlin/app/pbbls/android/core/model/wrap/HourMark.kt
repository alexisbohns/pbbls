package app.pbbls.android.core.model.wrap

/** A pebble on the hours: when it happened and when it was collected, in local decimal hours (0 until 24). */
data class HourMark(
    val momentId: String,
    val happened: Double,
    val collected: Double,
)

internal fun HourDto.toModel(): HourMark = HourMark(momentId, happened, collected)
