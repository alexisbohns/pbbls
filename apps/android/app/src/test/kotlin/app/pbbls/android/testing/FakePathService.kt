package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.services.PathServicing

/**
 * In-memory [PathServicing] for tests.
 *
 * It holds no `SupabaseClient` and reads no `BuildConfig` — that is the whole
 * point: the Path load path is drivable without a live project (#848).
 *
 * Lives in `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
 */
class FakePathService(
    /** What [loadPathPebbles] returns on a successful call. Settable mid-test. */
    var pebbles: List<Pebble> = emptyList(),
) : PathServicing {
    var loadCount = 0
        private set

    /**
     * Thrown by the next call, then cleared — so one fake can drive a failed
     * load and the retry that succeeds without being rebuilt.
     */
    var failNext: Exception? = null

    override suspend fun loadPathPebbles(): List<Pebble> {
        loadCount += 1
        val failure = failNext
        if (failure != null) {
            failNext = null
            throw failure
        }
        return pebbles
    }
}
