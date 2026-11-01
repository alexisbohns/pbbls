package app.pbbls.android.features.profile

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.data.ProfileRow
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Profile's three-part load, its covers, and the save fold-back (#849).
 *
 * The load is the interesting half: the profile row and its glyph fail
 * independently on purpose, and before this ViewModel that behaviour lived in
 * nested `try`/`catch` inside a `LaunchedEffect`.
 */
class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val row =
        ProfileRow(
            displayName = "Pebbler",
            createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            glyphId = "glyph-1",
        )

    private fun viewModel(
        profile: FakeProfileService = FakeProfileService(profile = row),
        stats: FakePathStatsService = FakePathStatsService(),
        supabase: FakeSupabaseService = FakeSupabaseService(),
        achievements: FakeAchievementsService = FakeAchievementsService(),
    ) = ProfileViewModel(profile, stats, supabase, achievements)

    // MARK: - Load

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ProfileUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()

            val state = viewModel.uiState.value as ProfileUiState.Content
            assertEquals("Pebbler", state.profile?.displayName)
        }

    /** You lost its collections card (#1383, spec D14): the Collections tab is their home. */
    @Test
    fun `collections are not read`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            viewModel(profile)
            advanceUntilIdle()

            assertEquals(0, profile.loadCollectionsCount)
        }

    @Test
    fun `a failed profile fetch is the error state`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            profile.failNext = IOException("offline")
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            assertEquals(
                R.string.profile_load_error,
                (viewModel.uiState.value as ProfileUiState.Error).messageRes,
            )
        }

    /**
     * The glyph is decoration hanging off the row, so its failure must not take
     * the screen down — the banner renders without it.
     */
    @Test
    fun `a failed glyph fetch still shows the profile`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            profile.glyphStrokesFailure = IOException("offline")
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            val state = viewModel.uiState.value as ProfileUiState.Content
            assertEquals("Pebbler", state.profile?.displayName)
            assertNull(state.glyphStrokes)
        }

    @Test
    fun `retry recovers`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            profile.failNext = IOException("offline")
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ProfileUiState.Error)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is ProfileUiState.Content)
        }

    /** Rotation, as far as a JVM test carries it. */
    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, profile.loadProfileCount)
        }

    // MARK: - The achievements shelf (D18), loaded here since #852

    private val served = AchievementsFixture.page
    private val stale = AchievementsFixture.page.copy(shelfCount = 2)
    private val clock = Instant.parse("2026-10-30T12:00:00Z")

    private fun shelfOf(viewModel: ProfileViewModel) = viewModel.uiState.value as ProfileUiState.Content

    private fun record(
        slug: String,
        family: String,
    ) = AchievementRecord(id = slug, slug = slug, family = family, sortOrder = 0, karmaReward = 10, isActive = true, titleEn = "Carved")

    @Test
    fun `the shelf is the served six in the server's order and count, titled from the catalog by slug`() =
        runTest {
            val carved = record("first-glyph", "first_glyph")
            val achievements = FakeAchievementsService(served = served, catalog = listOf(carved))
            val viewModel = viewModel(achievements = achievements)
            advanceUntilIdle()

            val state = shelfOf(viewModel)
            assertEquals(
                listOf("glyph-sales-1", "glyph-count-10", "pebble-count-100", "pebble-count-50", "blue-pebble", "first-glyph"),
                state.shelf.map { it.slug },
            )
            assertEquals(carved, state.shelf.last())
            assertEquals("no catalog row: the served row", "pebble_count", state.shelf[2].family)
            assertEquals(100, state.shelf[2].threshold)
            assertEquals(13, state.shelfCount)
            assertEquals("a read grants first, as the page does", listOf("check", "page"), achievements.calls)
        }

    @Test
    fun `the kept copy shows at once, and a copy under fifteen minutes old is not read again`() =
        runTest {
            val achievements = FakeAchievementsService(served = served, kept = stale).apply { keptAt = clock.minusSeconds(14 * 60) }
            val viewModel = viewModel(achievements = achievements)
            viewModel.now = { clock }
            advanceUntilIdle()

            assertEquals(2, shelfOf(viewModel).shelfCount)
            assertEquals("no read, no grant", emptyList<String>(), achievements.calls)
        }

    @Test
    fun `back on You, the shelf shows the copy the page kept since, without a read`() =
        runTest {
            val achievements = FakeAchievementsService(served = served, kept = stale).apply { keptAt = clock.minusSeconds(60) }
            val viewModel = viewModel(achievements = achievements)
            viewModel.now = { clock }
            advanceUntilIdle()
            assertEquals(2, shelfOf(viewModel).shelfCount)

            // The page read a newer page and kept it; then You comes back.
            achievements.kept = served
            viewModel.onResumed()
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(13, shelfOf(viewModel).shelfCount)
            assertEquals(0, achievements.pageCalls)
        }

    @Test
    fun `a copy made stale (no age) is read again at once`() =
        runTest {
            val achievements = FakeAchievementsService(served = served, kept = stale).apply { keptAt = null }
            val viewModel = viewModel(achievements = achievements)
            viewModel.now = { clock }
            advanceUntilIdle()

            assertEquals(listOf("check", "page"), achievements.calls)
            assertEquals(13, shelfOf(viewModel).shelfCount)
        }

    @Test
    fun `while a mutation's check is in flight the shelf reads without granting`() =
        runTest {
            val achievements = FakeAchievementsService(served = served).apply { firingCheck = true }
            val viewModel = viewModel(achievements = achievements)
            advanceUntilIdle()

            assertEquals(listOf("page"), achievements.calls)
            assertEquals(13, shelfOf(viewModel).shelfCount)
        }

    @Test
    fun `a copy fifteen minutes old or more is read again`() =
        runTest {
            val achievements = FakeAchievementsService(served = served, kept = stale).apply { keptAt = clock.minusSeconds(15 * 60) }
            val viewModel = viewModel(achievements = achievements)
            viewModel.now = { clock }
            advanceUntilIdle()

            assertEquals(1, achievements.pageCalls)
            assertEquals(13, shelfOf(viewModel).shelfCount)
        }

    @Test
    fun `a failed read keeps the kept copy, and with nothing the shelf just invites`() =
        runTest {
            val kept = FakeAchievementsService(served = served, kept = stale).apply { failNextPage = IOException("offline") }
            val keptModel = viewModel(achievements = kept)
            advanceUntilIdle()
            assertEquals(2, shelfOf(keptModel).shelfCount)

            val none = FakeAchievementsService(served = served).apply { failNextPage = IOException("offline") }
            val noneModel = viewModel(achievements = none)
            advanceUntilIdle()
            assertEquals("Pebbler", shelfOf(noneModel).profile?.displayName)
            assertTrue(shelfOf(noneModel).shelf.isEmpty())
            assertNull(shelfOf(noneModel).shelfCount)
        }

    @Test
    fun `none earned is no count, and a failed catalog keeps the served rows`() =
        runTest {
            val empty = FakeAchievementsService(served = served.copy(shelf = emptyList(), shelfCount = 0))
            val emptyModel = viewModel(achievements = empty)
            advanceUntilIdle()
            assertNull(shelfOf(emptyModel).shelfCount)
            assertEquals("no badges, no catalog read", 0, empty.catalogLoadCount)

            val failing = FakeAchievementsService(served = served).apply { failNext = IOException("offline") }
            val failingModel = viewModel(achievements = failing)
            advanceUntilIdle()
            assertEquals(6, shelfOf(failingModel).shelf.size)
            assertEquals("first_glyph", shelfOf(failingModel).shelf.last().family)
        }

    @Test
    fun `the zone is read at each load`() =
        runTest {
            val achievements = FakeAchievementsService(served = served)
            val viewModel = viewModel(achievements = achievements)
            viewModel.zone = { ZoneId.of("Asia/Tokyo") }
            advanceUntilIdle()
            assertEquals(setOf(ZoneId.of("Asia/Tokyo")), achievements.zones.toSet())
        }

    @Test
    fun `stats load alongside the profile`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()

            assertEquals(1, stats.loadCount)
            assertEquals(42, (viewModel.uiState.value as ProfileUiState.Content).karma)
        }

    /** The glyph counts (#1386) ride the stats singleton; null until served, then live. */
    @Test
    fun `glyph counts follow the stats`() =
        runTest {
            val stats = FakePathStatsService()
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()
            val before = viewModel.uiState.value as ProfileUiState.Content
            assertNull(before.glyphsCarved)
            assertNull(before.glyphsOwned)

            stats.glyphsCarved = 12
            stats.glyphsOwned = 5
            // The ViewModel observes through `snapshotFlow`; outside a composition a write is only
            // seen once the global snapshot is applied.
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            val after = viewModel.uiState.value as ProfileUiState.Content
            assertEquals(12, after.glyphsCarved)
            assertEquals(5, after.glyphsOwned)
        }

    // MARK: - Returning from a pushed screen

    /**
     * `refresh()` has carried the KDoc "for returning from a pushed screen"
     * since #893 and was never called for it: the ViewModel is scoped to the
     * `NavBackStackEntry`, which survives the trip to Souls, Collections,
     * Glyphs, Connections, Lab or Achievements. So renaming a collection in its
     * own list left Profile's carousel showing the old name. (Deferred from
     * PR #896.)
     */
    @Test
    fun `returning to Profile re-reads it`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            val loadsAfterInit = profile.loadProfileCount

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(loadsAfterInit, profile.loadProfileCount)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(loadsAfterInit + 1, profile.loadProfileCount)
        }

    /** A glyph carved or bought on the way out shows in the counts on return. */
    @Test
    fun `returning to You refreshes the stats`() =
        runTest {
            val stats = FakePathStatsService()
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(0, stats.refreshCount)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, stats.refreshCount)
        }

    /** The refresh is silent — it must not blank the page to a spinner. */
    @Test
    fun `returning does not flash the spinner`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.onResumed()

            viewModel.onResumed()
            assertTrue(viewModel.uiState.value is ProfileUiState.Content)
        }
}
