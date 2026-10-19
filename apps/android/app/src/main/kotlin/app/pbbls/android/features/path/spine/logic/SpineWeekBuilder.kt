package app.pbbls.android.features.path.spine.logic

import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.WeekRollEntry
import java.time.LocalDate
import java.time.ZoneId

/** One day of the Spine. [index] is 0 = Monday … 6 = Sunday. */
data class SpineDay(
    val date: LocalDate,
    val index: Int,
    val moments: List<PathMoment>,
    /** A past day with no moment: a seam with *still* under it. */
    val still: Boolean,
    val isToday: Boolean,
)

data class SpineWeek(
    val weekStart: LocalDate,
    val days: List<SpineDay>,
) {
    /** The week's moments in reading order: every day, top to bottom. */
    val readingOrder: List<PathMoment> get() = days.flatMap { it.moments }

    /** Completed moments only: a bare moment is on the path but is not a pebble. */
    val pebbleCount: Int get() = readingOrder.count { it.isCompleted }
}

/**
 * Port of `spine-week.ts` (#1052, spec D9): one week laid out as the Spine
 * reads it. A book, so days run oldest first in every week — the current one
 * included — and moments run oldest first within each day. `WeekRollBuilder`
 * keeps its newest-first current week for the roll; the Spine re-sorts here.
 *
 * A bare moment (#1050) takes its place like a pebble: its day is not still.
 * A past day with no moment is kept and marked `still`. A day after today is
 * not drawn unless it holds a moment: a future-dated one is rare but must
 * never vanish from the Path.
 */
object SpineWeekBuilder {
    private const val DAYS_IN_WEEK = 7

    fun build(
        entry: WeekRollEntry,
        zone: ZoneId,
        today: LocalDate,
    ): SpineWeek {
        val byDay = List(DAYS_IN_WEEK) { mutableListOf<PathMoment>() }
        for (moment in entry.moments) {
            val index = LocalDays.dayIndexInWeek(entry.weekStart, moment.happenedAt, zone)
            if (index in 0 until DAYS_IN_WEEK) byDay[index] += moment
        }

        val days = mutableListOf<SpineDay>()
        for (index in 0 until DAYS_IN_WEEK) {
            val date = entry.weekStart.plusDays(index.toLong())
            val moments = byDay[index].sortedWith(compareBy({ it.happenedAt.toInstant() }, { it.id }))
            val future = date.isAfter(today)
            if (future && moments.isEmpty()) continue
            days +=
                SpineDay(
                    date = date,
                    index = index,
                    moments = moments,
                    still = moments.isEmpty(),
                    isToday = date == today,
                )
        }
        return SpineWeek(weekStart = entry.weekStart, days = days)
    }
}
