package app.pbbls.android.testing

import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.Pebble
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
    /** What [loadPebbles] returns on a successful call. */
    var pebbles: List<Pebble> = emptyList(),
) : CollectionsServicing {
    var listCount = 0
        private set

    var loadCollectionCount = 0
        private set

    /** `(name, mode)` of every [create], oldest first. */
    val createCalls = mutableListOf<Pair<String, CollectionMode?>>()

    /** `(collectionId, name, mode)` of every [update], oldest first. */
    val updateCalls = mutableListOf<Triple<String, String, CollectionMode?>>()

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

    override suspend fun loadPebbles(collectionId: String): List<Pebble> {
        armed.fire()
        return pebbles
    }

    override suspend fun create(
        name: String,
        mode: CollectionMode?,
    ) {
        createCalls += name to mode
        writeGate?.await()
        armed.fire()
    }

    override suspend fun update(
        collectionId: String,
        name: String,
        mode: CollectionMode?,
    ) {
        updateCalls += Triple(collectionId, name, mode)
        writeGate?.await()
        armed.fire()
    }

    override suspend fun delete(collectionId: String) {
        deleteCalls += collectionId
        armed.fire()
    }
}
