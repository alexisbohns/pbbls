package app.pbbls.android.services

import android.util.Log
import app.pbbls.android.AppEnvironment
import app.pbbls.android.features.pebblemedia.ProcessedImage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/**
 * The write/delete surface [app.pbbls.android.features.pebblemedia.SnapUploadCoordinator]
 * depends on — extracted (M38 rule: when a test needs the fake) so the
 * coordinator's state machine is JVM-testable. [PebbleSnapRepository] is the
 * live conformance.
 */
interface SnapWriteRepositing {
    /** Upload both renditions; throws if either fails. */
    suspend fun uploadProcessed(
        processed: ProcessedImage,
        snapId: String,
        userId: String,
    )

    /** Best-effort Storage cleanup by snap id — logs, never throws. */
    suspend fun deleteFiles(
        snapId: String,
        userId: String,
    )

    /** Best-effort Storage cleanup for callers already holding the `storage_path` prefix. */
    suspend fun deleteFiles(storagePrefix: String)

    /** `delete_pebble_media` RPC; returns the row's `storage_path` for Storage cleanup. Throws. */
    suspend fun deletePebbleMedia(snapId: String): String
}

/**
 * Storage + RPC operations for the private `pebbles-media` bucket — ports
 * `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`, now both
 * halves: M39's signed-URL read path and M42's upload/delete write path.
 * `storage_path` rows hold the prefix `{user_id}/{snap_id}`; the two renditions
 * live at `/original.jpg` and `/thumb.jpg` beneath it. The bucket has no
 * UPDATE policy — replace is delete + re-upload (design D4).
 */
class PebbleSnapRepository(
    private val supabase: SupabaseService,
) : SignedUrlProviding,
    SnapWriteRepositing {
    override suspend fun signedUrls(storagePrefix: String): SnapUrls {
        val originalPath = "$storagePrefix/original.jpg"
        val thumbPath = "$storagePrefix/thumb.jpg"
        val signed =
            supabase.client.storage
                .from(BUCKET_ID)
                .createSignedUrls(SIGNED_URL_TTL_SECONDS.seconds, originalPath, thumbPath)
        check(signed.size == 2) { "expected 2 signed urls, got ${signed.size}" }
        return SnapUrls(
            original = resolveStorageUrl(AppEnvironment.supabaseUrl, signed[0].signedURL),
            thumb = resolveStorageUrl(AppEnvironment.supabaseUrl, signed[1].signedURL),
        )
    }

    /** Upload original + thumb in parallel — returns when both succeed, throws if either fails. */
    override suspend fun uploadProcessed(
        processed: ProcessedImage,
        snapId: String,
        userId: String,
    ) {
        val prefix = storagePrefix(userId = userId, snapId = snapId)
        coroutineScope {
            val bucket = supabase.client.storage.from(BUCKET_ID)
            val original =
                async {
                    bucket.upload("$prefix/original.jpg", processed.original) {
                        contentType = ContentType.Image.JPEG
                    }
                }
            val thumb =
                async {
                    bucket.upload("$prefix/thumb.jpg", processed.thumb) {
                        contentType = ContentType.Image.JPEG
                    }
                }
            original.await()
            thumb.await()
        }
    }

    override suspend fun deleteFiles(
        snapId: String,
        userId: String,
    ) {
        deleteFiles(storagePrefix = storagePrefix(userId = userId, snapId = snapId))
    }

    override suspend fun deleteFiles(storagePrefix: String) {
        try {
            supabase.client.storage
                .from(BUCKET_ID)
                .delete(listOf("$storagePrefix/original.jpg", "$storagePrefix/thumb.jpg"))
        } catch (e: Exception) {
            // Best-effort by contract — the orphan-sweep follow-up catches residue.
            Log.e(TAG, "snap delete failed for prefix $storagePrefix", e)
        }
    }

    override suspend fun deletePebbleMedia(snapId: String): String =
        supabase.client.postgrest
            .rpc(
                "delete_pebble_media",
                buildJsonObject { put("p_snap_id", snapId) },
            ).decodeAs()

    companion object {
        private const val TAG = "snap-repo"
        private const val BUCKET_ID = "pebbles-media"

        /** Mirrors [SnapURLCache.TTL_SECONDS] — web and iOS use 1h too. */
        private const val SIGNED_URL_TTL_SECONDS = 3_600L

        /**
         * Canonical prefix `{user_id}/{snap_id}`, lowercased so the first
         * segment matches Postgres' lowercase `auth.uid()::text` in the
         * bucket RLS policy.
         */
        fun storagePrefix(
            userId: String,
            snapId: String,
        ): String = "${userId.lowercase()}/${snapId.lowercase()}"
    }
}

/**
 * Batch-signing endpoints return bucket-relative paths
 * (`/object/sign/…?token=…`) rather than absolute URLs — resolve against the
 * project's storage origin, passing absolute URLs through untouched. Pure and
 * unit-tested (the repository itself needs a live client).
 */
internal fun resolveStorageUrl(
    supabaseUrl: String,
    signedUrl: String,
): String =
    if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
        signedUrl
    } else {
        "${supabaseUrl.trimEnd('/')}/storage/v1/${signedUrl.trimStart('/')}"
    }
