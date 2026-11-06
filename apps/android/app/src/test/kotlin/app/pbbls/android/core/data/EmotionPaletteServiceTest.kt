package app.pbbls.android.core.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The real [EmotionPaletteService] over a [MockEngine]: a failed load is visible and a retry recovers (D9). */
class EmotionPaletteServiceTest {
    private val row =
        """
        [{"id":"emotion-calm","slug":"calm","name":"Calm","emoji":"🙂","category_id":"cat-1",
          "category_slug":"peace","category_name":"Peace","primary_color":"#6B8F71FF",
          "secondary_color":"#A3C4A8FF","light_color":"#E4F0E6FF","surface_color":"#F3F8F4FF",
          "dark_color":"#2F4A34FF","shaded_color":"#4F6B54FF"}]
        """.trimIndent()

    private val requests = AtomicInteger()

    @OptIn(SupabaseInternal::class)
    private fun client(respond: (Int) -> Pair<HttpStatusCode, String>): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine {
                    val (status, body) = respond(requests.incrementAndGet())
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSetupPlatform = false
            }
            install(Postgrest)
        }

    private fun TestScope.service(respond: (Int) -> Pair<HttpStatusCode, String>) =
        EmotionPaletteService(SupabaseService(client(respond), backgroundScope))

    @Test
    fun `a failed load says so and leaves the cache empty`() =
        runTest {
            val service = service { HttpStatusCode.InternalServerError to """{"message":"boom"}""" }
            service.load()
            assertTrue(service.loadFailed)
            assertFalse(service.hasLoaded)
            assertTrue(service.byEmotionId.isEmpty())
        }

    @Test
    fun `a retry after a failure loads and clears the failure`() =
        runTest {
            val service =
                service { n -> if (n == 1) HttpStatusCode.InternalServerError to """{"message":"boom"}""" else HttpStatusCode.OK to row }
            service.load()
            assertTrue(service.loadFailed)
            service.load()
            assertFalse(service.loadFailed)
            assertTrue(service.hasLoaded)
            assertEquals(setOf("emotion-calm"), service.byEmotionId.keys)
        }

    @Test
    fun `a retry that fails again stays failed and keeps what loaded before`() =
        runTest {
            val service =
                service { n -> if (n == 1) HttpStatusCode.OK to row else HttpStatusCode.InternalServerError to """{"message":"boom"}""" }
            service.load()
            service.load()
            assertTrue(service.loadFailed)
            assertEquals(setOf("emotion-calm"), service.byEmotionId.keys)
        }

    @Test
    fun `cancellation travels instead of reading as a failure`() =
        runTest {
            val service = service { throw CancellationException("cancelled") }
            try {
                service.load()
                fail("cancellation was swallowed")
            } catch (e: CancellationException) {
                assertFalse(service.loadFailed)
            }
        }
}
