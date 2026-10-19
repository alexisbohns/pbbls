package app.pbbls.android.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapAspectCacheTest {
    /**
     * Production hands the cache the `@IoApplicationScope` (a SupervisorJob on
     * IO). Mirror that: a probe that fails must not fail the test scope, and a
     * probe still suspended at the end of a test must not hang `runTest`.
     */
    private fun TestScope.ioScope(): CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    private class FakeProvider : SignedUrlProviding {
        override suspend fun signedUrls(storagePrefix: String) =
            SnapUrls(
                original = "https://x/original/$storagePrefix",
                thumb = "https://x/thumb/$storagePrefix",
            )
    }

    private class FakeDecoder(
        private val answer: suspend (String) -> Pair<Int, Int>?,
    ) : SnapBoundsDecoder {
        var calls = 0
        val urls = mutableListOf<String>()

        override suspend fun bounds(url: String): Pair<Int, Int>? {
            calls += 1
            urls += url
            // Suspend so concurrent callers genuinely overlap under the
            // virtual clock.
            delay(10)
            return answer(url)
        }
    }

    private fun TestScope.cache(decoder: SnapBoundsDecoder): SnapAspectCache =
        SnapAspectCache(
            urls = SnapURLCache(FakeProvider(), ioScope(), nowMillis = { 0L }),
            scope = ioScope(),
            decoder = decoder,
        )

    @Test
    fun `decodes the thumb once and caches the ratio by prefix`() =
        runTest {
            val decoder = FakeDecoder { 1200 to 800 }
            val cache = cache(decoder)

            assertNull(cache.cached("u/s1"))
            assertEquals(1.5f, cache.ratio("u/s1"))
            assertEquals(1.5f, cache.cached("u/s1"))
            assertEquals(1.5f, cache.ratio("u/s1"))

            assertEquals(1, decoder.calls)
            assertEquals(listOf("https://x/thumb/u/s1"), decoder.urls)
        }

    @Test
    fun `two concurrent requests for one prefix share a single decode`() =
        runTest {
            val decoder = FakeDecoder { 1200 to 800 }
            val cache = cache(decoder)

            val first = async { cache.ratio("u/s1") }
            val second = async { cache.ratio("u/s1") }
            advanceUntilIdle()

            assertEquals(1.5f, first.await())
            assertEquals(1.5f, second.await())
            assertEquals(1, decoder.calls)
        }

    @Test
    fun `a null decode is a failure that sticks for the session`() =
        runTest {
            val decoder = FakeDecoder { null }
            val cache = cache(decoder)

            assertNull(cache.ratio("u/s1"))
            assertNull(cache.ratio("u/s1"))

            assertEquals(1, decoder.calls)
            assertNull(cache.cached("u/s1"))
        }

    @Test
    fun `a decode that never answers times out to null and does not retry`() =
        runTest {
            val decoder = FakeDecoder { awaitCancellation() }
            val cache = cache(decoder)

            val probe = async { cache.ratio("u/s1") }
            advanceUntilIdle()

            assertNull(probe.await())
            assertNull(cache.ratio("u/s1"))
            assertEquals(1, decoder.calls)
            assertNull(cache.cached("u/s1"))
        }

    @Test
    fun `empty bounds are a failure`() =
        runTest {
            val decoder = FakeDecoder { 0 to 0 }
            val cache = cache(decoder)

            assertNull(cache.ratio("u/s1"))
            assertNull(cache.cached("u/s1"))
            assertEquals(1, decoder.calls)
        }

    @Test
    fun `prefixes are cached independently`() =
        runTest {
            val decoder =
                FakeDecoder { url ->
                    if (url.endsWith("s1")) 1200 to 800 else 900 to 1200
                }
            val cache = cache(decoder)

            assertEquals(1.5f, cache.ratio("u/s1"))
            assertEquals(0.75f, cache.ratio("u/s2"))
            assertEquals(2, decoder.calls)
        }
}
