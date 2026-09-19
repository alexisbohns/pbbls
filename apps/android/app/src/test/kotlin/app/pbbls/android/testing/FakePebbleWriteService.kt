package app.pbbls.android.testing

import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.PebbleWriteServicing

/**
 * In-memory [PebbleWriteServicing] for tests.
 *
 * It holds no `SupabaseClient` and reads no `BuildConfig` — that is the whole
 * point: the composer's save path is drivable without a live project (#848).
 * The overrides take every parameter positionally because Kotlin forbids a
 * default on an override; the defaults are inherited from the interface.
 *
 * Lives in `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
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

    /**
     * Thrown by the next call, then cleared — so one fake can drive a failure
     * and the retry that follows it without being rebuilt. Only [delete]
     * throws in production; `create`/`update` report failure through
     * [ComposeResult.Failure], so prefer [createResult] / [updateResult] there.
     */
    var failNext: Exception? = null

    override suspend fun create(
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>?,
    ): ComposeResult {
        createCalls += draft to snaps
        throwIfArmed()
        return createResult
    }

    override suspend fun update(
        pebbleId: String,
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>,
    ): ComposeResult {
        updateCalls += Triple(pebbleId, draft, snaps)
        throwIfArmed()
        return updateResult
    }

    override suspend fun delete(pebbleId: String) {
        deletedPebbleIds += pebbleId
        throwIfArmed()
    }

    private fun throwIfArmed() {
        val failure = failNext ?: return
        failNext = null
        throw failure
    }
}
