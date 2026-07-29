package app.pbbls.android.services

import android.content.Context
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.PebbleDraftPayload
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Crash insurance for the open composer (M47) — ports iOS
 * `ComposerSnapshotStore.swift`.
 *
 * Deliberately **not** offline support: see the 2026-07-29 "Offline is a
 * non-goal on every surface" decision-log entry. No merge logic, no cross-device
 * sync, one composer at a time, cleared on publish or server-draft save. Media
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
) {
    private companion object {
        const val PREFS_NAME = "pebbles_prefs"
        const val KEY_SNAPSHOT = "composerSnapshot"
        const val TAG = "ComposerSnapshotStore"
    }

    // encodeDefaults stays off so unset keys are omitted, matching the wire.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The stored snapshot, or null when there is nothing worth restoring. A
     * payload written by an older build must never crash the composer, so a
     * decode failure discards the entry rather than propagating.
     */
    fun load(): PebbleDraftPayload? {
        val raw = prefs().getString(KEY_SNAPSHOT, null) ?: return null
        return try {
            json.decodeFromString<PebbleDraftPayload>(raw).takeUnless { it.isEmpty }
        } catch (e: Exception) {
            Log.w(TAG, "discarding unreadable snapshot", e)
            clear()
            null
        }
    }

    /** Overwrite the snapshot. Callers debounce; see [ComposerAutosave]. */
    fun save(payload: PebbleDraftPayload) {
        if (payload.isEmpty) {
            clear()
            return
        }
        try {
            val encoded = json.encodeToString(payload)
            prefs().edit().putString(KEY_SNAPSHOT, encoded).apply()
        } catch (e: Exception) {
            Log.e(TAG, "snapshot write failed", e)
        }
    }

    fun clear() {
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

/** Adapts the real store to [ComposerAutosave.SnapshotSink]. */
fun ComposerSnapshotStore.asSink(): ComposerAutosave.SnapshotSink =
    object : ComposerAutosave.SnapshotSink {
        override fun write(payload: PebbleDraftPayload) = save(payload)

        override fun erase() = clear()
    }

val LocalComposerSnapshotStore =
    staticCompositionLocalOf<ComposerSnapshotStore> {
        error("LocalComposerSnapshotStore not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
