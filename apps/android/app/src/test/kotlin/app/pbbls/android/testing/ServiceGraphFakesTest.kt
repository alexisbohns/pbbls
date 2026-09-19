package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseServicing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * that predate this issue cover pure helpers only: no screen's load, error or
 * save path was reachable.
 *
 * Two changes made it reachable. `di/SupabaseModule` now owns
 * `createSupabaseClient`, so `AppEnvironment` is read in exactly one place a
 * test never calls; and the five seams below are interfaces, so a fake stands in
 * without producing a client at all.
 */
class ServiceGraphFakesTest {
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

    @Test
    fun `the default graph supplies every seam as a fake`() {
        val graph = FakeServiceGraph()

        // Each field is typed as its interface, and nothing in the construction
        // above touched AppEnvironment or a SupabaseClient.
        val seams: List<Any> =
            listOf<Any>(
                graph.supabase as SupabaseServicing,
                graph.pathService as PathServicing,
                graph.profileService as ProfileServicing,
                graph.pebbleWrite as PebbleWriteServicing,
                graph.referenceData as ReferenceDataServicing,
            )

        assertEquals(5, seams.size)
        assertTrue(graph.supabase is FakeSupabaseService)
        assertTrue(graph.pathService is FakePathService)
        assertTrue(graph.profileService is FakeProfileService)
        assertTrue(graph.pebbleWrite is FakePebbleWriteService)
        assertTrue(graph.referenceData is FakeReferenceDataService)
    }

    @Test
    fun `a named seam drives the success path`() =
        runTest {
            val fixture = listOf(pebble("p1", "First light"), pebble("p2", "Second wind"))
            val graph = FakeServiceGraph(pathService = FakePathService(pebbles = fixture))

            val loaded = graph.pathService.loadPathPebbles()

            assertEquals(fixture, loaded)
            assertEquals(listOf("First light", "Second wind"), loaded.map { it.name })
            assertEquals(1, (graph.pathService as FakePathService).loadCount)
        }

    @Test
    fun `an armed failure throws once, then clears so the retry succeeds`() =
        runTest {
            val pathService = FakePathService(pebbles = listOf(pebble("p1", "First light")))
            val graph = FakeServiceGraph(pathService = pathService)
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
