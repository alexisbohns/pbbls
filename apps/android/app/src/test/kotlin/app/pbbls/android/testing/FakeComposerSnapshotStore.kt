package app.pbbls.android.testing

import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.services.ComposerSnapshotStoring

/**
 * In-memory [ComposerSnapshotStoring] (#849) — the crash snapshot, without
 * SharedPreferences or a `Context`.
 *
 * Seed [snapshot] to make the composer open with the restore prompt up; read it
 * back to assert the autosave wrote, or that a publish cleared it.
 */
class FakeComposerSnapshotStore(
    var snapshot: PebbleDraftPayload? = null,
) : ComposerSnapshotStoring {
    var clearCount = 0
        private set

    val saved = mutableListOf<PebbleDraftPayload>()

    override fun load(): PebbleDraftPayload? = snapshot

    override fun save(payload: PebbleDraftPayload) {
        saved += payload
        snapshot = payload
    }

    override fun clear() {
        clearCount += 1
        snapshot = null
        saved.clear()
    }
}
