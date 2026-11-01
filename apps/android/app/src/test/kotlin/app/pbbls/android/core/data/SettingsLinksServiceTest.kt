package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPreferencesStore
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * The real [SettingsLinksService] over a [MockEngine] client and an in-memory DataStore (#1489,
 * iOS `SettingsLinksService`): the kept payload, else the bundled seed; a refresh keeps what it
 * reads and a failed one changes nothing. Every body is a database payload, verbatim.
 */
class SettingsLinksServiceTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    /** The `settings_links` answer; null answers 500. */
    private var answer: String? = null

    /** The decoded query string of the last `settings_links` read. */
    private var query: String? = null

    private val store = InMemoryPreferencesStore()

    @OptIn(SupabaseInternal::class)
    private fun newClient(): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine { request ->
                    query = URLDecoder.decode(request.url.encodedQuery, Charsets.UTF_8)
                    val body = answer?.takeIf { request.url.encodedPath.endsWith("/settings_links") }
                    if (body == null) {
                        respond(
                            """{"message":"boom"}""",
                            HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSetupPlatform = false
            }
            install(Postgrest)
        }

    private fun TestScope.service(): SettingsLinksService =
        SettingsLinksService(
            supabase = SupabaseService(newClient(), backgroundScope),
            store = store,
            io = StandardTestDispatcher(testScheduler),
            builtIn = { fixture("settings-links.json") },
        )

    @Test
    fun `with nothing kept it shows the built-in list`() =
        runTest {
            assertEquals(listOf("credits", "terms", "privacy", "legal-notice", "support"), service().current().map { it.key })
        }

    @Test
    fun `a refresh reads the published rows in order and keeps them`() =
        runTest {
            answer = fixture("settings-links-edge.json")
            assertEquals(listOf("credits"), service().refresh().map { it.key })
            val sent = requireNotNull(query)
            assertTrue(sent, sent.contains("select=key,section,sort_order,title_en,title_fr,url"))
            assertTrue(sent, sent.contains("published=eq.true"))
            assertTrue(sent, sent.contains("order=section.asc.nullslast,sort_order.asc.nullslast"))
            // A new service (the next launch) starts from the kept payload, not the seed.
            answer = null
            assertEquals(listOf("credits"), service().current().map { it.key })
        }

    @Test
    fun `a failed refresh keeps what was kept`() =
        runTest {
            answer = fixture("settings-links-edge.json")
            service().refresh()
            answer = null
            assertEquals(listOf("credits"), service().refresh().map { it.key })
        }

    @Test
    fun `a failed first refresh falls back to the built-in list`() =
        runTest {
            assertEquals(5, service().refresh().size)
        }
}
