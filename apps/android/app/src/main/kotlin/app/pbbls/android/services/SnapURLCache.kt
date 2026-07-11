package app.pbbls.android.services

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Signed URLs for one snap's two renditions. */
data class SnapUrls(
    val original: String,
    val thumb: String,
)

/**
 * The seam [SnapURLCache] signs through — a live [PebbleSnapRepository] in
 * production, a fake in tests (extracted because a test needs it, per the
 * CLAUDE.md YAGNI rule).
 */
interface SignedUrlProviding {
    suspend fun signedUrls(storagePrefix: String): SnapUrls
}

/**
 * Per-session cache of signed snap URLs keyed by `storage_path` prefix —
 * ports `apps/ios/Pebbles/Services/SnapURLCache.swift`:
 *
 * - entries expire [SAFETY_MARGIN_SECONDS] before the signed TTL so a
 *   handed-out URL never dies mid-download;
 * - concurrent requests for one path coalesce onto a single in-flight sign
 *   (a failure propagates to every coalesced caller, and the next call
 *   retries);
 * - [invalidateAll] clears the cache on sign-out (`RootScreen`).
 *
 * Deliberately log-free (JVM-tested); the calling composable logs failures.
 */
class SnapURLCache internal constructor(
    private val provider: SignedUrlProviding,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
) {
    constructor(supabase: SupabaseService) : this(
        provider = PebbleSnapRepository(supabase),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        nowMillis = System::currentTimeMillis,
    )

    private data class Entry(
        val urls: SnapUrls,
        val expiresAtMillis: Long,
    )

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Entry>()
    private val inflight = mutableMapOf<String, Deferred<SnapUrls>>()

    suspend fun signedUrls(storagePath: String): SnapUrls {
        val task =
            mutex.withLock {
                cache[storagePath]
                    ?.takeIf { nowMillis() < it.expiresAtMillis }
                    ?.let { return it.urls }
                inflight.getOrPut(storagePath) {
                    scope.async {
                        try {
                            val urls = provider.signedUrls(storagePath)
                            mutex.withLock {
                                cache[storagePath] =
                                    Entry(
                                        urls = urls,
                                        expiresAtMillis =
                                            nowMillis() +
                                                (TTL_SECONDS - SAFETY_MARGIN_SECONDS) * 1_000,
                                    )
                            }
                            urls
                        } finally {
                            // Inside a different coroutine than the outer lock
                            // holder — Mutex is not reentrant, but these never
                            // nest within one coroutine.
                            mutex.withLock { inflight.remove(storagePath) }
                        }
                    }
                }
            }
        return task.await()
    }

    fun invalidateAll() {
        scope.launch { mutex.withLock { cache.clear() } }
    }

    companion object {
        /** Mirrors the web/iOS signed-URL TTL for `pebbles-media`. */
        internal const val TTL_SECONDS = 3_600L

        /** Cache entries die early so a handed-out URL outlives its use. */
        internal const val SAFETY_MARGIN_SECONDS = 60L
    }
}

/**
 * CompositionLocal for [SnapURLCache] — nullable, unlike the other service
 * locals: screenshot previews render rows without providing it (the thumb
 * shows its transparent placeholder), so no fake service plumbing is needed.
 */
val LocalSnapURLCache = staticCompositionLocalOf<SnapURLCache?> { null }
