package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.AchievementUnlockRecord
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/**
 * The load / error / retry contract of the first #849 ViewModel.
 *
 * This is the test acceptance criterion 3 asks for on every screen ("a JVM test
 * against fakes, `runTest` + `StandardTestDispatcher`"), and it is the template
 * the remaining ten copy.
 */
class AchievementsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun record(
        id: String,
        family: String,
        sortOrder: Int,
        isActive: Boolean = true,
    ) = AchievementRecord(
        id = id,
        slug = id,
        family = family,
        sortOrder = sortOrder,
        karmaReward = 5,
        isActive = isActive,
    )

    private fun unlock(id: String) =
        AchievementUnlockRecord(
            achievementId = id,
            unlockedAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
        )

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service =
                FakeAchievementsService(
                    catalog = listOf(record("p1", family = "pebbles", sortOrder = 1)),
                    unlocks = listOf(unlock("p1")),
                )
            val viewModel = AchievementsViewModel(service)

            // StandardTestDispatcher queues the init load, so the very first
            // state a collector sees is Loading — which is the state the screen
            // renders its spinner from.
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()

            val state = viewModel.uiState.value as AchievementsUiState.Content
            assertEquals(listOf("p1"), state.catalog.map { it.id })
            assertEquals(setOf("p1"), state.unlockedAt.keys)
        }

    @Test
    fun `runs the retroactive grant before reading`() =
        runTest {
            val service = FakeAchievementsService()
            AchievementsViewModel(service)
            advanceUntilIdle()

            assertEquals(1, service.checkCount)
            assertEquals(1, service.catalogLoadCount)
        }

    @Test
    fun `a failed read becomes Error, not a half-filled grid`() =
        runTest {
            val service = FakeAchievementsService()
            service.failNext = IOException("offline")
            val viewModel = AchievementsViewModel(service)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AchievementsUiState.Error
            assertEquals(R.string.achievements_load_error, state.messageRes)
        }

    @Test
    fun `retry reloads and recovers`() =
        runTest {
            val service =
                FakeAchievementsService(catalog = listOf(record("p1", "pebbles", 1)))
            // ArmedFailure clears after firing, so the retry hits the happy path.
            service.failNext = IOException("offline")
            val viewModel = AchievementsViewModel(service)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AchievementsUiState.Error)

            viewModel.retry()
            assertEquals(AchievementsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AchievementsUiState.Content
            assertEquals(listOf("p1"), state.catalog.map { it.id })
        }

    /**
     * The rotation criterion, as far as a JVM test can carry it: a recomposition
     * reads `uiState` again and gets the loaded value back without the service
     * being touched. A `ViewModel` surviving the configuration change is the
     * framework's half; not re-fetching on every read is this class's half.
     */
    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val service = FakeAchievementsService(catalog = listOf(record("p1", "pebbles", 1)))
            val viewModel = AchievementsViewModel(service)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, service.catalogLoadCount)
        }

    /**
     * Step 6 of the issue: the per-recomposition derivation moves into the state.
     * Inactive-and-unearned badges stay filtered, and the grouping is computed
     * from the same source data the case already carries.
     */
    @Test
    fun `Content derives the family groups`() =
        runTest {
            val service =
                FakeAchievementsService(
                    catalog =
                        listOf(
                            record("p1", family = "pebbles", sortOrder = 1),
                            record("p2", family = "pebbles", sortOrder = 2),
                            record("s1", family = "souls", sortOrder = 3, isActive = false),
                        ),
                    unlocks = listOf(unlock("p1")),
                )
            val viewModel = AchievementsViewModel(service)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AchievementsUiState.Content
            assertEquals(listOf("pebbles"), state.groups.map { it.family })
            assertEquals(
                listOf("p1", "p2"),
                state.groups
                    .single()
                    .records
                    .map { it.id },
            )
        }

    /** `by lazy` means the grouping is computed once per state value, not per read. */
    @Test
    fun `the derived groups are the same instance on every read`() =
        runTest {
            val service = FakeAchievementsService(catalog = listOf(record("p1", "pebbles", 1)))
            val viewModel = AchievementsViewModel(service)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AchievementsUiState.Content
            assertTrue(state.groups === state.groups)
        }
}
