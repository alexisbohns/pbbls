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
 * feed's own weeks. The wraps are not armed until a part renders them, so they throw.
 */
class FakePathWrapService(
    var strip: Strip = Strip(emptyList()),
) : PathWrapServicing {
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
    ): WeekWrap = throw IOException("no week wrap armed in the fake")

    override suspend fun monthWrap(
        month: YearMonth,
        zone: ZoneId,
    ): MonthWrap = throw IOException("no month wrap armed in the fake")
}
