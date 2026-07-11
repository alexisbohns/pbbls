package app.pbbls.android.features.path.models

import java.time.LocalDate

/**
 * One ISO week of the path — mirrors iOS `WeekRollEntry.swift`.
 *
 * @param weekStart the ISO Monday of the week, in the device zone.
 * @param pebbles pre-sorted by `WeekRollBuilder` (past weeks ascending,
 *   current/future descending by `happenedAt`).
 */
data class WeekRollEntry(
    val weekStart: LocalDate,
    val pebbles: List<Pebble>,
)
