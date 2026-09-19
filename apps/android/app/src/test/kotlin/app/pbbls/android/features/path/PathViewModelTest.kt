package app.pbbls.android.features.path

import androidx.compose.runtime.snapshots.Snapshot
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.FakePathStatsService
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.FakePebbleWriteService
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
import java.time.ZoneId

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

    private fun pebble(
        id: String,
        happenedAt: OffsetDateTime = now,
        createdAt: OffsetDateTime = now,
    ) = Pebble(
        id = id,
        name = "Pebble $id",
        happenedAt = happenedAt,
        createdAt = createdAt,
        intensity = 2,
        positiveness = 1,
    )

    private fun draftRecord(id: String) = PebbleDraftRecord(id = id, payload = PebbleDraftPayload(), updatedAt = now)

    private fun viewModel(
        path: FakePathService = FakePathService(),
        stats: FakePathStatsService = FakePathStatsService(),
        drafts: FakePebbleDraftsService = FakePebbleDraftsService(),
        writes: FakePebbleWriteService = FakePebbleWriteService(),
    ) = PathViewModel(path, stats, drafts, writes)

    // MARK: - Load

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val viewModel = viewModel(FakePathService(pebbles = listOf(pebble("a"))))
            assertEquals(PathUiState.Loading, viewModel.uiState.value)

            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.any { entry -> entry.pebbles.any { it.id == "a" } })
        }

    @Test
    fun `a failed first load becomes Error`() =
        runTest {
            val path = FakePathService()
            path.failNext = IOException("offline")
            val viewModel = viewModel(path)
            advanceUntilIdle()

            assertEquals(R.string.path_load_error, (viewModel.uiState.value as PathUiState.Error).messageRes)
        }

    @Test
    fun `retry recovers from a failed first load`() =
        runTest {
            val path = FakePathService(pebbles = listOf(pebble("a")))
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
            val path = FakePathService(pebbles = listOf(pebble("a")))
            val viewModel = viewModel(path)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is PathUiState.Content)

            path.failNext = IOException("offline")
            viewModel.reload()
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.any { entry -> entry.pebbles.any { it.id == "a" } })
        }

    /** Rotation, as far as a JVM test carries it: no refetch on re-read. */
    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val path = FakePathService(pebbles = listOf(pebble("a")))
            val viewModel = viewModel(path)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, path.loadCount)
        }

    @Test
    fun `reloadFocusing lands on the new pebble's week`() =
        runTest {
            val lastYear = now.minusWeeks(30)
            val path = FakePathService(pebbles = listOf(pebble("old", happenedAt = lastYear)))
            val viewModel = viewModel(path)
            advanceUntilIdle()

            path.pebbles = listOf(pebble("old", happenedAt = lastYear), pebble("new", happenedAt = lastYear))
            viewModel.reloadFocusing("new")
            advanceUntilIdle()

            val state = viewModel.uiState.value as PathUiState.Content
            val expected = WeekRollBuilder.weekStart(lastYear.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate())
            assertEquals(expected, state.focusedWeekStart)
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
            val path = FakePathService(pebbles = listOf(pebble("old", createdAt = now.minusDays(3))))
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
            val viewModel = viewModel(FakePathService(pebbles = listOf(pebble("today"))), stats)
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
    fun `confirming a delete removes the pebble and reloads`() =
        runTest {
            val path = FakePathService(pebbles = listOf(pebble("a")))
            val writes = FakePebbleWriteService()
            val viewModel = viewModel(path, writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(pebble("a"))
            assertEquals(
                "a",
                viewModel.covers.value.pendingDeletion
                    ?.id,
            )

            path.pebbles = emptyList()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("a"), writes.deletedPebbleIds)
            assertNull(viewModel.covers.value.pendingDeletion)
            val state = viewModel.uiState.value as PathUiState.Content
            assertTrue(state.entries.all { it.pebbles.isEmpty() })
        }

    @Test
    fun `deleting the pebble whose detail is open closes the detail`() =
        runTest {
            val viewModel = viewModel(FakePathService(pebbles = listOf(pebble("a"))))
            advanceUntilIdle()

            viewModel.openDetail("a")
            viewModel.requestDelete(pebble("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertNull(viewModel.covers.value.detailPebbleId)
        }

    @Test
    fun `a failed delete surfaces the error dialog`() =
        runTest {
            val writes = FakePebbleWriteService()
            writes.failNext = IOException("offline")
            val viewModel = viewModel(writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(pebble("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    @Test
    fun `cancelling a delete writes nothing`() =
        runTest {
            val writes = FakePebbleWriteService()
            val viewModel = viewModel(writes = writes)
            advanceUntilIdle()

            viewModel.requestDelete(pebble("a"))
            viewModel.cancelDelete()
            advanceUntilIdle()

            assertTrue(writes.deletedPebbleIds.isEmpty())
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    // MARK: - Covers

    /**
     * The hand-written exclusion the old screen carried
     * (`isPresentingDrafts && !isPresentingCreate && !isPresentingFlow`), now a
     * property with a test rather than a condition repeated at the call site.
     */
    @Test
    fun `the drafts list hides while a composer is stacked over it`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openDrafts()
            assertTrue(viewModel.covers.value.showsDrafts)

            viewModel.resumeDraft(draftRecord("d1"))
            assertFalse(viewModel.covers.value.showsDrafts)
            assertTrue(viewModel.covers.value.isPresentingFlow)
        }

    /**
     * The asymmetry between the two composers, pinned because it is easy to
     * "tidy away": dismissing the flow leaves the drafts list too, while
     * cancelling the form returns to it.
     */
    @Test
    fun `dismissing the flow closes drafts, cancelling the form does not`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openDrafts()
            viewModel.resumeDraft(draftRecord("d1"))
            viewModel.closeFlow()
            assertFalse(viewModel.covers.value.isPresentingDrafts)

            viewModel.openDrafts()
            viewModel.openForm()
            viewModel.closeForm()
            assertTrue(viewModel.covers.value.isPresentingDrafts)
        }

    @Test
    fun `the form reveals the new pebble through the detail cover`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.openForm()
            viewModel.onFormCreated("new-pebble")
            advanceUntilIdle()

            assertEquals("new-pebble", viewModel.covers.value.detailPebbleId)
            assertFalse(viewModel.covers.value.isPresentingCreate)
        }

    @Test
    fun `saving an edit swaps back to the detail and makes it re-read`() =
        runTest {
            val viewModel = viewModel(FakePathService(pebbles = listOf(pebble("a"))))
            advanceUntilIdle()

            viewModel.openDetail("a")
            viewModel.openEdit()
            assertEquals("a", viewModel.covers.value.editingPebbleId)
            val before = viewModel.covers.value.detailReloadKey

            viewModel.onEditSaved()
            advanceUntilIdle()

            assertNull(viewModel.covers.value.editingPebbleId)
            assertEquals("a", viewModel.covers.value.detailPebbleId)
            assertEquals(before + 1, viewModel.covers.value.detailReloadKey)
        }

    @Test
    fun `every draft write bumps the drafts reload key and rereads the badge`() =
        runTest {
            val drafts = FakePebbleDraftsService()
            val viewModel = viewModel(drafts = drafts)
            advanceUntilIdle()
            val countsAfterInit = drafts.countCallCount
            val keyBefore = viewModel.covers.value.draftsReloadKey

            viewModel.onFlowDraftSaved()
            advanceUntilIdle()

            assertEquals(keyBefore + 1, viewModel.covers.value.draftsReloadKey)
            assertEquals(countsAfterInit + 1, drafts.countCallCount)
        }
}
