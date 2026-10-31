package app.pbbls.android.testing

import app.pbbls.android.core.data.AchievementCheckResult
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.ZoneId

/**
 * In-memory [AchievementsServicing] (#849, #1307): the catalog, and the page's
 * read, device copy, seen marks and pulls, drivable without a live project. See
 * [PebblesTestHarness] for where these live and why.
 */
class FakeAchievementsService(
    /** What [loadCatalog] returns on a successful call. Settable mid-test. */
    var catalog: List<AchievementRecord> = emptyList(),
    /** What [page] returns. An empty page by default: every card hidden. */
    var served: AchievementsPage = decodeAchievementsPage("{}"),
    /** What [cachedPage] returns: the device's copy. */
    var kept: AchievementsPage? = null,
    /** What [pullCeremony] returns when it does not throw. */
    var pullResult: CeremonyPull = CeremonyPull.AlreadyPulled,
    /** What [loadUnlocks] returns: the unlocked achievement ids. */
    var unlocks: Set<String> = emptySet(),
    /** What [claimBluePebble] returns when it does not throw; empty is "not granted". */
    var claimResult: List<AchievementCheckResult> = emptyList(),
) : AchievementsServicing {
    var unlocksLoadCount = 0
        private set

    var claimCount = 0
        private set

    var catalogLoadCount = 0
        private set

    var checkCount = 0
        private set

    var fireCheckCount = 0
        private set

    var pageCalls = 0
        private set

    /** Every check and page call in order, so a test can assert the retroactive grant runs first. */
    val calls = mutableListOf<String>()

    val seen = mutableListOf<AchievementCard>()

    val pulls = mutableListOf<CompletionStack>()

    /** The zone of every [page], [cachedPage] and [pullCeremony] call, in order. */
    val zones = mutableListOf<ZoneId>()

    /** What [keptAt] answers: the page's age for the shelf (D18). */
    var keptAt: Instant? = null

    /** When set, [page] waits for it: a test can observe what shows before the read answers. */
    var pageGate: CompletableDeferred<Unit>? = null

    /**
     * One gate per [page] call, taken in order ahead of [pageGate]: a test can land overlapping
     * reads out of order. Each read answers what [served] was when it was asked.
     */
    val pageGates = ArrayDeque<CompletableDeferred<Unit>>()

    private val armed = ArmedFailure()
    private val pageFailure = ArmedFailure()
    private val pullFailure = ArmedFailure()
    private val unlocksFailure = ArmedFailure()
    private val claimFailure = ArmedFailure()

    /** Thrown by the next [loadUnlocks], then cleared. */
    var failNextUnlocks: Exception?
        get() = unlocksFailure.next
        set(value) {
            unlocksFailure.next = value
        }

    /** Thrown by the next [claimBluePebble], then cleared. */
    var failNextClaim: Exception?
        get() = claimFailure.next
        set(value) {
            claimFailure.next = value
        }

    /**
     * Thrown by the next [loadCatalog], then cleared.
     *
     * Deliberately not armed on [checkIgnoringFailure]: its contract is that it
     * swallows everything, so a fake that could throw from it would let a test
     * assert behaviour the real service cannot produce. [markSeen] is not armed
     * for the same reason: the real one logs and swallows.
     */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    /** Thrown by the next [page], then cleared. */
    var failNextPage: Exception?
        get() = pageFailure.next
        set(value) {
            pageFailure.next = value
        }

    /** Thrown by the next [pullCeremony], then cleared. */
    var failNextPull: Exception?
        get() = pullFailure.next
        set(value) {
            pullFailure.next = value
        }

    override suspend fun loadCatalog(): List<AchievementRecord> {
        catalogLoadCount += 1
        armed.fire()
        return catalog
    }

    override suspend fun loadUnlocks(): Set<String> {
        unlocksLoadCount += 1
        unlocksFailure.fire()
        return unlocks
    }

    override suspend fun claimBluePebble(): List<AchievementCheckResult> {
        claimCount += 1
        calls += "claim"
        claimFailure.fire()
        return claimResult
    }

    override suspend fun checkIgnoringFailure() {
        checkCount += 1
        calls += "check"
    }

    override fun fireCheck() {
        fireCheckCount += 1
    }

    /** What [isFiringCheck] says: a mutation-path check in flight. */
    var firingCheck = false

    override val isFiringCheck: Boolean get() = firingCheck

    override suspend fun page(zone: ZoneId): AchievementsPage {
        pageCalls += 1
        calls += "page"
        zones += zone
        val answer = served
        (pageGates.removeFirstOrNull() ?: pageGate)?.await()
        pageFailure.fire()
        return answer
    }

    override suspend fun cachedPage(zone: ZoneId): AchievementsPage? {
        zones += zone
        return kept
    }

    override suspend fun keptAt(zone: ZoneId): Instant? = keptAt

    override fun markSeen(card: AchievementCard) {
        seen += card
    }

    override suspend fun pullCeremony(
        stack: CompletionStack,
        zone: ZoneId,
    ): CeremonyPull {
        pulls += stack
        zones += zone
        pullFailure.fire()
        return pullResult
    }
}
