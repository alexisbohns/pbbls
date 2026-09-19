package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.services.PathServicing

/**
 * In-memory [PathServicing] (#848) — the Path load path, drivable without a live
 * project. See [PebblesTestHarness] for where these live and why.
 */
class FakePathService(
    /** What [loadPathPebbles] returns on a successful call. Settable mid-test. */
    var pebbles: List<Pebble> = emptyList(),
) : PathServicing {
    var loadCount = 0
        private set

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun loadPathPebbles(): List<Pebble> {
        loadCount += 1
        armed.fire()
        return pebbles
    }
}
