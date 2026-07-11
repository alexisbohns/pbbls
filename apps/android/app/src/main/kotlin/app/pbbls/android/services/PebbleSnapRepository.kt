package app.pbbls.android.services

import app.pbbls.android.AppEnvironment
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds

/**
 * Signs read URLs for snap renditions in the private `pebbles-media` bucket —
 * the read path of `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`.
 * `storage_path` rows hold the prefix `{user_id}/{snap_id}`; the two renditions
 * live at `/original.jpg` and `/thumb.jpg` beneath it, signed in one call.
 */
class PebbleSnapRepository(
    private val supabase: SupabaseService,
) : SignedUrlProviding {
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

    companion object {
        private const val BUCKET_ID = "pebbles-media"

        /** Mirrors [SnapURLCache.TTL_SECONDS] — web and iOS use 1h too. */
        private const val SIGNED_URL_TTL_SECONDS = 3_600L
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
