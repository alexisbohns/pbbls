package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSupabaseService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/**
 * Profile's three-part load, its covers, and the save fold-back (#849).
 *
 * The load is the interesting half: the profile row, its glyph and the
 * collections fail independently on purpose, and before this ViewModel that
 * behaviour lived in nested `try`/`catch` inside a `LaunchedEffect`.
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
    ) = ProfileViewModel(profile, stats, supabase)

    // MARK: - Load

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(ProfileUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()

            val state = viewModel.uiState.value as ProfileUiState.Content
            assertEquals("Pebbler", state.profile?.displayName)
            assertTrue(state.collectionsLoaded)
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

    /** Likewise the collections card, which is its own card on the page. */
    @Test
    fun `a failed collections fetch still shows the profile`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            profile.collectionsFailure = IOException("offline")
            val viewModel = viewModel(profile)
            advanceUntilIdle()

            val state = viewModel.uiState.value as ProfileUiState.Content
            assertEquals("Pebbler", state.profile?.displayName)
            assertTrue(state.collections.isEmpty())
            // Still "loaded", so the carousel shows its empty tile rather than
            // a skeleton that never resolves.
            assertTrue(state.collectionsLoaded)
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

    @Test
    fun `stats load alongside the profile`() =
        runTest {
            val stats = FakePathStatsService(karma = 42)
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()

            assertEquals(1, stats.loadCount)
            assertEquals(42, (viewModel.uiState.value as ProfileUiState.Content).karma)
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

    // MARK: - Covers

    @Test
    fun `the settings cover opens and closes`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openSettings()
            assertTrue(viewModel.covers.value.isPresentingSettings)

            viewModel.closeSettings()
            assertFalse(viewModel.covers.value.isPresentingSettings)
        }

    @Test
    fun `creating a collection reloads the page only`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            val loadsAfterInit = profile.loadProfileCount

            viewModel.openCreateCollection()
            viewModel.onCollectionCreated()
            advanceUntilIdle()

            assertFalse(viewModel.covers.value.isPresentingCreateCollection)
            assertEquals(loadsAfterInit + 1, profile.loadProfileCount)
            // Only the page. The composer's collection-picker cache is refreshed
            // by CollectionFormViewModel's own uncancellable save, which is why
            // this ViewModel no longer takes ReferenceDataServicing at all.
        }

    /**
     * Saving Settings folds the new values into the row already on screen rather
     * than refetching — closing the cover must not flash a spinner over content
     * that is already correct.
     */
    @Test
    fun `a settings save updates the row in place without refetching`() =
        runTest {
            val profile = FakeProfileService(profile = row)
            val viewModel = viewModel(profile)
            advanceUntilIdle()
            val loadsAfterInit = profile.loadProfileCount

            viewModel.openSettings()
            viewModel.onSettingsSaved(
                displayName = "Sam",
                glyph = Glyph(id = "glyph-2", name = "New", strokes = emptyList(), viewBox = "0 0 100 100"),
                handle = "sam",
                isPublic = true,
            )

            val state = viewModel.uiState.value as ProfileUiState.Content
            assertEquals("Sam", state.profile?.displayName)
            assertEquals("glyph-2", state.profile?.glyphId)
            assertEquals("sam", state.profile?.handle)
            assertTrue(state.profile?.publicProfile == true)
            assertFalse(viewModel.covers.value.isPresentingSettings)
            assertEquals("no refetch", loadsAfterInit, profile.loadProfileCount)
        }
}
