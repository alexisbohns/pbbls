package app.pbbls.android.core.data

import app.pbbls.android.core.model.achievements.AchievementPayloads
import app.pbbls.android.testing.FakeWrapDiskCache
import app.pbbls.android.testing.testSession
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The real [AchievementsService] over a [MockEngine] client (#1307): the page is kept on the
 * device only for the user who asked for it, and read back only by that user.
 */
class AchievementsServiceTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val disk = FakeWrapDiskCache()
    private lateinit var client: SupabaseClient

    /** Runs before each answer to a request whose path ends with the key. */
    private val beforeAnswer = mutableMapOf<String, suspend () -> Unit>()

    @OptIn(SupabaseInternal::class)
    private fun newClient(): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    beforeAnswer.entries
                        .firstOrNull { path.endsWith(it.key) }
                        ?.value
                        ?.invoke()
                    val body =
                        when {
                            path.endsWith("/rpc/get_achievements") -> AchievementPayloads.read(AchievementPayloads.PAGE)
                            path.endsWith("/rpc/check_achievements") -> AchievementPayloads.read(AchievementPayloads.CHECK)
                            else -> "[]"
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSetupPlatform = false
            }
            install(Postgrest)
        }

    /** A signed-in [SupabaseService] whose session follows the client's, as in the app. */
    private suspend fun TestScope.signedIn(userId: String?): Pair<SupabaseService, AchievementsService> {
        client = newClient()
        val supabase = SupabaseService(client, backgroundScope)
        // Unconfined: the collector runs as the status changes, wherever that happens.
        backgroundScope.launch(Dispatchers.Unconfined) { supabase.start() }
        if (userId != null) signInAs(supabase, userId)
        return supabase to AchievementsService(supabase, AchievementNotificationService(), backgroundScope, disk)
    }

    private suspend fun signInAs(
        supabase: SupabaseService,
        userId: String,
    ) {
        client.auth.importSession(testSession(userId), autoRefresh = false)
        // Real time: the status travels through the client's own coroutines.
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (supabase.session?.user?.id != userId) delay(5)
            }
        }
    }

    @Test
    fun `a read keeps the page for the user who asked, and reads it back for them`() =
        runTest {
            val (_, service) = signedIn("alice")
            val page = service.page(zone)
            assertEquals(setOf(Triple("alice", zone.id, "achievements-page")), disk.bodies.keys)
            assertEquals(page, service.cachedPage(zone))
        }

    @Test
    fun `a session that swaps while the read is in flight keeps nothing`() =
        runTest {
            val (supabase, service) = signedIn("alice")
            beforeAnswer["/rpc/get_achievements"] = { signInAs(supabase, "bob") }
            assertNotNull(service.page(zone))
            assertTrue(disk.bodies.isEmpty())
            assertNull(service.cachedPage(zone))
        }

    @Test
    fun `a read without a session keeps nothing, and there is no copy to read`() =
        runTest {
            val (_, service) = signedIn(null)
            assertNotNull(service.page(zone))
            assertTrue(disk.bodies.isEmpty())
            assertNull(service.cachedPage(zone))
        }
}
