package app.pbbls.android.features.path

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.R
import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.core.model.PebbleDraftPayload
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
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
 * The Path timeline's load, refresh, delete and cover contract (#849).
 *
 * None of this was reachable from a test before: the screen held all of it in
 * `remember` inside a composable that read five `Local…Service`s.
 */
class PathViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: OffsetDateTime = OffsetDateTime.now()

    /** Dated now: the pager opens on the current week. `createdAt` equals `happenedAt`. */
    private fun moment(
        id: String,
        happenedAt: OffsetDateTime = now,
    ): PathMoment = UiFixtures.pathMoment(id = id, name = "Moment $id", happenedAt = happenedAt)

    private fun draftRecord(id: String) = PebbleDraftRecord(id = id, payload = PebbleDraftPayload(), updatedAt = now)

    private fun viewModel(
        path: FakeMomentReadService = FakeMomentReadService(),
        stats: FakePathStatsService = FakePathStatsService(),
        drafts: FakePebbleDraftsService = FakePebbleDraftsService(),
        writes: FakeMomentWriteService = FakeMomentWriteService(),
    ) = PathViewModel(path, stats, drafts, writes)

    // MARK: - Returning from a pushed screen

    /**
     * `PathScreen` kept its pebbles in `remember`, so pushing Profile and popping
     * back rebuilt it and re-ran the fetch. The ViewModel is scoped to the
     * `NavBackStackEntry`, which survives that round trip — so without the resume
     * hook, a pebble edited from a pushed screen stayed stale on the timeline.
     * (Deferred from PR #896, where the souls and collections lists got the same
     * treatment.)
     */
    @Test
    fun `returning to the timeline re-reads it`() =
        runTest {
            val path = FakeMomentReadService(path = listOf(moment("p1")))
            val viewModel = viewModel(path = path)
            advanceUntilIdle()
            val loadsAfterInit = path.loadPathCount

            // First resume: the screen just opened, `init` already loaded.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(loadsAfterInit, path.loadPathCount)

            // Second: back from Profile.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(loadsAfterInit + 1, path.loadPathCount)
        }

    /**
     * The resume hook is also what replaced `detailReloadKey`, `draftsReloadKey`
     * and the `onFlowPublished`/`onFormCreated` callbacks (#852): every entry that
     * writes something — publish, edit, drafts — pops back to Path, and a resume
     * refreshes both the timeline and the drafts badge in one place.
     */
    @Test
    fun `returning to the timeline also refreshes the draft count`() =
        runTest {
            val drafts = FakePebbleDraftsService()
            val viewModel = viewModel(drafts = drafts)
            advanceUntilIdle()
            val countsAfterInit = drafts.countCallCount

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(countsAfterInit, drafts.countCallCount)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(countsAfterInit + 1, drafts.countCallCount)
        }

    /** The refresh is silent — it must not blank the timeline to a spinner. */
    @Test
    fun `returning does not flash the spinner`() =
        runTest {
            val viewModel = viewModel(path = FakeMomentReadService(path = listOf(moment("p1"))))
            advanceUntilIdle()
            viewModel.onResumed()

            viewModel.onResumed()
            assertTrue(viewModel.uiState.value is PathUiState.Content)
        }

    // MARK: - Load

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val viewModel = viewModel(FakeMomentReadService(path = listOf(moment("a"))))
            assertEquals(PathUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.any { entry -> entry.moments.any { it.id == "a" } })
        }

    @Test
    fun `a failed first load becomes Error`() =
        runTest {
            val path = FakeMomentReadService()
            path.failNext = IOException("offline")
            val viewModel = viewModel(path)
            advanceUntilIdle()

            assertEquals(R.string.path_load_error, (viewModel.uiState.value as PathUiState.Error).messageRes)
        }

    @Test
    fun `retry recovers from a failed first load`() =
        runTest {
            val path = FakeMomentReadService(path = listOf(moment("a")))
            path.failNext = IOException("offline")
            val viewModel = viewModel(path)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is PathUiState.Error)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is PathUiState.Content)
        }

    /**
     * The regression the split between [PathViewModel.retry] and
     * [PathViewModel.reload] exists for: a background refresh that loses the
     * network must not replace a timeline the user is looking at with an error
     * screen.
     */
    @Test
    fun `a failed reload keeps the timeline that is already up`() =
        runTest {
            val path = FakeMomentReadService(path = listOf(moment("a")))
            val viewModel = viewModel(path)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is PathUiState.Content)

            path.failNext = IOException("offline")
            viewModel.reload()
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.any { entry -> entry.moments.any { it.id == "a" } })
        }

    /** Rotation, as far as a JVM test carries it: no refetch on re-read. */
    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val path = FakeMomentReadService(path = listOf(moment("a")))
            val viewModel = viewModel(path)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, path.loadPathCount)
        }

    // MARK: - Stats and the local active-today override

    @Test
    fun `loads stats alongside the timeline`() =
        runTest {
            val stats = FakePathStatsService()
            viewModel(stats = stats)
            advanceUntilIdle()

            assertEquals(1, stats.loadCount)
        }

    /**
     * The server compares `active_today` against UTC `current_date`. A user past
     * local midnight with no pebble today must not be told they were active.
     */
    @Test
    fun `ripple recomputes active_today against the device day`() =
        runTest {
            val stats =
                FakePathStatsService(
                    ripple = RippleSummary(rippleLevel = 3, pebbles28d = 7, activeToday = true),
                )
            val path = FakeMomentReadService(path = listOf(moment("old", happenedAt = now.minusDays(3))))
            val viewModel = viewModel(path, stats)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertFalse("no pebble created today, so active_today must be false", state.ripple!!.activeToday)
        }

    @Test
    fun `ripple keeps active_today when a pebble was created today`() =
        runTest {
            val stats =
                FakePathStatsService(
                    ripple = RippleSummary(rippleLevel = 3, pebbles28d = 7, activeToday = false),
                )
            val viewModel = viewModel(FakeMomentReadService(path = listOf(moment("today"))), stats)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.ripple!!.activeToday)
        }

    /**
     * The `snapshotFlow` bridge, which the other stats tests do *not* cover:
     * they would pass off the synchronous read inside `publish()`. This one
     * changes the shared service after the load has settled, the way the glyph
     * picker's `applyKarmaBalance` does while Path is underneath it, and asserts
     * the timeline's state follows.
     *
     * `Snapshot.sendApplyNotifications()` is explicit here because nothing else
     * drives it off device — in the app the Recomposer's frame clock does.
     */
    @Test
    fun `a karma change on the shared stats service reaches the state`() =
        runTest {
            val stats = FakePathStatsService(karma = 10)
            val viewModel = viewModel(stats = stats)
            advanceUntilIdle()
            assertEquals(10, (viewModel.uiState.value as PathUiState.Content).karma)

            stats.applyKarmaBalance(42)
            Snapshot.sendApplyNotifications()
            advanceUntilIdle()

            assertEquals(42, (viewModel.uiState.value as PathUiState.Content).karma)
        }

    @Test
    fun `no server ripple means no ripple at all`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull((viewModel.uiState.value as PathUiState.Content).ripple)
        }

    @Test
    fun `an uncompleted moment is on the timeline`() =
        runTest {
            val path =
                FakeMomentReadService(
                    path = listOf(UiFixtures.pathMoment(id = "bare", name = null, happenedAt = now, pebble = null, firstSnapPath = "u/s")),
                )
            val viewModel = viewModel(path)
            advanceUntilIdle()

            val content = viewModel.uiState.value as PathUiState.Content
            assertEquals(listOf("bare"), content.entries.flatMap { it.moments }.map { it.id })
        }

    /** A moment captured today without its pebble is not activity yet (A-13). */
    @Test
    fun `ripple counts today only for a completed moment created today`() =
        runTest {
            val stats = FakePathStatsService(ripple = RippleSummary(rippleLevel = 1, pebbles28d = 1, activeToday = true))
            val path = FakeMomentReadService(path = listOf(UiFixtures.pathMoment(happenedAt = now, pebble = null)))
            val viewModel = viewModel(path, stats)
            advanceUntilIdle()

            assertEquals(false, (viewModel.uiState.value as PathUiState.Content).ripple?.activeToday)
        }

    // MARK: - Draft count

    @Test
    fun `reads the draft count for the entry-point badge`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(draftRecord("d1"), draftRecord("d2")))
            val viewModel = viewModel(drafts = drafts)
            advanceUntilIdle()

            assertEquals(2, (viewModel.uiState.value as PathUiState.Content).draftCount)
        }

    /** A badge that cannot be read is a zero, never an error screen. */
    @Test
    fun `a failed draft count does not fail the timeline`() =
        runTest {
            val drafts = FakePebbleDraftsService()
            drafts.failNext = IOException("offline")
            val viewModel = viewModel(drafts = drafts)
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertEquals(0, state.draftCount)
        }

    // MARK: - Delete

    @Test
    fun `confirming a delete removes the moment and reloads`() =
        runTest {
            val path = FakeMomentReadService(path = listOf(moment("a")))
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(path, writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(moment("a"))
            assertEquals(
                "a",
                viewModel.covers.value.pendingDeletion
                    ?.id,
            )

            path.path = emptyList()
            viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertEquals(listOf("a"), writes.deletedMoments)
            assertNull(viewModel.covers.value.pendingDeletion)
            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.all { it.moments.isEmpty() })
        }

    @Test
    fun `a failed delete surfaces the error dialog`() =
        runTest {
            val writes = FakeMomentWriteService()
            writes.failNext = IOException("offline")
            val viewModel = viewModel(writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(moment("a"))
            viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    @Test
    fun `cancelling a delete writes nothing`() =
        runTest {
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(moment("a"))
            viewModel.cancelDelete()
            advanceUntilIdle()

            assertTrue(writes.deletedMoments.isEmpty())
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    /** R8: "Delete the pebble, keep the moment" sends the PEBBLE id to `delete_pebble`. */
    @Test
    fun `deleting only the pebble keeps the moment`() =
        runTest {
            val target = UiFixtures.pathMoment(id = "m-1", happenedAt = now, pebble = UiFixtures.pathPebble(id = "p-1"))
            val path = FakeMomentReadService(path = listOf(target))
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(path, writes = writes)
            advanceUntilIdle()
            val loadsBefore = path.loadPathCount

            viewModel.requestDelete(target)
            viewModel.confirmDelete(MomentDeleteChoice.PEBBLE_ONLY)
            advanceUntilIdle()

            assertEquals(listOf("p-1"), writes.deletedPebbles)
            assertTrue(writes.deletedMoments.isEmpty())
            assertEquals(loadsBefore + 1, path.loadPathCount)
        }

    @Test
    fun `deleting the moment deletes the moment`() =
        runTest {
            val target = UiFixtures.pathMoment(id = "m-1", happenedAt = now)
            val path = FakeMomentReadService(path = listOf(target))
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(path, writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(target)
            viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertEquals(listOf("m-1"), writes.deletedMoments)
            assertTrue(writes.deletedPebbles.isEmpty())
        }
}
