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
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * The real [HomeService] over a [MockEngine] client (#1383): the RPCs leave with iOS's params, a
 * reveal the server did not record is a failure, and the name read never fails the greeting.
 */
class HomeServiceTest {
    private val zone = ZoneId.of("Europe/Paris")

    /** Answers a request whose path ends with the key; anything else answers 500. */
    private val answers = ConcurrentHashMap<String, String>()

    /** The JSON body of each RPC call, by path. */
    private val bodies = ConcurrentHashMap<String, JsonObject>()

    /** The query string of each table read, by path. */
    private val queries = ConcurrentHashMap<String, String>()

    @OptIn(SupabaseInternal::class)
    private fun newClient(): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    if (path.contains("/rpc/")) {
                        bodies[path] = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    } else {
                        queries[path] = request.url.encodedQuery
                    }
                    val body = answers.entries.firstOrNull { path.endsWith(it.key) }?.value
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

    private fun TestScope.service(): HomeService = HomeService(SupabaseService(newClient(), backgroundScope))

    @Test
    fun `home_wraps leaves with the zone and answers newest first`() =
        runTest {
            val cover = """{"days":[],"souls_in_order":[],"inks":[],"papers":[]}"""
            answers["/rpc/home_wraps"] =
                """{"months":[{"month":"2026-08-01","count":1,"revealed_at":null,"cover":$cover},""" +
                """{"month":"2026-09-01","count":2,"revealed_at":null,"cover":$cover}]}"""
            val months = service().wraps(zone)
            assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 8)), months.map { it.month })
            assertEquals("Europe/Paris", bodies.getValue("/rest/v1/rpc/home_wraps")["p_tz"]?.jsonPrimitive?.content)
        }

    @Test
    fun `reveal_wrap leaves with the month's first day and answers when it was recorded`() =
        runTest {
            answers["/rpc/reveal_wrap"] = """{"month":"2026-09-01","revealed_at":"2026-10-26T22:24:21.123456+00:00"}"""
            val at = service().reveal(YearMonth.of(2026, 9), zone)
            assertEquals(Instant.parse("2026-10-26T22:24:21.123456Z"), at)
            val sent = bodies.getValue("/rest/v1/rpc/reveal_wrap")
            assertEquals("2026-09-01", sent["p_month"]?.jsonPrimitive?.content)
            assertEquals("Europe/Paris", sent["p_tz"]?.jsonPrimitive?.content)
        }

    @Test
    fun `a reveal the server did not record is a failure, read as Unknown`() =
        runTest {
            answers["/rpc/reveal_wrap"] = """{"month":"2026-09-01","revealed_at":null}"""
            try {
                service().reveal(YearMonth.of(2026, 9), zone)
                fail("a null revealed_at must throw")
            } catch (e: IllegalStateException) {
                assertTrue(e.toDataError() is DataError.Unknown)
            }
        }

    @Test
    fun `guides read the published rows by sort order and drop a bad one`() =
        runTest {
            answers["/guides"] =
                """[{"id":"not-a-uuid","slug":"x","sort_order":1,"title_en":"X","summary_en":"x","body_md_en":"x"},""" +
                """{"id":"6f0e2c3e-5b57-4b9d-9a3d-6f7f4b8c1d2e","slug":"stones","sort_order":20,"title_en":"About the stones","summary_en":"s","body_md_en":"b"}]"""
            val guides = service().guides()
            assertEquals(listOf("stones"), guides.map { it.slug })
            val query = queries.getValue("/rest/v1/guides")
            assertTrue(query, query.contains("published=eq.true"))
            assertTrue(query, query.contains("order=sort_order.asc"))
        }

    @Test
    fun `the display name is trimmed`() =
        runTest {
            answers["/profiles"] = """[{"display_name":"  Alexis "}]"""
            assertEquals("Alexis", service().displayName())
        }

    @Test
    fun `an unset or unreadable display name greets without a name`() =
        runTest {
            answers["/profiles"] = """[{"display_name":null}]"""
            assertEquals("", service().displayName())
            answers.clear()
            assertEquals("", service().displayName())
        }
}
