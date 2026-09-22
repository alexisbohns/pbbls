package app.pbbls.android.features.lab

import app.pbbls.android.R
import app.pbbls.android.core.model.Log
import app.pbbls.android.core.model.LogPlatform
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.core.model.LogStatus
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

/** The see-all list's load, mode parsing and reaction contract (#849, #852). */
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

            viewModel.start(LogListMode.CHANGELOG.name)
            assertEquals(LogListUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertEquals(LogListMode.CHANGELOG, state.mode)
            assertEquals(listOf("c"), state.logs.map { it.id })
        }

    /** The see-all list is uncapped, unlike the Lab page's preview of it. */
    @Test
    fun `the full list asks for no limit`() =
        runTest {
            val logs = FakeLogsService()
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.BACKLOG.name)
            advanceUntilIdle()

            assertEquals(listOf<Int?>(null), logs.backlogLimits)
        }

    /** The rotation guard: the screen's `LaunchedEffect(mode)` re-runs for free. */
    @Test
    fun `start is idempotent for the same mode`() =
        runTest {
            val logs = FakeLogsService()
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG.name)
            advanceUntilIdle()
            repeat(3) { viewModel.start(LogListMode.CHANGELOG.name) }
            advanceUntilIdle()

            assertEquals(1, logs.changelogLimits.size)
        }

    @Test
    fun `a failed load is Error, and retry recovers`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            logs.changelogFailure = IOException("offline")
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG.name)
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

            viewModel.start(LogListMode.CHANGELOG.name)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is LogListUiState.Error)
        }

    // MARK: - Mode parsing

    /**
     * `PebblesKey.LabLogList.mode` is a `String` (a `NavKey` argument can only
     * carry primitives) mapped back with `LogListMode.valueOf`. A value that
     * matches no constant — a stale persisted key from a dropped one, or
     * tampering — must not silently become the first constant (the
     * `AuthMode.fromRoute` bug this migration deleted elsewhere): it publishes
     * `Error` instead, without ever touching the service.
     */
    @Test
    fun `an unrecognized mode is Error, not the first constant`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")))
            val viewModel = viewModel(logs)

            viewModel.start("not-a-real-mode")
            advanceUntilIdle()

            assertEquals(
                R.string.lab_list_load_error,
                (viewModel.uiState.value as LogListUiState.Error).messageRes,
            )
            assertTrue("must not fetch on an unresolved mode", logs.changelogLimits.isEmpty())
            assertTrue("must not fetch on an unresolved mode", logs.backlogLimits.isEmpty())
        }

    // MARK: - Reactions

    @Test
    fun `reacting flips membership and moves the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 2)))
            val viewModel = viewModel(logs)
            viewModel.start(LogListMode.BACKLOG.name)
            advanceUntilIdle()

            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertTrue("b" in state.reactedIds)
            assertEquals(3, state.logs.single().reactionCount)
            assertEquals(listOf("b"), logs.reactCalls)
        }

    /** The list can be left mid-toggle, so the revert has to survive it. */
    @Test
    fun `a failed reaction reverts both the flag and the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 2)))
            val viewModel = viewModel(logs)
            viewModel.start(LogListMode.BACKLOG.name)
            advanceUntilIdle()

            logs.failNextReaction = IOException("offline")
            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as LogListUiState.Content
            assertFalse("b" in state.reactedIds)
            assertEquals(2, state.logs.single().reactionCount)
        }

    // MARK: - Mode changes

    /**
     * A different mode re-reads. Re-opening the SAME mode (`start is idempotent`
     * above) is what covers the guard actually skipping a redundant fetch; the
     * mode guard's release on close (`finish()`) is gone as of #852 — an
     * entry's ViewModel is destroyed when the entry is popped, so the next
     * presentation is always a fresh instance with nothing to release.
     */
    @Test
    fun `start on a different mode reloads`() =
        runTest {
            val logs = FakeLogsService(changelog = listOf(log("c")), backlog = listOf(log("b")))
            val viewModel = viewModel(logs)

            viewModel.start(LogListMode.CHANGELOG.name)
            advanceUntilIdle()
            viewModel.start(LogListMode.BACKLOG.name)
            advanceUntilIdle()

            assertEquals(
                listOf("b"),
                (viewModel.uiState.value as LogListUiState.Content).logs.map { it.id },
            )
        }
}
