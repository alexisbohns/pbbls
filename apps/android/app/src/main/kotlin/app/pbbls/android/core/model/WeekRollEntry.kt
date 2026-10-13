package app.pbbls.android.core.model

import java.time.LocalDate

/**
 * One ISO week of the path — mirrors iOS `WeekRollEntry.swift`.
 *
 * @param weekStart the ISO Monday of the week, in the device zone.
 * @param moments pre-sorted by `WeekRollBuilder` (past weeks ascending,
 *   current/future descending by `happenedAt`). Uncompleted moments included.
 */
data class WeekRollEntry(
    val weekStart: LocalDate,
    val moments: List<PathMoment>,
)
