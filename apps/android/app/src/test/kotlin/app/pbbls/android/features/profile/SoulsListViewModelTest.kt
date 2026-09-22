package app.pbbls.android.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pbbls.android.R
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSoulsService
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

/**
 * The souls grid's load, error, retry and delete contract (#849).
 *
 * None of this was reachable before: the screen held seven `remember`s and read
 * two `Local…Service`s inside the composable.
 */
class SoulsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun soul(
        id: String,
        name: String = "Soul $id",
    ) = SoulWithGlyph(
        id = id,
        name = name,
        glyphId = "glyph-$id",
        glyph = Glyph(id = "glyph-$id", strokes = emptyList(), viewBox = "0 0 200 200"),
    )

    private fun viewModel(
        souls: FakeSoulsService = FakeSoulsService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = SoulsListViewModel(souls, refs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)

            assertEquals(SoulsListUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulsListUiState.Content
            assertEquals(listOf("a"), state.souls.map { it.id })
        }

    /**
     * The empty grid is [SoulsListUiState.Content] with no souls, not a fourth
     * case and not an error — the three `remember`s it replaces could say
     * "not loading, did not fail, no items" and leave the screen to guess.
     */
    @Test
    fun `an empty result is Content, not Error`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulsListUiState.Content
            assertTrue(state.souls.isEmpty())
        }

    @Test
    fun `a failed fetch becomes Error`() =
        runTest {
            val service = FakeSoulsService()
            service.failNext = IOException("offline")
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()

            assertEquals(
                R.string.souls_load_error,
                (viewModel.uiState.value as SoulsListUiState.Error).messageRes,
            )
        }

    @Test
    fun `retry reloads and recovers`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            service.failNext = IOException("offline")
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is SoulsListUiState.Error)

            viewModel.retry()
            assertEquals(SoulsListUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(1, (viewModel.uiState.value as SoulsListUiState.Content).souls.size)
        }

    /** The rotation criterion: reading the state again does not re-fetch. */
    @Test
    fun `reading the state again does not refetch`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()

            repeat(5) { viewModel.uiState.value }

            assertEquals(1, service.listCount)
        }

    // MARK: - Delete

    /**
     * The bug this ViewModel exists for: the delete and the reference-data
     * refresh used to run in `rememberCoroutineScope`, so popping the screen
     * mid-sequence left a deleted soul in the composer's picker cache.
     */
    @Test
    fun `confirmDelete deletes, refreshes the picker cache and reloads`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(souls = service, refs = refs)
            advanceUntilIdle()

            viewModel.requestDelete(soul("a"))
            val pending = viewModel.covers.value.pendingDeletion
            assertEquals("a", pending?.id)

            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("a"), service.deleteCalls)
            assertEquals(1, refs.refreshSoulsCount)
            assertEquals(2, service.listCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    /**
     * **The regression this ViewModel exists for.** The delete ran in
     * `rememberCoroutineScope`, so popping the screen after the request had left
     * skipped the cache refresh, and the composer's picker went on offering a
     * soul the server had dropped. Clearing the `ViewModelStore` cancels
     * `viewModelScope` the way leaving does.
     */
    @Test
    fun `clearing the ViewModel mid-delete still refreshes the picker cache`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(souls = service, refs = refs)
            advanceUntilIdle()

            val store = ViewModelStore()
            ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
                },
            )[SoulsListViewModel::class.java]

            val gate = CompletableDeferred<Unit>()
            service.deleteGate = gate
            viewModel.requestDelete(soul("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("a"), service.deleteCalls)
            assertEquals(0, refs.refreshSoulsCount)

            store.clear()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "the picker cache must still be refreshed, or a deleted soul stays taggable",
                1,
                refs.refreshSoulsCount,
            )
        }

    @Test
    fun `a failed delete reports and leaves the grid alone`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.requestDelete(soul("a"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            // No reload, so the grid the user is looking at is untouched.
            assertEquals(1, service.listCount)
            assertTrue(viewModel.uiState.value is SoulsListUiState.Content)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    @Test
    fun `cancelDelete drops the target without calling the server`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()

            viewModel.requestDelete(soul("a"))
            viewModel.cancelDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(service.deleteCalls.isEmpty())
        }

    // MARK: - Returning from the detail

    /**
     * The regression the resume hook exists for: the ViewModel is scoped to the
     * `NavBackStackEntry`, which survives a trip to the detail, so `init` alone
     * would leave a soul renamed in the detail showing its old name on the grid
     * — with no gesture that repairs it.
     */
    @Test
    fun `returning to the grid re-reads it`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()

            // First resume: the screen just opened, `init` already loaded.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.listCount)

            // Second: back from the detail.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.listCount)
        }

    /** The refresh is silent — it must not blank the grid to a spinner. */
    @Test
    fun `returning does not flash the spinner`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()
            viewModel.onResumed()

            viewModel.onResumed()
            assertTrue(viewModel.uiState.value is SoulsListUiState.Content)
        }

    /**
     * A refresh that fails keeps the grid that is already on screen — replacing
     * real souls with an error because a background reload lost the network
     * would be a regression.
     */
    @Test
    fun `a failed reload keeps the grid`() =
        runTest {
            val service = FakeSoulsService(souls = listOf(soul("a")))
            val viewModel = viewModel(souls = service)
            advanceUntilIdle()
            viewModel.onResumed()
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.onResumed()
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulsListUiState.Content
            assertEquals(listOf("a"), state.souls.map { it.id })
        }
}
