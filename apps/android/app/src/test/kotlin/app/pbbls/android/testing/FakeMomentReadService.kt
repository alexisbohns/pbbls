package app.pbbls.android.testing

import app.pbbls.android.core.data.MomentReadServicing
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PebbleGlyph

class FakeMomentReadService(
    var path: List<PathMoment> = emptyList(),
    var moments: Map<String, MomentDetail> = emptyMap(),
    var withSoul: List<MomentDetail> = emptyList(),
    var inCollection: List<MomentDetail> = emptyList(),
    var glyph: PebbleGlyph? = null,
) : MomentReadServicing {
    var loadPathCount = 0
        private set
    val loadedMomentIds = mutableListOf<String>()
    val resolvedGlyphFor = mutableListOf<String>()
    private val armed = ArmedFailure()

    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    /** Fails every `loadPath` while set, whatever else is armed: the first-pebble read is not `loadMoment`. */
    var failPath: Exception? = null

    /** Parks `loadPath` until completed, to prove a slow read holds nothing up. */
    var pathGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun loadPath(): List<PathMoment> {
        loadPathCount += 1
        pathGate?.await()
        failPath?.let { throw it }
        armed.fire()
        return path
    }

    override suspend fun loadMoment(momentId: String): MomentDetail {
        loadedMomentIds += momentId
        armed.fire()
        return moments[momentId] ?: throw NoSuchElementException(momentId)
    }

    override suspend fun loadMomentsWithSoul(soulId: String): List<MomentDetail> {
        armed.fire()
        return withSoul
    }

    override suspend fun loadMomentsInCollection(collectionId: String): List<MomentDetail> {
        armed.fire()
        return inCollection
    }

    override suspend fun resolveGlyph(pebbleId: String): PebbleGlyph? {
        resolvedGlyphFor += pebbleId
        armed.fire()
        return glyph
    }
}
