package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import app.pbbls.android.core.model.PebbleDraftPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The local crash-snapshot seam (#849).
 *
 * `ComposerSnapshotStore` is SharedPreferences, so it needs a `Context` and a
 * JVM test cannot build one. `RecordFlowViewModel` drives the whole draft
 * lifecycle through this interface instead, and the fake is a map.
 *
 * **Owned by a user id.** The snapshot is intimate, unpublished content on a
 * device that can change hands, so it is stamped with the user who wrote it and
 * [load] only ever hands it back to that same user. A snapshot owned by anyone
 * else is discarded on sight rather than left for its owner: they are not the
 * one signed in, and whoever is must never see it. [clear] runs on every
 * sign-out (`RootViewModel`) as well; the owner check is what still holds when
 * a session swaps without passing through signed-out.
 */
interface ComposerSnapshotStoring {
    fun load(ownerId: String): PebbleDraftPayload?

    fun save(
        payload: PebbleDraftPayload,
        ownerId: String,
    )

    fun clear()
}

/**
 * What is actually persisted: the payload plus who wrote it. A bare payload
 * written by a build that predates the owner stamp fails to decode as this, and
 * [ComposerSnapshotStore.load] discards it — an unowned snapshot cannot prove
 * it belongs to whoever is signed in now.
 */
@Serializable
data class OwnedComposerSnapshot(
    val ownerId: String,
    val payload: PebbleDraftPayload,
) {
    /** The payload, only for its owner and only when there is something to restore. */
    fun restorableFor(requesterId: String): PebbleDraftPayload? = payload.takeIf { ownerId == requesterId && !it.isEmpty }
}

/**
 * Crash insurance for the open composer (M47) — ports iOS
 * `ComposerSnapshotStore.swift`.
 *
 * Deliberately **not** offline support: see the 2026-07-29 "Offline is a
 * non-goal on every surface" decision-log entry. No merge logic, no cross-device
 * sync, one composer at a time, cleared on publish, server-draft save or
 * sign-out, and scoped to the user who wrote it (GDP-07). Media
 * is excluded (design D3): an intentional "save as draft" earns durable media, an
 * accidental crash recovery does not.
 *
 * Backed by the existing `pebbles_prefs` SharedPreferences file rather than
 * DataStore — that would need a `libs.versions.toml` entry, and version bumps
 * are deliberate isolated commits (`apps/android/CLAUDE.md`). One JSON string is
 * well within what SharedPreferences is for.
 *
 * The composer state this guards is otherwise lost on any process death:
 * `CreatePebbleScreen` is an `if`-composed cover with no back-stack entry.
 */
class ComposerSnapshotStore(
    private val context: Context,
) : ComposerSnapshotStoring {
    private companion object {
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_SNAPSHOT = "composerSnapshot"
        const val TAG = "ComposerSnapshotStore"
    }

    // encodeDefaults stays off so unset keys are omitted, matching the wire.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * [ownerId]'s stored snapshot, or null when there is nothing worth
     * restoring. A payload written by an older build must never crash the
     * composer, so a decode failure discards the entry rather than propagating;
     * so does a snapshot owned by someone other than [ownerId].
     */
    override fun load(ownerId: String): PebbleDraftPayload? {
        val raw = prefs().getString(KEY_SNAPSHOT, null) ?: return null
        val stored =
            try {
                json.decodeFromString<OwnedComposerSnapshot>(raw)
            } catch (e: Exception) {
                Log.w(TAG, "discarding unreadable snapshot", e)
                clear()
                return null
            }
        if (stored.ownerId != ownerId) {
            Log.i(TAG, "discarding a snapshot owned by another user")
            clear()
            return null
        }
        return stored.restorableFor(ownerId)
    }

    /** Overwrite the snapshot. Callers debounce; see [ComposerAutosave]. */
    override fun save(
        payload: PebbleDraftPayload,
        ownerId: String,
    ) {
        if (payload.isEmpty) {
            clear()
            return
        }
        try {
            val encoded = json.encodeToString(OwnedComposerSnapshot(ownerId, payload))
            prefs().edit().putString(KEY_SNAPSHOT, encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "snapshot write failed", e)
        }
    }

    override fun clear() {
        prefs().edit().remove(KEY_SNAPSHOT).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

/**
 * Pure debounce policy over a snapshot sink, kept free of `Context` and
 * `SharedPreferences` so it is JVM-testable without a device — the same
 * discipline as `PebbleWriteService`'s companion helpers.
 *
 * Not a coroutine timer: the composer drives it from a `LaunchedEffect` keyed on
 * the payload, so the delay lives at the call site and this only has to decide
 * *what* to write. That keeps the whole thing synchronous and deterministic.
 */
class ComposerAutosave(
    private val sink: SnapshotSink,
) {
    /** Indirection so tests can assert writes without touching Android APIs. */
    interface SnapshotSink {
        fun write(payload: PebbleDraftPayload)

        fun erase()
    }

    private var pending: PebbleDraftPayload? = null

    /** Remember what the composer currently holds, without writing yet. */
    fun stage(payload: PebbleDraftPayload) {
        pending = payload.takeUnless { it.isEmpty }
    }

    /**
     * Write the staged snapshot. Called once the debounce delay has elapsed, and
     * again when the composer leaves the foreground.
     */
    fun flush() {
        val payload = pending ?: return
        pending = null
        sink.write(payload)
    }

    /**
     * The composer published or saved a server draft — the snapshot is redundant,
     * and leaving it would re-offer work the user already committed. Drops the
     * staged value too, so a later [flush] cannot resurrect it.
     */
    fun clear() {
        pending = null
        sink.erase()
    }
}

/**
 * Adapts a store to [ComposerAutosave.SnapshotSink], stamping each write with
 * [ownerId] read at write time. With nobody signed in there is no one to own
 * the snapshot, so the write is dropped rather than stored unowned.
 */
fun ComposerSnapshotStoring.asSink(ownerId: () -> String?): ComposerAutosave.SnapshotSink =
    object : ComposerAutosave.SnapshotSink {
        override fun write(payload: PebbleDraftPayload) {
            val owner = ownerId() ?: return
            save(payload, owner)
        }

        override fun erase() = clear()
    }
