package app.pbbls.android.core.data

import app.pbbls.android.core.model.achievements.AchievementPayloads
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The real [AchievementsService] over a [MockEngine] client (#1307): the page is kept on the
 * device only for the user who asked for it, and read back only by that user; the check's rows
 * decode through the client exactly as the shipped `check()` decodes them.
 */
class AchievementsServiceTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val disk = FakeWrapDiskCache()
    private lateinit var client: SupabaseClient

    /** Answers a request whose path ends with the key, ahead of the dumps. */
    private val answers = ConcurrentHashMap<String, String>()

    /** Runs before each answer to a request whose path ends with the key. */
    private val beforeAnswer = ConcurrentHashMap<String, suspend () -> Unit>()

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
                        answers.entries.firstOrNull { path.endsWith(it.key) }?.value ?: when {
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
    fun `the page's age is its last read in this process or its kept copy, whichever is later`() =
        runTest {
            val (_, service) = signedIn("alice")
            assertNull("nothing kept, nothing read", service.keptAt(zone))
            disk.now = { Instant.parse("2026-10-30T08:00:00Z") }
            val before = Instant.now()
            service.page(zone)
            val age = service.keptAt(zone)!!
            assertTrue("the read in this process is the later", !age.isBefore(before))
            assertNull("another zone has its own age", service.keptAt(ZoneId.of("Asia/Tokyo")))
        }

    @Test
    fun `a kept copy from an earlier process gives the age alone`() =
        runTest {
            val (_, service) = signedIn("alice")
            val kept = Instant.parse("2026-10-30T08:00:00Z")
            disk.keptTimes[Triple("alice", zone.id, "achievements-page")] = kept
            assertEquals(kept, service.keptAt(zone))
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

    @Test
    fun `check decodes the dumped rows through the client's own serializer, as production does`() =
        runTest {
            val (_, service) = signedIn("alice")
            assertEquals(listOf(AchievementCheckResult(slug = "first-moment", karmaGranted = 0)), service.check())
        }

    @Test
    fun `a check that unlocks something makes the kept page stale until the next read`() =
        runTest {
            val (_, service) = signedIn("alice")
            service.page(zone)
            assertNotNull(service.keptAt(zone))
            service.fireCheck()
            var tries = 0
            while (service.keptAt(zone) != null && tries++ < 1_000) {
                runCurrent()
                Thread.sleep(5)
            }
            assertNull("the shelf reads on its next open", service.keptAt(zone))
            service.page(zone)
            assertNotNull("a read makes it fresh again", service.keptAt(zone))
        }

    @Test
    fun `a pulled ceremony makes the kept page stale, an empty answer does not`() =
        runTest {
            val (_, service) = signedIn("alice")
            service.page(zone)
            assertEquals(CeremonyPull.AlreadyPulled, service.pullCeremony(CompletionStack.SIX_FIRSTS, zone))
            assertNotNull(service.keptAt(zone))
            answers["/rpc/pull_ceremony"] = """[{"stack":"six_firsts","karma_granted":30}]"""
            assertEquals(CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 30), service.pullCeremony(CompletionStack.SIX_FIRSTS, zone))
            assertNull(service.keptAt(zone))
        }

    @Test
    fun `an older read landing after a newer one was kept is not kept`() =
        runTest {
            val (_, service) = signedIn("alice")
            val gate = CompletableDeferred<Unit>()
            val calls = AtomicInteger(0)
            val older = AchievementPayloads.read(AchievementPayloads.PAGE)
            val newer = AchievementPayloads.read(AchievementPayloads.PULLED)
            beforeAnswer["/rpc/get_achievements"] = {
                if (calls.incrementAndGet() == 1) {
                    gate.await()
                    answers["/rpc/get_achievements"] = older
                } else {
                    answers["/rpc/get_achievements"] = newer
                }
            }
            withContext(Dispatchers.Default) {
                val first = async { service.page(zone) }
                withTimeout(5_000) { while (calls.get() < 1) delay(5) }
                service.page(zone)
                gate.complete(Unit)
                first.await()
            }
            assertEquals(newer, disk.bodies[Triple("alice", zone.id, "achievements-page")])
        }

    @Test
    fun `only a read that left after the page went stale makes it fresh again`() =
        runTest {
            val (_, service) = signedIn("alice")
            service.page(zone)
            val gate = CompletableDeferred<Unit>()
            val waiting = AtomicInteger(0)
            beforeAnswer["/rpc/get_achievements"] = {
                if (waiting.incrementAndGet() == 1) gate.await()
            }
            answers["/rpc/pull_ceremony"] = """[{"stack":"six_firsts","karma_granted":30}]"""
            withContext(Dispatchers.Default) {
                val inFlight = async { service.page(zone) }
                withTimeout(5_000) { while (waiting.get() < 1) delay(5) }
                service.pullCeremony(CompletionStack.SIX_FIRSTS, zone)
                gate.complete(Unit)
                inFlight.await()
            }
            assertNull("the read in flight may not have seen the pull", service.keptAt(zone))
            service.page(zone)
            assertNotNull(service.keptAt(zone))
        }

    @Test
    fun `a mutation's check is in flight from the call until it has finished`() =
        runTest {
            val (_, service) = signedIn("alice")
            val seenDuring = AtomicInteger(-1)
            beforeAnswer["/rpc/check_achievements"] = { seenDuring.set(if (service.isFiringCheck) 1 else 0) }
            assertFalse(service.isFiringCheck)
            service.fireCheck()
            assertTrue("counted before the launch", service.isFiringCheck)
            var tries = 0
            while (service.isFiringCheck && tries++ < 1_000) {
                runCurrent()
                Thread.sleep(5)
            }
            assertEquals(1, seenDuring.get())
            assertFalse(service.isFiringCheck)
        }
}
