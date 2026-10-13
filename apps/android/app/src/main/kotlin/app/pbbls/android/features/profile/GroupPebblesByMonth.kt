package app.pbbls.android.features.profile

import app.pbbls.android.core.model.PathMoment
import java.time.YearMonth
import java.time.ZoneId

/**
 * Groups moments by calendar month in [zone], returning `(month, pebbles)`
 * pairs ordered descending by month — ports iOS `groupPebblesByMonth`.
 *
 * - Within a group, input order is preserved — callers typically pass pebbles
 *   already sorted descending by `happenedAt`.
 * - [zone] is injectable to keep tests deterministic; production callers pass
 *   `ZoneId.systemDefault()` (the iOS `Calendar.current` analog).
 */
fun groupPebblesByMonth(
    pebbles: List<PathMoment>,
    zone: ZoneId,
): List<Pair<YearMonth, List<PathMoment>>> {
    val buckets = LinkedHashMap<YearMonth, MutableList<PathMoment>>()
    for (pebble in pebbles) {
        val month = YearMonth.from(pebble.happenedAt.atZoneSameInstant(zone))
        buckets.getOrPut(month) { mutableListOf() }.add(pebble)
    }
    return buckets.entries
        .map { it.key to it.value.toList() }
        .sortedByDescending { it.first }
}
