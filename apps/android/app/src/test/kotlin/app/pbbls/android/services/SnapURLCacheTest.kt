package app.pbbls.android.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SnapURLCacheTest {
    /**
     * The cache's scope in production carries a SupervisorJob, so a failed
     * sign never cancels anything else. Mirror that here — handing the cache
     * the raw TestScope would fail the whole test when the provider throws,
     * even though every caller catches the awaited exception.
     */
    private fun TestScope.cacheScope(): CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    private class FakeProvider : SignedUrlProviding {
        var signCount = 0
        var failNext = false

        override suspend fun signedUrls(storagePrefix: String): SnapUrls {
            signCount += 1
            // Suspend so concurrent callers genuinely overlap under runTest's
            // virtual clock.
            delay(50)
            if (failNext) {
                failNext = false
                throw IOException("sign failed")
            }
            return SnapUrls(
                original = "https://x/original/$storagePrefix?n=$signCount",
                thumb = "https://x/thumb/$storagePrefix?n=$signCount",
            )
        }
    }

    @Test
    fun `concurrent requests for one path coalesce onto a single sign`() =
        runTest {
            val provider = FakeProvider()
            val cache = SnapURLCache(provider, cacheScope(), nowMillis = { 0L })

            val results = (1..5).map { async { cache.signedUrls("user/snap") } }.map { it.await() }

            assertEquals(1, provider.signCount)
            assertTrue(results.all { it == results.first() })
        }

    @Test
    fun `cache hits before expiry and re-signs after`() =
        runTest {
            var now = 0L
            val provider = FakeProvider()
            val cache = SnapURLCache(provider, cacheScope(), nowMillis = { now })

            cache.signedUrls("p")
            now = (SnapURLCache.TTL_SECONDS - SnapURLCache.SAFETY_MARGIN_SECONDS) * 1_000 - 1
            cache.signedUrls("p")
            assertEquals(1, provider.signCount)

            // The safety margin expires the entry a minute before the real TTL.
            now += 1
            cache.signedUrls("p")
            assertEquals(2, provider.signCount)
        }

    @Test
    fun `distinct paths sign independently`() =
        runTest {
            val provider = FakeProvider()
            val cache = SnapURLCache(provider, cacheScope(), nowMillis = { 0L })

            cache.signedUrls("a")
            cache.signedUrls("b")

            assertEquals(2, provider.signCount)
        }

    @Test
    fun `a failure reaches every coalesced caller and the next call retries`() =
        runTest {
            val provider = FakeProvider().apply { failNext = true }
            val cache = SnapURLCache(provider, cacheScope(), nowMillis = { 0L })

            val callers = (1..3).map { async { runCatching { cache.signedUrls("p") } } }
            val outcomes = callers.map { it.await() }

            assertEquals(1, provider.signCount)
            assertTrue(outcomes.all { it.isFailure })

            // The failed in-flight task was evicted — a fresh call re-signs.
            val retried = cache.signedUrls("p")
            assertEquals(2, provider.signCount)
            assertTrue(retried.thumb.contains("n=2"))
        }

    @Test
    fun `invalidateAll forces a re-sign`() =
        runTest {
            val provider = FakeProvider()
            val cache = SnapURLCache(provider, cacheScope(), nowMillis = { 0L })

            cache.signedUrls("p")
            cache.invalidateAll()
            advanceUntilIdle()
            cache.signedUrls("p")

            assertEquals(2, provider.signCount)
        }

    // resolveStorageUrl (the repository's pure URL normalizer)

    @Test
    fun `absolute signed urls pass through`() {
        assertEquals(
            "https://proj.supabase.co/storage/v1/object/sign/b/p?token=t",
            resolveStorageUrl(
                "https://proj.supabase.co",
                "https://proj.supabase.co/storage/v1/object/sign/b/p?token=t",
            ),
        )
    }

    @Test
    fun `relative signed urls resolve against the storage origin`() {
        assertEquals(
            "https://proj.supabase.co/storage/v1/object/sign/b/p?token=t",
            resolveStorageUrl("https://proj.supabase.co", "/object/sign/b/p?token=t"),
        )
        assertEquals(
            "https://proj.supabase.co/storage/v1/object/sign/b/p?token=t",
            resolveStorageUrl("https://proj.supabase.co/", "object/sign/b/p?token=t"),
        )
    }
}
