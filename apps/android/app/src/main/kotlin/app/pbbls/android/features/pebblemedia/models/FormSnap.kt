package app.pbbls.android.features.pebblemedia.models

/**
 * In-form representation of the (at most one — design D1) photo attached to a
 * pebble being created or edited — ports iOS `FormSnap.swift`.
 *
 * - [Existing] — already saved in the DB. The form renders the thumbnail from
 *   [Existing.storagePath] and exposes a remove affordance that triggers the
 *   eager `delete_pebble_media` RPC.
 * - [Pending] — an in-flight or just-uploaded local pick (no DB row yet).
 */
sealed interface FormSnap {
    data class Existing(
        val id: String,
        val storagePath: String,
    ) : FormSnap

    data class Pending(
        val snap: AttachedSnap,
    ) : FormSnap
}

/**
 * One photo attached to an in-progress pebble, including upload state —
 * ports iOS `AttachedSnap.swift`. Value type; immutable updates via `copy`.
 *
 * [localThumb] holds the 420px JPEG bytes so the form renders an instant
 * preview without a Storage round-trip (byte-array equality is referential —
 * state transitions always `copy` the same array, so it never matters).
 */
data class AttachedSnap(
    /** Client-generated lowercase UUID — becomes the Storage folder AND `snaps.id`. */
    val id: String,
    val localThumb: ByteArray,
    val state: UploadState,
) {
    enum class UploadState {
        UPLOADING,
        UPLOADED,
        FAILED,
    }

    /**
     * Storage folder shared by both renditions: `{user_id}/{id}`, lowercase
     * because the bucket RLS policy compares the first segment to Postgres'
     * lowercase `auth.uid()::text`.
     */
    fun storagePrefix(userId: String): String = "${userId.lowercase()}/${id.lowercase()}"
}
