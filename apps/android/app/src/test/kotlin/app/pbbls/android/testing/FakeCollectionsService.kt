package app.pbbls.android.testing

import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [CollectionsServicing] (#849) — the collections list, detail and
 * form paths, drivable without a live project. See [PebblesTestHarness] for
 * where these live and why.
 */
class FakeCollectionsService(
    /** What [list] returns on a successful call. Settable mid-test. */
    var collections: List<Collection> = emptyList(),
    /** What [loadCollection] returns on a successful call. */
    var collection: Collection? = null,
) : CollectionsServicing {
    var listCount = 0
        private set

    var loadCollectionCount = 0
        private set

    data class CreateCall(
        val name: String,
        val mode: CollectionMode?,
        val glyphId: String?,
    )

    data class UpdateCall(
        val collectionId: String,
        val name: String,
        val mode: CollectionMode?,
        val glyphId: String?,
    )

    /** Every [create], oldest first. */
    val createCalls = mutableListOf<CreateCall>()

    /** Every [update], oldest first. */
    val updateCalls = mutableListOf<UpdateCall>()

    /** Every id passed to [delete], oldest first. */
    val deleteCalls = mutableListOf<String>()

    /** Awaited by [create] and [update] when set — see [FakeSoulsService.writeGate]. */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Awaited by [list] when set, to hold a fetch open mid-flight. */
    var listGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun list(): List<Collection> {
        listCount += 1
        listGate?.await()
        armed.fire()
        return collections
    }

    override suspend fun loadCollection(collectionId: String): Collection {
        loadCollectionCount += 1
        armed.fire()
        return collection ?: collections.first { it.id == collectionId }
    }

    override suspend fun create(
        name: String,
        mode: CollectionMode?,
        glyphId: String?,
    ) {
        createCalls += CreateCall(name, mode, glyphId)
        writeGate?.await()
        armed.fire()
    }

    override suspend fun update(
        collectionId: String,
        name: String,
        mode: CollectionMode?,
        glyphId: String?,
    ) {
        updateCalls += UpdateCall(collectionId, name, mode, glyphId)
        writeGate?.await()
        armed.fire()
    }

    override suspend fun delete(collectionId: String) {
        deleteCalls += collectionId
        armed.fire()
    }
}
