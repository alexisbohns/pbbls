package app.pbbls.android.features.path

import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** The drafts list's load and optimistic delete (#849). */
class DraftsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun record(id: String) = PebbleDraftRecord(id = id, payload = PebbleDraftPayload(name = id), updatedAt = OffsetDateTime.now())

    @Test
    fun `loads the list`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(record("d1"), record("d2")))
            val viewModel = DraftsViewModel(drafts)

            viewModel.start(reloadKey = 0)
            assertEquals(DraftsUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as DraftsUiState.Content
            assertEquals(listOf("d1", "d2"), state.drafts.map { it.id })
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(record("d1")))
            drafts.failNext = IOException("offline")
            val viewModel = DraftsViewModel(drafts)

            viewModel.start(reloadKey = 0)
            advanceUntilIdle()
            assertEquals(DraftsUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is DraftsUiState.Content)
        }

    /**
     * The key is the host saying "a draft was written, read it again". Keying on
     * it rather than reloading unconditionally is what lets a rotation re-run
     * the screen's effect without refetching.
     */
    @Test
    fun `the same reload key does not refetch, a new one does`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(record("d1")))
            val viewModel = DraftsViewModel(drafts)

            viewModel.start(reloadKey = 0)
            advanceUntilIdle()
            viewModel.start(reloadKey = 0)
            advanceUntilIdle()

            val state = viewModel.uiState.value as DraftsUiState.Content
            assertEquals(1, state.drafts.size)

            drafts.records.add(record("d2"))
            viewModel.start(reloadKey = 1)
            advanceUntilIdle()

            assertEquals(2, (viewModel.uiState.value as DraftsUiState.Content).drafts.size)
        }

    @Test
    fun `deleting drops the row immediately and removes it on the server`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(record("d1"), record("d2")))
            val viewModel = DraftsViewModel(drafts)
            viewModel.start(reloadKey = 0)
            advanceUntilIdle()

            viewModel.delete(record("d1"))

            // Optimistic: gone from the list before the server is asked.
            assertEquals(
                listOf("d2"),
                (viewModel.uiState.value as DraftsUiState.Content).drafts.map { it.id },
            )

            advanceUntilIdle()
            assertEquals(listOf("d2"), drafts.records.map { it.id })
        }

    /**
     * The delete ran in `rememberCoroutineScope`, so leaving mid-delete removed
     * the row server-side and skipped the reconcile — the Path's badge kept
     * counting a draft that no longer existed until the next cold start.
     */
    @Test
    fun `a failed delete reloads the truth back in`() =
        runTest {
            val drafts = FakePebbleDraftsService(mutableListOf(record("d1"), record("d2")))
            val viewModel = DraftsViewModel(drafts)
            viewModel.start(reloadKey = 0)
            advanceUntilIdle()

            drafts.failNext = IOException("offline")
            viewModel.delete(record("d1"))
            advanceUntilIdle()

            // The optimistic removal is undone by the reload.
            assertEquals(
                listOf("d1", "d2"),
                (viewModel.uiState.value as DraftsUiState.Content).drafts.map { it.id },
            )
        }
}
