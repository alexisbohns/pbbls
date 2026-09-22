package app.pbbls.android.testing

import app.pbbls.android.core.model.ComposePebbleResponse
import app.pbbls.android.core.model.PebbleDraft
import app.pbbls.android.core.model.PebbleSnapPayload
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.PebbleWriteServicing

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
    /** What [update] returns. */
    var updateResult: ComposeResult = ComposeResult.Success(ComposePebbleResponse(pebbleId = "pebble-1")),
) : PebbleWriteServicing {
    /** Arguments of every [create] call, oldest first. */
    val createCalls = mutableListOf<Pair<PebbleDraft, List<PebbleSnapPayload>?>>()

    /** Arguments of every [update] call, oldest first. */
    val updateCalls = mutableListOf<Triple<String, PebbleDraft, List<PebbleSnapPayload>>>()

    /** Every pebble id passed to [delete], oldest first. */
    val deletedPebbleIds = mutableListOf<String>()

    private val armed = ArmedFailure()

    /**
     * Thrown by the next call, then cleared. Only [delete] throws in production;
     * `create`/`update` report failure through [ComposeResult.Failure], so prefer
     * [createResult] / [updateResult] there.
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
        armed.fire()
        return createResult
    }

    override suspend fun update(
        pebbleId: String,
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>,
    ): ComposeResult {
        updateCalls += Triple(pebbleId, draft, snaps)
        armed.fire()
        return updateResult
    }

    override suspend fun delete(pebbleId: String) {
        deletedPebbleIds += pebbleId
        armed.fire()
    }
}
