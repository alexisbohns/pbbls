package app.pbbls.android.core.model.wrap

/** An emotion family (category slug) in a week: its pebbles, its worth (intensity × charge), its share and its days. */
data class FamilyStat(
    val slug: String,
    val count: Int,
    val worth: Double,
    val share: Double,
    val days: DaySet,
)

internal fun FamilyDto.toModel(length: Int): FamilyStat = FamilyStat(slug, count, worth, share, days.toDaySet(length))
