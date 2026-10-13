package app.pbbls.android.testing

import app.pbbls.android.core.data.CompleteSnapshotStoring
import app.pbbls.android.core.model.CompleteSnapshot

class FakeCompleteSnapshotStore : CompleteSnapshotStoring {
    val stored = mutableMapOf<String, Pair<String, CompleteSnapshot>>()
    var clearAllCount = 0
        private set

    override fun load(
        momentId: String,
        ownerId: String,
    ): CompleteSnapshot? = stored[momentId]?.takeIf { it.first == ownerId }?.second?.takeUnless { it.isEmpty }

    override fun save(
        momentId: String,
        ownerId: String,
        snapshot: CompleteSnapshot,
    ) {
        if (snapshot.isEmpty) stored.remove(momentId) else stored[momentId] = ownerId to snapshot
    }

    override fun clear(momentId: String) {
        stored.remove(momentId)
    }

    override fun clearAll() {
        clearAllCount += 1
        stored.clear()
    }
}
