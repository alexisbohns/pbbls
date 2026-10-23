package app.pbbls.android.testing

import app.pbbls.android.core.data.PathWrapServicing
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.Strip
import app.pbbls.android.core.model.wrap.WeekWrap
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * In-memory [PathWrapServicing] (#1207). The strip defaults to empty, which leaves the Path on the
 * feed's own weeks; a week answers from [weeks], or with an empty wrap. Months are not armed until
 * the month page renders them, so they throw.
 */
class FakePathWrapService(
    var strip: Strip = Strip(emptyList()),
    val weeks: MutableMap<LocalDate, WeekWrap> = mutableMapOf(),
) : PathWrapServicing {
    private val weekFailure = ArmedFailure()

    /** Arms the next week wrap call to throw. */
    var failNextWeek: Exception?
        get() = weekFailure.next
        set(value) {
            weekFailure.next = value
        }

    /** Week wrap calls by week, in order. */
    val weekCalls = mutableListOf<LocalDate>()

    private val failure = ArmedFailure()

    var failNext: Exception?
        get() = failure.next
        set(value) {
            failure.next = value
        }

    var stripCount = 0
        private set

    var lastZone: ZoneId? = null
        private set

    override suspend fun strip(zone: ZoneId): Strip {
        stripCount += 1
        lastZone = zone
        failure.fire()
        return strip
    }

    override suspend fun weekWrap(
        weekStart: LocalDate,
        zone: ZoneId,
    ): WeekWrap {
        weekCalls += weekStart
        weekFailure.fire()
        return weeks[weekStart] ?: emptyWeek(weekStart)
    }

    override suspend fun monthWrap(
        month: YearMonth,
        zone: ZoneId,
    ): MonthWrap = throw IOException("no month wrap armed in the fake")

    companion object {
        fun emptyWeek(weekStart: LocalDate): WeekWrap =
            WeekWrap(weekStart, 0, 0, false, 0, 0, emptyList(), null, null, null, null, null, null, null, null)
    }
}
