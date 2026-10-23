package app.pbbls.android.core.model.wrap

import java.time.LocalDate
import java.time.YearMonth

/** The Path's strip (#1207), oldest first: weeks and the months placed after the week holding their last day. */
data class Strip(
    val items: List<StripItem>,
)

sealed interface StripItem {
    val focusable: Boolean

    /** An ISO week; an empty past week is a non-focusable spacer, and [live] marks the current week. */
    data class Week(
        val weekStart: LocalDate,
        val isoWeek: Int,
        val isoYear: Int,
        val moments: Int,
        override val focusable: Boolean,
        val live: Boolean,
        val days: List<DayWeight>,
    ) : StripItem

    /** A completed month with at least one pebble. */
    data class Month(
        val month: YearMonth,
        val pebbles: Int,
    ) : StripItem {
        override val focusable: Boolean get() = true
    }
}

/** Unknown kinds are dropped, so a server that adds an item kind does not break older clients. */
fun StripDto.toModel(): Strip =
    Strip(
        items.mapNotNull { item ->
            when (item.kind) {
                "week" ->
                    item.weekStart?.let {
                        StripItem.Week(
                            LocalDate.parse(it),
                            item.isoWeek,
                            item.isoYear,
                            item.count,
                            item.focusable,
                            item.live,
                            item.days.toDayWeights(),
                        )
                    }
                "month" -> item.month?.let { StripItem.Month(YearMonth.from(LocalDate.parse(it)), item.count) }
                else -> null
            }
        },
    )
