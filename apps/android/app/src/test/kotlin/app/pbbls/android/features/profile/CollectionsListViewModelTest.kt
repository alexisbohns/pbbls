package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The collections list's load, refresh and delete contract (#849). */
class CollectionsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun collection(
        id: String,
        name: String = "Collection $id",
        mode: CollectionMode? = null,
    ) = Collection(id = id, name = name, mode = mode, pebbleCount = 3)

    private fun viewModel(
        collections: FakeCollectionsService = FakeCollectionsService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = CollectionsListViewModel(collections, refs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)

            assertEquals(CollectionsListUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionsListUiState.Content
            assertEquals(listOf("a"), state.collections.map { it.id })
            assertFalse(state.isRefreshing)
        }

    @Test
    fun `an empty result is Content, not Error`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue((viewModel.uiState.value as CollectionsListUiState.Content).collections.isEmpty())
        }

    @Test
    fun `a failed fetch becomes Error, and retry recovers`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            service.failNext = IOException("offline")
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            assertEquals(
                R.string.collections_load_error,
                (viewModel.uiState.value as CollectionsListUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is CollectionsListUiState.Content)
        }

    /**
     * The pull gesture holds the indicator up for the fetch and puts it down
     * after — and it rides on [CollectionsListUiState.Content], so "refreshing
     * while erroring" is not a representable state.
     */
    @Test
    fun `refresh raises and lowers the indicator`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            viewModel.refresh()
            assertTrue((viewModel.uiState.value as CollectionsListUiState.Content).isRefreshing)

            advanceUntilIdle()
            val state = viewModel.uiState.value as CollectionsListUiState.Content
            assertFalse(state.isRefreshing)
            assertEquals(2, service.listCount)
        }

    /**
     * Deliberate behaviour change: the screen this replaces cleared its error
     * flag at the top of every fetch, so a pull-to-refresh that lost the network
     * swapped the user's collections for the error screen. A failed refresh now
     * lowers the indicator and leaves the list alone.
     */
    @Test
    fun `a failed refresh keeps the list and lowers the indicator`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionsListUiState.Content
            assertEquals(listOf("a"), state.collections.map { it.id })
            assertFalse(state.isRefreshing)
        }

    /**
     * The indicator is raised by [CollectionsListViewModel.refresh] and lowered
     * in that coroutine's tail — which cancelling skips. Every other entry point
     * cancels the fetch in flight, so without lowering it there too the spinner
     * runs forever: pull to refresh on a slow network, then long-press a row and
     * confirm the delete.
     */
    @Test
    fun `a delete during a pull-to-refresh does not strand the indicator`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            service.listGate = CompletableDeferred()
            viewModel.refresh()
            advanceUntilIdle()
            assertTrue((viewModel.uiState.value as CollectionsListUiState.Content).isRefreshing)

            // The delete's reload cancels the refresh mid-flight.
            service.listGate = null
            viewModel.requestDelete(collection("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertFalse((viewModel.uiState.value as CollectionsListUiState.Content).isRefreshing)
        }

    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, service.listCount)
        }

    // MARK: - Delete

    @Test
    fun `confirmDelete deletes, refreshes the picker cache and reloads`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(collections = service, refs = refs)
            advanceUntilIdle()

            viewModel.requestDelete(collection("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("a"), service.deleteCalls)
            assertEquals(1, refs.refreshCollectionsCount)
            assertEquals(2, service.listCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    @Test
    fun `a failed delete reports and leaves the list alone`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.requestDelete(collection("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            assertEquals(1, service.listCount)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    @Test
    fun `cancelDelete drops the target without calling the server`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            viewModel.requestDelete(collection("a"))
            viewModel.cancelDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(service.deleteCalls.isEmpty())
        }

    // MARK: - Returning from the detail

    /** See [SoulsListViewModelTest]: the back stack entry outlives the round trip. */
    @Test
    fun `returning to the list re-reads it`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.listCount)

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.listCount)
        }

    @Test
    fun `a failed reload keeps the list`() =
        runTest {
            val service = FakeCollectionsService(collections = listOf(collection("a")))
            val viewModel = viewModel(collections = service)
            advanceUntilIdle()
            viewModel.onResumed()
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.onResumed()
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionsListUiState.Content
            assertEquals(listOf("a"), state.collections.map { it.id })
        }
}
