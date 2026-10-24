package app.pbbls.android.core.model.wrap

import java.time.LocalDate

/**
 * One week's wrap (#1207), decided by the server: [tiles] are the thresholds' verdict in render order,
 * and a tile's data is non-null exactly when the tile is present.
 */
data class WeekWrap(
    val weekStart: LocalDate,
    val isoWeek: Int,
    val isoYear: Int,
    val live: Boolean,
    val pebbles: Int,
    val people: Int,
    val tiles: List<WeekTile>,
    val persons: List<PersonStat>?,
    /** The week's first moments, for the "was someone there for…" invitation; only when nobody is named. */
    val inviteMomentIds: List<String>?,
    val families: List<FamilyStat>?,
    val lead: Lead?,
    val domains: List<DomainStat>?,
    val weight: WeightGrid?,
    val hours: List<HourMark>?,
    val kept: Kept?,
)

enum class WeekTile(
    val wire: String,
) {
    CONTENTS("contents"),
    PERSONS("persons"),
    EMOTIONS("emotions"),
    DOMAINS("domains"),
    WEIGHT("weight"),
    HOURS("hours"),
    KEPT("kept"),
    ;

    companion object {
        fun fromWire(wire: String): WeekTile? = entries.firstOrNull { it.wire == wire }
    }
}

/** Pebble counts by intensity 1..3 (index 0..2), for bright, even and heavy pebbles. */
data class WeightGrid(
    val bright: List<Int>,
    val even: List<Int>,
    val heavy: List<Int>,
)

data class Kept(
    val photoMomentIds: List<String>,
    val cards: List<CardCount>,
)

data class CardCount(
    val momentId: String,
    val count: Int,
)

private const val DAYS_IN_WEEK = 7

/** Unknown tiles are dropped, so a server that adds a tile does not break older clients. */
fun WeekWrapDto.toModel(): WeekWrap =
    WeekWrap(
        weekStart = LocalDate.parse(weekStart),
        isoWeek = isoWeek,
        isoYear = isoYear,
        live = live,
        pebbles = count,
        people = peopleCount,
        tiles = tiles.mapNotNull(WeekTile::fromWire),
        persons = persons?.map { it.toModel(DAYS_IN_WEEK) },
        inviteMomentIds = inviteMomentIds,
        families = families?.map { it.toModel(DAYS_IN_WEEK) },
        lead = lead?.toModel(),
        domains = domains?.map { it.toModel(DAYS_IN_WEEK) },
        weight = weight?.let { WeightGrid(it.high, it.neutral, it.low) },
        hours = hours?.map { it.toModel() },
        kept = kept?.let { k -> Kept(k.photoMomentIds, k.cards.map { CardCount(it.momentId, it.count) }) },
    )
