package app.pbbls.android.features.lab

import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.LogPlatform
import app.pbbls.android.features.lab.models.LogSpecies
import app.pbbls.android.features.lab.models.LogStatus
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** The see-all cover's load, mode guard and reaction contract (#849). */
class LogListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun log(
        id: String,
        reactionCount: Int = 0,
    ) = Log(
        id = id,
        species = LogSpecies.FEATURE,
        platform = LogPlatform.ANDROID,
        status = LogStatus.SHIPPED,
        titleEn = "Log $id",
        summaryEn = "Summary $id",
        published = true,
        createdAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
        reactionCount = reactionCount,
    )

    private fun viewModel(logs: FakeLogsService = FakeLogsService()) = LogListViewModel(logs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            assertEquals(LogListUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertEquals(listOf("c"), state.logs.map { it.id })
        }

    /** The see-all list is uncapped, unlike the Lab page's preview of it. */
    @Test
    fun `the full list asks for no limit`() =
        runTest {
            val logs = FakeLogsService()
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.BACKLOG)
            advanceUntilIdle()

            assertEquals(listOf<Int?>(null), logs.backlogLimits)
        }

    /** The rotation guard: the screen's `LaunchedEffect(mode)` re-runs for free. */
    @Test
    fun `start is idempotent for the same mode`() =
        runTest {
            val logs = FakeLogsService()
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()
            repeat(3) { viewModel.start(LogListMode.CHANGELOG) }
            advanceUntilIdle()

            assertEquals(1, logs.changelogLimits.size)
        }

    @Test
    fun `a failed load is Error, and retry recovers`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            logs.changelogFailure = IOException("offline")
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()
            assertEquals(
                R.string.lab_list_load_error,
                (viewModel.uiState.value as LogListUiState.Error).messageRes,
            )

            logs.changelogFailure = null
            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is LogListUiState.Content)
        }

    /**
     * Unlike the Lab page, the feed and the reactions fail together here: a list
     * with no rows is not a list.
     */
    @Test
    fun `a failed reactions read takes the whole list down`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            logs.reactionsFailure = IOException("offline")
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is LogListUiState.Error)
        }

    // MARK: - Reactions

    @Test
    fun `reacting flips membership and moves the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 2)))
            val viewModel = viewModel(logs)
            viewModel.start(LogListMode.BACKLOG)
            advanceUntilIdle()

            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertTrue("b" in state.reactedIds)
            assertEquals(3, state.logs.single().reactionCount)
            assertEquals(listOf("b"), logs.reactCalls)
        }

    /** The cover can be closed mid-toggle, so the revert has to survive it. */
    @Test
    fun `a failed reaction reverts both the flag and the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 2)))
            val viewModel = viewModel(logs)
            viewModel.start(LogListMode.BACKLOG)
            advanceUntilIdle()

            logs.failNextReaction = IOException("offline")
            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertFalse("b" in state.reactedIds)
            assertEquals(2, state.logs.single().reactionCount)
        }

    // MARK: - Reset

    /**
     * Re-opening the SAME mode is what actually tests `finish()` — a different
     * mode reloads on its own (the test below), so asserting on one would pass
     * with the reset deleted.
     */
    @Test
    fun `finish releases the mode guard`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()
            assertEquals(1, logs.changelogLimits.size)

            viewModel.finish()
            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()

            assertEquals("the next presentation must re-read", 2, logs.changelogLimits.size)
        }

    /** A different mode re-reads even without a finish. */
    @Test
    fun `start on a different mode reloads`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")), backlog = listOf(log("b")))
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG)
            advanceUntilIdle()
            viewModel.start(LogListMode.BACKLOG)
            advanceUntilIdle()

            assertEquals(
                listOf("b"),
                (viewModel.uiState.value as LogListUiState.Content).logs.map { it.id },
            )
        }
}
