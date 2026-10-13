package app.pbbls.android.testing

import app.pbbls.android.core.data.MomentWriteServicing
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.core.model.PebbleCompletePayload
import kotlinx.coroutines.CompletableDeferred

class FakeMomentWriteService : MomentWriteServicing {
    val saved = mutableListOf<MomentSavePayload>()
    val completed = mutableListOf<PebbleCompletePayload>()
    val deletedPebbles = mutableListOf<String>()
    val deletedMoments = mutableListOf<String>()
    val karmaReads = mutableListOf<Pair<String, KarmaReason>>()
    var nextMomentId = "moment-new"
    var nextPebbleId = "pebble-new"
    var karma: Int? = 4

    /** Holds a write in flight until completed, for back-while-saving tests. */
    var gate: CompletableDeferred<Unit>? = null
    private val armed = ArmedFailure()

    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun saveMoment(payload: MomentSavePayload): String {
        gate?.await()
        armed.fire()
        saved += payload
        return payload.id ?: nextMomentId
    }

    /** Thrown by the next `completePebble` only: the edit form's "moment saved, pebble refused" case. */
    var failOnComplete: Exception? = null

    override suspend fun completePebble(payload: PebbleCompletePayload): String {
        gate?.await()
        armed.fire()
        failOnComplete?.let {
            failOnComplete = null
            throw it
        }
        completed += payload
        return nextPebbleId
    }

    override suspend fun deletePebble(pebbleId: String) {
        armed.fire()
        deletedPebbles += pebbleId
    }

    override suspend fun deleteMoment(momentId: String) {
        armed.fire()
        deletedMoments += momentId
    }

    override suspend fun karmaDelta(
        pebbleId: String,
        reason: KarmaReason,
    ): Int? {
        karmaReads += pebbleId to reason
        return karma
    }
}
