package app.pbbls.android.testing

import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.AchievementUnlockRecord
import app.pbbls.android.services.AchievementsServicing

/**
 * In-memory [AchievementsServicing] (#849) — the achievements load path,
 * drivable without a live project. See [PebblesTestHarness] for where these
 * live and why.
 */
class FakeAchievementsService(
    /** What [loadCatalog] returns on a successful call. Settable mid-test. */
    var catalog: List<AchievementRecord> = emptyList(),
    /** What [loadUnlocks] returns on a successful call. Settable mid-test. */
    var unlocks: List<AchievementUnlockRecord> = emptyList(),
) : AchievementsServicing {
    var catalogLoadCount = 0
        private set

    var checkCount = 0
        private set

    var fireCheckCount = 0
        private set

    private val armed = ArmedFailure()

    /**
     * Thrown by the next [loadCatalog] or [loadUnlocks], then cleared.
     *
     * Deliberately not armed on [checkIgnoringFailure]: its contract is that it
     * swallows everything, so a fake that could throw from it would let a test
     * assert behaviour the real service cannot produce.
     */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun loadCatalog(): List<AchievementRecord> {
        catalogLoadCount += 1
        armed.fire()
        return catalog
    }

    override suspend fun loadUnlocks(): List<AchievementUnlockRecord> {
        armed.fire()
        return unlocks
    }

    override suspend fun checkIgnoringFailure() {
        checkCount += 1
    }

    override fun fireCheck() {
        fireCheckCount += 1
    }
}
