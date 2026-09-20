package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime
import java.time.YearMonth

/** The collection detail's load, grouping and pebble-delete contract (#849). */
class CollectionDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun collection(id: String) = Collection(id = id, name = "Collection $id", mode = CollectionMode.STACK, pebbleCount = 2)

    private fun pebble(
        id: String,
        happenedAt: String,
    ) = Pebble(
        id = id,
        name = "Pebble $id",
        happenedAt = OffsetDateTime.parse(happenedAt),
        createdAt = OffsetDateTime.parse(happenedAt),
        intensity = 2,
        positiveness = 1,
    )

    private fun viewModel(
        collections: FakeCollectionsService = FakeCollectionsService(),
        writes: FakePebbleWriteService = FakePebbleWriteService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = CollectionDetailViewModel(collections, writes, refs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service =
                FakeCollectionsService(
                    collection = collection("a"),
                    pebbles = listOf(pebble("p1", "2026-09-19T12:00:00Z")),
                )
            val viewModel = viewModel(collections = service)

            viewModel.start("a")
            assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionDetailUiState.Content
            assertEquals("a", state.collection.id)
            assertEquals(listOf("p1"), state.pebbles.map { it.id })
        }

    @Test
    fun `start is idempotent for the same collection`() =
        runTest {
            val service = FakeCollectionsService(collection = collection("a"))
            val viewModel = viewModel(collections = service)

            viewModel.start("a")
            advanceUntilIdle()
            repeat(3) { viewModel.start("a") }
            advanceUntilIdle()

            assertEquals(1, service.loadCollectionCount)
        }

    @Test
    fun `a failed load becomes Error, and retry recovers`() =
        runTest {
            val service = FakeCollectionsService(collection = collection("a"))
            service.failNext = IOException("offline")
            val viewModel = viewModel(collections = service)

            viewModel.start("a")
            advanceUntilIdle()
            assertEquals(
                R.string.soul_detail_load_error,
                (viewModel.uiState.value as CollectionDetailUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is CollectionDetailUiState.Content)
        }

    // MARK: - Month grouping

    /**
     * The grouping the screen used to recompute in a `remember(pebbles)` is a
     * property of the state value now.
     */
    @Test
    fun `Content groups the pebbles by month, newest first`() =
        runTest {
            val service =
                FakeCollectionsService(
                    collection = collection("a"),
                    pebbles =
                        listOf(
                            pebble("sep", "2026-09-19T12:00:00Z"),
                            pebble("aug", "2026-08-02T12:00:00Z"),
                            pebble("aug2", "2026-08-01T12:00:00Z"),
                        ),
                )
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionDetailUiState.Content
            // Built against the ViewModel's own zone, so the assertion holds
            // wherever the test runs.
            val expected =
                listOf(
                    YearMonth.from(OffsetDateTime.parse("2026-09-19T12:00:00Z").atZoneSameInstant(state.zone)),
                    YearMonth.from(OffsetDateTime.parse("2026-08-02T12:00:00Z").atZoneSameInstant(state.zone)),
                )
            assertEquals(expected, state.groups.map { it.first })
            assertEquals(listOf("aug", "aug2"), state.groups[1].second.map { it.id })
        }

    /** `by lazy` means the grouping is computed once per state value, not per read. */
    @Test
    fun `the derived groups are the same instance on every read`() =
        runTest {
            val service =
                FakeCollectionsService(
                    collection = collection("a"),
                    pebbles = listOf(pebble("p1", "2026-09-19T12:00:00Z")),
                )
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionDetailUiState.Content
            assertSame(state.groups, state.groups)
        }

    // MARK: - Pebble delete

    @Test
    fun `confirmDelete deletes the pebble and reloads`() =
        runTest {
            val service =
                FakeCollectionsService(
                    collection = collection("a"),
                    pebbles = listOf(pebble("p1", "2026-09-19T12:00:00Z")),
                )
            val writes = FakePebbleWriteService()
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(collections = service, writes = writes, refs = refs)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.requestDelete(pebble("p1", "2026-09-19T12:00:00Z"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("p1"), writes.deletedPebbleIds)
            assertEquals(1, refs.refreshCollectionsCount)
            assertEquals(2, service.loadCollectionCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    @Test
    fun `a failed delete reports and leaves the list alone`() =
        runTest {
            val service =
                FakeCollectionsService(
                    collection = collection("a"),
                    pebbles = listOf(pebble("p1", "2026-09-19T12:00:00Z")),
                )
            val writes = FakePebbleWriteService()
            val viewModel = viewModel(collections = service, writes = writes)
            viewModel.start("a")
            advanceUntilIdle()

            writes.failNext = IOException("offline")
            viewModel.requestDelete(pebble("p1", "2026-09-19T12:00:00Z"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            assertEquals(1, service.loadCollectionCount)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    // MARK: - Covers

    @Test
    fun `saving an edited pebble closes its cover and reloads`() =
        runTest {
            val service = FakeCollectionsService(collection = collection("a"))
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.openPebble("p1")
            assertEquals("p1", viewModel.covers.value.editingPebbleId)

            viewModel.onPebbleSaved()
            advanceUntilIdle()

            assertNull(viewModel.covers.value.editingPebbleId)
            assertEquals(2, service.loadCollectionCount)
        }
}
