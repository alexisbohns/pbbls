package app.pbbls.android.core.model.wrap

/** A domain in a week or a month: its pebbles and its days. */
data class DomainStat(
    val domainId: String,
    val count: Int,
    val days: DaySet,
)

internal fun DomainDto.toModel(length: Int): DomainStat = DomainStat(domainId, count, days.toDaySet(length))
