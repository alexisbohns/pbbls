package app.pbbls.android.features.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.core.model.Log
import app.pbbls.android.core.model.LogPlatform
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.core.model.LogStatus
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** The Lab's four independent feeds and its optimistic reaction toggle (#849). */
class LabViewModelTest {
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

    private fun viewModel(logs: FakeLogsService = FakeLogsService()) = LabViewModel(logs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val logs = FakeLogsService(announcements = listOf(log("a")), backlog = listOf(log("b")))
            val viewModel = viewModel(logs)

            assertEquals(LabUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertEquals(listOf("a"), state.announcements.map { it.id })
            assertEquals(listOf("b"), state.backlog.map { it.id })
        }

    /**
     * Only the two capped feeds take a limit; the others are unlimited.
     *
     * The value is pinned to iOS `LabView.feedLimit` (5), not to whatever this
     * ViewModel happens to declare — the two surfaces show the same preview, and
     * a test that asserts the local constant would defend a drift rather than
     * catch one.
     */
    @Test
    fun `the capped feeds ask for the iOS feed limit`() =
        runTest {
            val logs = FakeLogsService()
            viewModel(logs)
            advanceUntilIdle()

            assertEquals(listOf(5), logs.changelogLimits)
            assertEquals(listOf(5), logs.backlogLimits)
        }

    /**
     * The feeds fail independently by design: one dead query leaves its section
     * empty and the page renders.
     */
    @Test
    fun `one dead feed leaves the rest of the page up`() =
        runTest {
            val logs = FakeLogsService(announcements = listOf(log("a")))
            logs.backlogFailure = IOException("offline")
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertEquals(listOf("a"), state.announcements.map { it.id })
            assertTrue(state.backlog.isEmpty())
        }

    /** …and only all four failing together is the page being unavailable. */
    @Test
    fun `all four feeds failing is Error, and retry recovers`() =
        runTest {
            val logs = FakeLogsService(announcements = listOf(log("a")))
            logs.failEveryFeed(IOException("offline"))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            assertEquals(
                R.string.lab_load_error,
                (viewModel.uiState.value as LabUiState.Error).messageRes,
            )

            logs.announcementsFailure = null
            logs.changelogFailure = null
            logs.initiativesFailure = null
            logs.backlogFailure = null
            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is LabUiState.Content)
        }

    /**
     * **A failed reload must keep the page, not blank it.**
     *
     * Writing the four `orEmpty()` lists before checking whether anything came
     * back renders the Lab as just the community card — no error, no retry, and
     * `isLoaded` already true so it never returns to Loading. Reachable by
     * backgrounding the app, losing connectivity and resuming.
     */
    @Test
    fun `a reload with every feed dead keeps the page that is up`() =
        runTest {
            val logs = FakeLogsService(announcements = listOf(log("a")), backlog = listOf(log("b")))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            logs.failEveryFeed(IOException("offline"))
            logs.reactionsFailure = IOException("offline")
            viewModel.onResumed()
            viewModel.onResumed()
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertEquals(listOf("a"), state.announcements.map { it.id })
            assertEquals(listOf("b"), state.backlog.map { it.id })
        }

    /** …while the same failure on the FIRST load is the error state. */
    @Test
    fun `a first load with every feed dead is Error`() =
        runTest {
            val logs = FakeLogsService()
            logs.failEveryFeed(IOException("offline"))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is LabUiState.Error)
        }

    /** Returning from Profile re-reads: the back stack entry outlives the trip. */
    @Test
    fun `returning to the Lab re-reads it`() =
        runTest {
            val logs = FakeLogsService()
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, logs.changelogLimits.size)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, logs.changelogLimits.size)
        }

    // MARK: - Reactions

    @Test
    fun `reacting flips membership and moves the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 4)))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            viewModel.toggleReaction(log("b"))

            // Optimistic, before the request runs.
            val state = viewModel.uiState.value as LabUiState.Content
            assertTrue("b" in state.reactedIds)
            assertEquals(5, state.backlog.single().reactionCount)

            advanceUntilIdle()
            assertEquals(listOf("b"), logs.reactCalls)
        }

    @Test
    fun `un-reacting sends the opposite write`() =
        runTest {
            val logs =
                FakeLogsService(backlog = listOf(log("b", reactionCount = 4)), reactions = setOf("b"))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            assertEquals(listOf("b"), logs.unreactCalls)
            assertTrue(logs.reactCalls.isEmpty())
            val state = viewModel.uiState.value as LabUiState.Content
            assertFalse("b" in state.reactedIds)
            assertEquals(3, state.backlog.single().reactionCount)
        }

    /**
     * The revert is what undoes the optimism. Losing it leaves a reaction
     * showing as registered that the server rejected, with a count one too
     * high — and re-tapping then sends the *opposite* request, because the
     * client believes its own optimism.
     */
    @Test
    fun `a failed reaction reverts both the flag and the count`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 4)))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            logs.failNextReaction = IOException("offline")
            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertFalse("b" in state.reactedIds)
            assertEquals(4, state.backlog.single().reactionCount)
        }

    /** **The write this ViewModel exists for** — the revert must survive leaving. */
    @Test
    fun `clearing the ViewModel mid-reaction still reverts a failure`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 4)))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[LabViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            logs.reactGate = gate
            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()
            assertEquals(listOf("b"), logs.reactCalls)

            store.clear()
            logs.failNextReaction = IOException("offline")
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertFalse("the optimism must be undone, or the heart lies", "b" in state.reactedIds)
            assertEquals(4, state.backlog.single().reactionCount)
        }

    /**
     * A reload landing between the optimistic flip and the failure replaces the
     * data the optimism was applied to. Reverting then subtracts the +1 from a
     * count the server never had it in.
     */
    @Test
    fun `a reload between the toggle and its failure cancels the revert`() =
        runTest {
            val logs = FakeLogsService(backlog = listOf(log("b", reactionCount = 4)))
            val viewModel = viewModel(logs)
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            logs.reactGate = gate
            viewModel.toggleReaction(log("b"))
            advanceUntilIdle()

            // The server's own answer arrives while the write is still in flight.
            logs.backlog = listOf(log("b", reactionCount = 9))
            viewModel.onResumed()
            viewModel.onResumed()
            advanceUntilIdle()

            logs.failNextReaction = IOException("offline")
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value as LabUiState.Content
            assertEquals(
                "the revert must not subtract from a count that never held the +1",
                9,
                state.backlog.single().reactionCount,
            )
        }
}
