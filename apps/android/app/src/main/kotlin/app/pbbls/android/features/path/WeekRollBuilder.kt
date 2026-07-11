package app.pbbls.android.features.path

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.WeekRollEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Pure builder for the Path screen's weeks roll — mirrors iOS
 * `WeekRollBuilder.swift` exactly:
 *
 * Returns every ISO week with at least one pebble, plus the current week (so
 * a fresh user always sees their current-week empty state). Future weeks are
 * NOT pre-created — they only appear once a pebble is dated into them.
 * Entries sort ascending by [WeekRollEntry.weekStart].
 *
 * Within each entry, pebbles sort asymmetrically:
 * - Past weeks (Monday strictly before the current week's Monday) sort
 *   **ascending** by `happenedAt` — reading time forward.
 * - Current and future weeks sort **descending** — most recent first.
 */
object WeekRollBuilder {
    /** ISO Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** ISO week-of-week-based-year number of a week's Monday (1..53). */
    fun isoWeekNumber(weekStart: LocalDate): Int = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    fun build(
        pebbles: List<Pebble>,
        zone: ZoneId,
        today: LocalDate,
    ): List<WeekRollEntry> {
        val currentStart = weekStart(today)

        // Bucket pebbles by their week's Monday, in the device zone (the
        // iOS Calendar analog — grouping follows the user's wall clock).
        val grouped =
            pebbles.groupBy { pebble ->
                weekStart(pebble.happenedAt.atZoneSameInstant(zone).toLocalDate())
            }

        // Union: every week with pebbles, plus current. No empty future weeks.
        val weekStarts = grouped.keys + currentStart

        return weekStarts
            .sorted()
            .map { ws ->
                val raw = grouped[ws].orEmpty()
                val sorted =
                    if (ws < currentStart) {
                        raw.sortedBy { it.happenedAt.toInstant() }
                    } else {
                        raw.sortedByDescending { it.happenedAt.toInstant() }
                    }
                WeekRollEntry(weekStart = ws, pebbles = sorted)
            }
    }

    fun previous(
        of: LocalDate,
        entries: List<WeekRollEntry>,
    ): WeekRollEntry? {
        val idx = entries.indexOfFirst { it.weekStart == of }
        if (idx <= 0) return null
        return entries[idx - 1]
    }

    fun next(
        of: LocalDate,
        entries: List<WeekRollEntry>,
    ): WeekRollEntry? {
        val idx = entries.indexOfFirst { it.weekStart == of }
        if (idx < 0 || idx + 1 >= entries.size) return null
        return entries[idx + 1]
    }
}
