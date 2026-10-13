package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.pbbls.android.core.model.CompleteSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The Complete flow's autosave seam; the fake is a map. */
interface CompleteSnapshotStoring {
    fun load(
        momentId: String,
        ownerId: String,
    ): CompleteSnapshot?

    fun save(
        momentId: String,
        ownerId: String,
        snapshot: CompleteSnapshot,
    )

    fun clear(momentId: String)

    /** Every moment's snapshot, on sign-out. */
    fun clearAll()
}

/** What is persisted: the snapshot and the user who wrote it (GDP-07, as the old composer snapshot). */
@Serializable
data class OwnedCompleteSnapshot(
    val ownerId: String,
    val snapshot: CompleteSnapshot,
) {
    fun restorableFor(requesterId: String): CompleteSnapshot? = snapshot.takeIf { ownerId == requesterId && !it.isEmpty }
}

/**
 * Crash and leave-for-later insurance for the Complete flow, one entry per
 * moment in the existing `pebbles_prefs` file. Not offline support (decision
 * log 2026-07-29). Cleared on completion, on `delete_moment`, and on sign-out.
 */
@Singleton
class CompleteSnapshotStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CompleteSnapshotStoring {
        private val json = Json { ignoreUnknownKeys = true }

        override fun load(
            momentId: String,
            ownerId: String,
        ): CompleteSnapshot? {
            val raw = prefs().getString(key(momentId), null) ?: return null
            val stored =
                try {
                    json.decodeFromString(OwnedCompleteSnapshot.serializer(), raw)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "discarding an unreadable snapshot", e)
                    clear(momentId)
                    return null
                }
            if (stored.ownerId != ownerId) {
                Log.i(TAG, "discarding a snapshot owned by another user")
                clear(momentId)
                return null
            }
            return stored.restorableFor(ownerId)
        }

        override fun save(
            momentId: String,
            ownerId: String,
            snapshot: CompleteSnapshot,
        ) {
            if (snapshot.isEmpty) {
                clear(momentId)
                return
            }
            prefs().edit {
                putString(key(momentId), json.encodeToString(OwnedCompleteSnapshot.serializer(), OwnedCompleteSnapshot(ownerId, snapshot)))
            }
        }

        override fun clear(momentId: String) {
            prefs().edit { remove(key(momentId)) }
        }

        override fun clearAll() {
            val prefs = prefs()
            val keys = prefs.all.keys.filter { it.startsWith(PREFIX) }
            prefs.edit {
                keys.forEach { remove(it) }
                // The retired composer's snapshot must not outlive the sign-out it was scoped to.
                remove(LEGACY_COMPOSER_SNAPSHOT_KEY)
            }
        }

        private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val TAG = "complete-snapshot"
            private const val PREFS_NAME = "pebbles_prefs"
            private const val PREFIX = "completeSnapshot."
            private const val LEGACY_COMPOSER_SNAPSHOT_KEY = "composerSnapshot"

            fun key(momentId: String): String = PREFIX + momentId.lowercase()
        }
    }
