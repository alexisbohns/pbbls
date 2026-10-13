package app.pbbls.android.testing

import app.pbbls.android.core.data.ComposeResult
import app.pbbls.android.core.data.PebbleWriteServicing
import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.PebbleSnapPayload
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [PebbleWriteServicing] (#848) — the composer's save path, drivable
 * without a live project. See [PebblesTestHarness] for where these live and why.
 *
 * The overrides take every parameter positionally because Kotlin forbids a
 * default on an override; the defaults are inherited from the interface.
 */
class FakePebbleWriteService(
    /** What [create] returns. A [ComposeResult.Failure] is the reachable error path. */
    var createResult: ComposeResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-1")),
) : PebbleWriteServicing {
    /** Arguments of every [create] call, oldest first. */
    val createCalls = mutableListOf<Pair<PebbleDraft, List<PebbleSnapPayload>?>>()

    /**
     * Awaited by [create] when set, so a test can hold a publish in flight and
     * act on the screen meanwhile — back, close — then complete it to let the
     * server "answer".
     */
    var createGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /**
     * Thrown by the next call, then cleared. `create` reports failure through
     * [ComposeResult.Failure] in production, so prefer [createResult].
     */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun create(
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>?,
    ): ComposeResult {
        createCalls += draft to snaps
        createGate?.await()
        armed.fire()
        return createResult
    }
}
