package app.pbbls.android.testing

import app.pbbls.android.core.data.PathService
import app.pbbls.android.core.data.PebbleWriteService
import app.pbbls.android.core.data.ProfileService
import app.pbbls.android.core.data.ReferenceDataService
import app.pbbls.android.core.data.SupabaseService
import app.pbbls.android.core.model.Pebble
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/**
 * Acceptance criterion 3 of #848: the service graph is fakeable.
 *
 * **Before #848 this test could not exist.** Every service's root,
 * `SupabaseService`, built a live supabase-kt client from `AppEnvironment` in
 * its own initializer, and `AppEnvironment` throws `IllegalStateException` on a
 * blank `BuildConfig.SUPABASE_URL` — the fork-PR case, and the case on any
 * machine without `secrets.properties`. Constructing the graph in a JVM test
 * therefore threw before a single assertion ran, which is why the 84 test files
 * that predate this issue covered pure helpers and the two seams that already
 * had interfaces (`SnapURLCacheTest` and `SnapUploadCoordinatorTest` drive real
 * service-layer classes through `SignedUrlProviding` / `SnapWriteRepositing`);
 * no screen's load, error or save path was reachable.
 *
 * Two changes made it reachable. `di/SupabaseModule` now owns
 * `createSupabaseClient`, so `AppEnvironment` is read in exactly one place a
 * test never calls; and the five seams below are interfaces, so a fake stands in
 * without producing a client at all.
 */
class ServiceFakesTest {
    private fun pebble(
        id: String,
        name: String,
    ): Pebble =
        Pebble(
            id = id,
            name = name,
            happenedAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
            createdAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
            intensity = 2,
            positiveness = 1,
        )

    /**
     * The regression this whole stack exists to prevent.
     *
     * Before #848, `SupabaseService` built its supabase-kt client from
     * `AppEnvironment` in its initializer, which throws on a blank
     * `BuildConfig.SUPABASE_URL`. Constructing any service therefore needed real
     * secrets, which is why no screen test existed. Now the client is a
     * constructor parameter ([app.pbbls.android.di.SupabaseModule] is the only
     * `AppEnvironment` reader), so a test can build its own and wire the graph.
     *
     * **Caveat, stated honestly:** on a machine that has `secrets.properties`, a
     * reintroduced `AppEnvironment` read would not throw and this test would stay
     * green. It bites on CI and on fork PRs — which is precisely the case the
     * issue is about.
     *
     * The `@OptIn` is for `autoSetupPlatform`, which is `@SupabaseInternal` and
     * whose own KDoc says "For testing." — this is that case. Without it the Auth
     * plugin launches an Android platform-setup coroutine that crashes
     * asynchronously on the JVM, and since kotlinx-coroutines-test routes every
     * unhandled coroutine exception to the framework, a LATER test fails at
     * random (measured before the flag: 2 runs in 3).
     */
    @OptIn(SupabaseInternal::class)
    @Test
    fun servicesConstructWithoutSecrets() {
        // A syntactically valid but entirely fake project. createSupabaseClient
        // performs no network I/O (see SupabaseModule's KDoc), so this costs
        // nothing and never leaves the JVM.
        val client =
            createSupabaseClient(
                supabaseUrl = "https://example.supabase.co",
                supabaseKey = "not-a-real-key",
            ) {
                // Four settings, all about the throwaway client's own plumbing —
                // none of them touch what this test asserts, which is that the
                // services construct.
                //
                // The default session manager and code-verifier cache persist
                // through multiplatform-settings, which needs an Android Context
                // and throws on the plain JVM; the in-memory pair is the library's
                // own documented remedy. `autoLoadFromStorage` and
                // `autoSetupPlatform` make `AuthImpl.init` launch background
                // coroutines (a storage read, an Android platform setup) on
                // construction — on the JVM those fail, and because
                // kotlinx-coroutines-test routes every unhandled coroutine
                // exception to the test framework, they surface as a spurious
                // failure in whichever `runTest` runs next.
                install(Auth) {
                    sessionManager = MemorySessionManager()
                    codeVerifierCache = MemoryCodeVerifierCache()
                    autoLoadFromStorage = false
                    autoSetupPlatform = false
                }
                install(Postgrest)
            }
        val supabase = SupabaseService(client, TestScope())

        // Each of these used to be unconstructible in a JVM test.
        PathService(supabase)
        ProfileService(supabase)
        PebbleWriteService(supabase)
        ReferenceDataService(supabase)
    }

    @Test
    fun `a named seam drives the success path`() =
        runTest {
            val fixture = listOf(pebble("p1", "First light"), pebble("p2", "Second wind"))
            val graph = FakeServices(pathService = FakePathService(pebbles = fixture))

            val loaded = graph.pathService.loadPathPebbles()

            assertEquals(fixture, loaded)
            assertEquals(listOf("First light", "Second wind"), loaded.map { it.name })
            assertEquals(1, (graph.pathService as FakePathService).loadCount)
        }

    @Test
    fun `an armed failure throws once, then clears so the retry succeeds`() =
        runTest {
            val pathService = FakePathService(pebbles = listOf(pebble("p1", "First light")))
            val graph = FakeServices(pathService = pathService)
            pathService.failNext = IOException("network down")

            val thrown =
                try {
                    graph.pathService.loadPathPebbles()
                    null
                } catch (e: IOException) {
                    e
                }

            assertEquals("network down", thrown?.message)
            // Cleared by the throw — this is what lets a test drive retry
            // without rebuilding the fake.
            assertEquals(null, pathService.failNext)

            val retried = graph.pathService.loadPathPebbles()

            assertEquals(listOf("First light"), retried.map { it.name })
            assertEquals(2, pathService.loadCount)
        }
}
