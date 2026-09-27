package app.pbbls.android.testing

import app.pbbls.android.core.data.ComposerSnapshotStoring
import app.pbbls.android.core.model.PebbleDraftPayload

/**
 * In-memory [ComposerSnapshotStoring] (#849) — the crash snapshot, without
 * SharedPreferences or a `Context`.
 *
 * Seed [snapshot] to make the composer open with the restore prompt up; read it
 * back to assert the autosave wrote, or that a publish cleared it.
 *
 * Mirrors the real store's owner scoping (GDP-07): set [ownerId] to someone
 * other than the signed-in user and [load] discards the snapshot instead of
 * returning it. A seed with no [ownerId] counts as the caller's own, so a test
 * that is not about ownership seeds just the payload.
 */
class FakeComposerSnapshotStore(
    var snapshot: PebbleDraftPayload? = null,
    var ownerId: String? = null,
) : ComposerSnapshotStoring {
    var clearCount = 0
        private set

    val saved = mutableListOf<PebbleDraftPayload>()

    override fun load(ownerId: String): PebbleDraftPayload? {
        val owner = this.ownerId
        if (owner != null && owner != ownerId) {
            clear()
            return null
        }
        return snapshot
    }

    override fun save(
        payload: PebbleDraftPayload,
        ownerId: String,
    ) {
        saved += payload
        snapshot = payload
        this.ownerId = ownerId
    }

    override fun clear() {
        clearCount += 1
        snapshot = null
        ownerId = null
        saved.clear()
    }
}
