package app.pbbls.android.testing

import app.pbbls.android.core.data.LogsServicing
import app.pbbls.android.core.model.Log
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [LogsServicing] (#849) — the Lab's four feeds and the reaction
 * toggle.
 *
 * Each feed has its own failure switch rather than sharing [ArmedFailure]:
 * the Lab's whole design is that one dead feed leaves the others rendering, and
 * a one-shot arming cannot express "the backlog is down but the changelog is
 * fine".
 */
class FakeLogsService(
    var announcements: List<Log> = emptyList(),
    var changelog: List<Log> = emptyList(),
    var initiatives: List<Log> = emptyList(),
    var backlog: List<Log> = emptyList(),
    var reactions: Set<String> = emptySet(),
) : LogsServicing {
    var announcementsFailure: Exception? = null
    var changelogFailure: Exception? = null
    var initiativesFailure: Exception? = null
    var backlogFailure: Exception? = null
    var reactionsFailure: Exception? = null

    /** [log]'s answer and failure switch — [AnnouncementDetailViewModel]'s by-id load. */
    var log: Log? = null
    var logFailure: Exception? = null
    val logCalls = mutableListOf<String>()

    /** Every limit the Lab asked each capped feed for, oldest first. */
    val changelogLimits = mutableListOf<Int?>()
    val backlogLimits = mutableListOf<Int?>()

    /** Every log id passed to [react] / [unreact], oldest first. */
    val reactCalls = mutableListOf<String>()
    val unreactCalls = mutableListOf<String>()

    /** Awaited by [react] and [unreact] when set, to hold a toggle in flight. */
    var reactGate: CompletableDeferred<Unit>? = null

    /** Thrown by the next [react] / [unreact], then cleared. */
    private val armed = ArmedFailure()

    var failNextReaction: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    /** Base the fake stands in for `AppEnvironment` with — never read here. */
    var coverBase: String? = "https://project.test/storage/v1/object/public/lab"

    /** Sets every feed's failure at once — the "Lab is down" case. */
    fun failEveryFeed(error: Exception) {
        announcementsFailure = error
        changelogFailure = error
        initiativesFailure = error
        backlogFailure = error
    }

    override suspend fun announcements(limit: Int?): List<Log> {
        announcementsFailure?.let { throw it }
        return announcements
    }

    override suspend fun log(id: String): Log? {
        logCalls += id
        logFailure?.let { throw it }
        return log
    }

    override suspend fun changelog(limit: Int?): List<Log> {
        changelogLimits += limit
        changelogFailure?.let { throw it }
        return changelog
    }

    override suspend fun initiatives(): List<Log> {
        initiativesFailure?.let { throw it }
        return initiatives
    }

    override suspend fun backlog(limit: Int?): List<Log> {
        backlogLimits += limit
        backlogFailure?.let { throw it }
        return backlog
    }

    override suspend fun myReactions(): Set<String> {
        reactionsFailure?.let { throw it }
        return reactions
    }

    override suspend fun react(logId: String) {
        reactCalls += logId
        reactGate?.await()
        armed.fire()
    }

    override suspend fun unreact(logId: String) {
        unreactCalls += logId
        reactGate?.await()
        armed.fire()
    }

    override fun coverImageUrl(log: Log): String? = log.coverImagePath?.let { "$coverBase/$it" }
}
