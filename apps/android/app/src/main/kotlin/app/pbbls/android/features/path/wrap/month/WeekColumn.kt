package app.pbbls.android.features.path.wrap.month

import app.pbbls.android.core.model.wrap.CoverPebble
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.ui.marks.CairnGeometry
import app.pbbls.android.features.path.wrap.intro.IntroWeekColumn
import java.time.YearMonth

/** A day of a week's column (#1242; iOS `WeekColumnDay`, D17): outside the month, a day without a pebble, or a day's pebbles in the cover's order. */
internal sealed interface WeekColumnDay {
    data object Outside : WeekColumnDay

    data object Empty : WeekColumnDay

    data class Pebbles(
        val pebbles: List<CoverPebble>,
    ) : WeekColumnDay
}

/** A week as a column of its card (iOS `WeekColumn`): its Monday, its seven days, its cairn's days. */
internal data class WeekColumn(
    val week: MonthWeek,
    val days: List<WeekColumnDay>,
) {
    /**
     * The column's intro shape: each day's pebbles shown (null outside the month), and its cairn's stones, none
     * for a week without a pebble, whose dotted stone the column draws even when its moments built a cairn.
     */
    val shape: IntroWeekColumn
        get() =
            IntroWeekColumn(
                days.map { day ->
                    when (day) {
                        WeekColumnDay.Outside -> null
                        WeekColumnDay.Empty -> 0
                        is WeekColumnDay.Pebbles -> minOf(day.pebbles.size, PIPS)
                    }
                },
                if (week.pebbles == 0) 0 else CairnGeometry.widths(week.days).size,
            )

    companion object {
        /** At most this many pebbles a day; a busier day shows three. */
        const val PIPS = 3

        private const val DAYS = 7

        /** The month's weeks as columns: each day placed by its date, its pebbles from the cover. */
        fun columns(wrap: MonthWrap): List<WeekColumn> {
            // The first of a day's entries wins, as iOS's `uniquingKeysWith`.
            val byDay =
                wrap.cover
                    ?.days
                    .orEmpty()
                    .distinctBy { it.day }
                    .associate { it.day to it.pebbles }
            return wrap.weeks.orEmpty().map { week ->
                val days =
                    List(DAYS) { offset ->
                        val date = week.weekStart.plusDays(offset.toLong())
                        if (YearMonth.from(date) != wrap.month) {
                            WeekColumnDay.Outside
                        } else {
                            val pebbles = byDay[date.dayOfMonth - 1].orEmpty()
                            if (pebbles.isEmpty()) WeekColumnDay.Empty else WeekColumnDay.Pebbles(pebbles)
                        }
                    }
                WeekColumn(week, days)
            }
        }
    }
}
