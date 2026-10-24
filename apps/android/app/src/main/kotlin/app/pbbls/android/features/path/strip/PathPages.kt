package app.pbbls.android.features.path.strip

import app.pbbls.android.core.model.WeekRollEntry
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.StripItem
import app.pbbls.android.features.path.WeekRollBuilder
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs

/**
 * The strip as drawn and the pages it focuses (#1207). The server's strip is the truth; the feed only
 * fills what the strip cannot know yet: a week the feed holds and the strip does not (the strip failed
 * to load, or lags a write by one fetch) joins as a week with its moments counted and its cairn left
 * blank. Nothing here aggregates a day.
 */
object PathPages {
    fun items(
        strip: Strip?,
        entries: List<WeekRollEntry>,
        today: LocalDate,
    ): List<StripItem> {
        val current = WeekRollBuilder.weekStart(today)
        val fromServer = strip?.items.orEmpty()
        val focusableWeeks =
            fromServer
                .filterIsInstance<StripItem.Week>()
                .filter { it.focusable }
                .map { it.weekStart }
                .toSet()
        val missing =
            entries
                .filter { it.weekStart !in focusableWeeks }
                .associate { entry ->
                    entry.weekStart to
                        StripItem.Week(
                            weekStart = entry.weekStart,
                            isoWeek = WeekRollBuilder.isoWeekNumber(entry.weekStart),
                            isoYear = entry.weekStart.get(IsoFields.WEEK_BASED_YEAR),
                            moments = entry.moments.size,
                            focusable = true,
                            live = entry.weekStart == current,
                            days = emptyList(),
                        )
                }
        // A spacer the feed now fills is replaced in place; the rest join in date order.
        val kept =
            fromServer.map { item ->
                if (item is StripItem.Week &&
                    item.weekStart in missing
                ) {
                    missing.getValue(item.weekStart)
                } else {
                    item
                }
            }
        val placed = kept.filterIsInstance<StripItem.Week>().map { it.weekStart }.toSet()
        return (
            kept +
                missing.values.filter {
                    it.weekStart !in placed
                }
        ).sortedWith(compareBy({ sortWeek(it) }, { if (it is StripItem.Month) 1 else 0 }))
    }

    fun pages(items: List<StripItem>): List<PathPage> = items.filter { it.focusable }.map(::pageOf)

    fun pageOf(item: StripItem): PathPage =
        when (item) {
            is StripItem.Week -> PathPage.Week(item.weekStart)
            is StripItem.Month -> PathPage.Month(item.month)
        }

    /**
     * The iOS `PathView.load()` refocus rule, over pages: keep the focus if it is still a page;
     * otherwise the current week; otherwise the page closest in time (a month by its last day).
     * Null only without pages.
     */
    fun refocused(
        pages: List<PathPage>,
        focused: PathPage?,
        today: LocalDate,
    ): PathPage? {
        if (pages.isEmpty()) return null
        if (focused != null && focused in pages) return focused
        val current = PathPage.Week(WeekRollBuilder.weekStart(today))
        if (current in pages) return current
        val anchor = (focused ?: current).date()
        return pages.minByOrNull { abs(it.date().toEpochDay() - anchor.toEpochDay()) }
    }

    // A month sits after the week that holds its last day.
    private fun sortWeek(item: StripItem): LocalDate =
        when (item) {
            is StripItem.Week -> item.weekStart
            is StripItem.Month -> WeekRollBuilder.weekStart(item.month.atEndOfMonth())
        }

    private fun PathPage.date(): LocalDate =
        when (this) {
            is PathPage.Week -> weekStart
            is PathPage.Month -> month.atEndOfMonth()
        }
}
