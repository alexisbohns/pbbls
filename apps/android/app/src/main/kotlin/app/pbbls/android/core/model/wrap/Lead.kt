package app.pbbls.android.core.model.wrap

/** The families in the order a lead sentence names them, and the families with nothing. Slugs only; clients word it. */
data class Lead(
    val order: List<String>,
    val zero: List<String>,
)

internal fun LeadDto.toModel(): Lead = Lead(order, zero)
