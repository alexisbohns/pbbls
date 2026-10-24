package app.pbbls.android.core.model.wrap

/** A soul in a week or a month: its pebbles, its days and, in a month, whether it is new since the previous one. */
data class PersonStat(
    val soulId: String,
    val count: Int,
    val days: DaySet,
    val newSincePrevious: Boolean,
)

internal fun PersonDto.toModel(length: Int): PersonStat = PersonStat(soulId, count, days.toDaySet(length), newSincePrevious)
