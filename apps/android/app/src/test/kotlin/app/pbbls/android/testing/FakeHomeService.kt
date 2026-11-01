package app.pbbls.android.testing

import app.pbbls.android.core.data.HomeServicing
import app.pbbls.android.core.model.home.Guide
import app.pbbls.android.core.model.home.HomeWrapMonth
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * In-memory [HomeServicing] (#1383): Home's name, months, unfold and readings, drivable without a
 * live project. [displayName] is not armed: the real one swallows its failure and answers "".
 */
class FakeHomeService(
    var name: String = "",
    var months: List<HomeWrapMonth> = emptyList(),
    var guideRows: List<Guide> = emptyList(),
    /** What [reveal] answers when it does not throw, and what it records on the month in [months]. */
    var revealedAt: Instant = Instant.parse("2026-10-26T22:24:21Z"),
) : HomeServicing {
    var wrapsCalls = 0
        private set

    var guidesCalls = 0
        private set

    /** Every [reveal] call's month, in order. */
    val reveals = mutableListOf<YearMonth>()

    /** The zone of every [wraps] and [reveal] call, in order. */
    val zones = mutableListOf<ZoneId>()

    private val wrapsFailure = ArmedFailure()
    private val revealFailure = ArmedFailure()
    private val guidesFailure = ArmedFailure()

    /** Thrown by the next [wraps], then cleared. */
    var failNextWraps: Exception?
        get() = wrapsFailure.next
        set(value) {
            wrapsFailure.next = value
        }

    /** Thrown by the next [reveal], then cleared. */
    var failNextReveal: Exception?
        get() = revealFailure.next
        set(value) {
            revealFailure.next = value
        }

    /** Thrown by the next [guides], then cleared. */
    var failNextGuides: Exception?
        get() = guidesFailure.next
        set(value) {
            guidesFailure.next = value
        }

    override suspend fun displayName(): String = name

    override suspend fun wraps(zone: ZoneId): List<HomeWrapMonth> {
        wrapsCalls += 1
        zones += zone
        wrapsFailure.fire()
        return months
    }

    override suspend fun reveal(
        month: YearMonth,
        zone: ZoneId,
    ): Instant {
        reveals += month
        zones += zone
        revealFailure.fire()
        // As the server records it: the next read of the months has this one unfolded.
        months = months.map { if (it.month == month) it.copy(revealedAt = revealedAt) else it }
        return revealedAt
    }

    override suspend fun guides(): List<Guide> {
        guidesCalls += 1
        guidesFailure.fire()
        return guideRows
    }
}
