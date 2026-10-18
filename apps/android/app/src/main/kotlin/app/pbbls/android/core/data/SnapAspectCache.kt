package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.di.IoApplicationScope
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "snap-aspect"

/** Reads a photo's pixel bounds (width, height) without keeping it. */
fun interface SnapBoundsDecoder {
    suspend fun bounds(url: String): Pair<Int, Int>?
}

/**
 * Width / height of a snap's photo, learned once per session from its thumb
 * (Spine spec D3, the web's D14).
 *
 * `snaps` stores no dimensions, so a Spine card starts square and eases to the
 * photo's ratio once this has probed the thumb's bounds. By `storage_path`
 * prefix:
 *
 * - a decoded ratio is kept for the process;
 * - a failed or timed-out probe is kept too — the card keeps its square field
 *   and nothing retries within the session (a retry would re-animate the card
 *   every time the row scrolled back in);
 * - concurrent requests for one prefix share a single decode, on a scope the
 *   service owns, so a caller leaving the composition cancels only its own
 *   `await`, never a sibling's probe.
 *
 * The scope is the `@IoApplicationScope` singleton rather than one built here,
 * because `DispatchersModule` is the one place a long-lived scope is built.
 */
@Singleton
class SnapAspectCache
    @Inject
    constructor(
        private val urls: SnapURLCache,
        @IoApplicationScope private val scope: CoroutineScope,
        private val decoder: SnapBoundsDecoder,
    ) {
        private val ratios = ConcurrentHashMap<String, Float>()
        private val failed = ConcurrentHashMap.newKeySet<String>()
        private val inflight = ConcurrentHashMap<String, Deferred<Float?>>()

        /** The ratio if already known this session, else null. No I/O. */
        fun cached(storagePrefix: String): Float? = ratios[storagePrefix]

        /**
         * The ratio, decoding the thumb once; null when the probe failed or timed
         * out, and that failure sticks for the session.
         */
        suspend fun ratio(storagePrefix: String): Float? {
            ratios[storagePrefix]?.let { return it }
            if (storagePrefix in failed) return null
            // computeIfAbsent, not getOrPut: the latter may build a second async
            // for a racing caller and drop it, already started.
            val task =
                inflight.computeIfAbsent(storagePrefix) {
                    scope.async { probe(storagePrefix) }
                }
            return task.await()
        }

        private suspend fun probe(storagePrefix: String): Float? {
            val ratio =
                runCatchingCancellable {
                    withTimeoutOrNull(TIMEOUT_MS) {
                        val thumb = urls.signedUrls(storagePrefix).thumb
                        decoder.bounds(thumb)?.let { (width, height) ->
                            if (width > 0 && height > 0) width.toFloat() / height else null
                        }
                    }
                }.onFailure { Log.w(TAG, "snap ratio probe failed for $storagePrefix", it) }
                    .getOrNull()
            if (ratio == null) {
                Log.w(TAG, "snap ratio unknown for $storagePrefix; card stays square this session")
                failed += storagePrefix
            } else {
                ratios[storagePrefix] = ratio
            }
            inflight.remove(storagePrefix)
            return ratio
        }

        companion object {
            /** Sign + fetch + decode budget before the card settles on square. */
            internal const val TIMEOUT_MS = 4_000L
        }
    }

/**
 * The production [SnapBoundsDecoder]: Coil's singleton loader (the one
 * `AsyncImage` uses, so the thumb this fetches is the thumb the card then
 * shows from cache) at its original size, software-backed so the bitmap's
 * bounds are readable. Returns null on anything but a success.
 */
class CoilSnapBoundsDecoder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SnapBoundsDecoder {
        override suspend fun bounds(url: String): Pair<Int, Int>? {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size.ORIGINAL)
                    .allowHardware(false)
                    .build()
            val result = SingletonImageLoader.get(context).execute(request)
            val image = (result as? SuccessResult)?.image ?: return null
            return image.width to image.height
        }
    }
