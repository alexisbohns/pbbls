package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.inMemorySettings
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

/** The collection detail's load, grouping and moment-delete contract (#849, #1048). */
class CollectionDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun collection(id: String) = Collection(id = id, name = "Collection $id", mode = CollectionMode.STACK, pebbleCount = 2)

    private fun moment(
        id: String,
        happenedAt: String,
    ): MomentDetail =
        UiFixtures
            .momentDetail(id = id, name = "Moment $id")
            .copy(happenedAt = OffsetDateTime.parse(happenedAt), createdAt = OffsetDateTime.parse(happenedAt))

    private fun viewModel(
        collections: FakeCollectionsService = FakeCollectionsService(),
        moments: FakeMomentReadService = FakeMomentReadService(),
        writes: FakeMomentWriteService = FakeMomentWriteService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = CollectionDetailViewModel(collections, moments, writes, refs, HapticsPreference(inMemorySettings()))

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service =
                FakeCollectionsService(collection = collection("a"))
            val moments = FakeMomentReadService(inCollection = listOf(moment("p1", "2026-09-19T12:00:00Z")))
            val viewModel = viewModel(collections = service, moments = moments)

            viewModel.start("a")
            assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionDetailUiState.Content
            assertEquals("a", state.collection.id)
            assertEquals(moments.inCollection.map { it.toPathMoment() }, state.moments)
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
    fun `Content groups the moments by month, newest first`() =
        runTest {
            val service =
                FakeCollectionsService(collection = collection("a"))
            val moments =
                FakeMomentReadService(
                    inCollection =
                        listOf(
                            moment("sep", "2026-09-19T12:00:00Z"),
                            moment("aug", "2026-08-02T12:00:00Z"),
                            moment("aug2", "2026-08-01T12:00:00Z"),
                        ),
                )
            val viewModel = viewModel(collections = service, moments = moments)
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
                FakeCollectionsService(collection = collection("a"))
            val moments = FakeMomentReadService(inCollection = listOf(moment("p1", "2026-09-19T12:00:00Z")))
            val viewModel = viewModel(collections = service, moments = moments)
            viewModel.start("a")
            advanceUntilIdle()

            val state = viewModel.uiState.value as CollectionDetailUiState.Content
            assertSame(state.groups, state.groups)
        }

    // MARK: - Delete

    @Test
    fun `confirmDelete deletes the moment and reloads`() =
        runTest {
            val service =
                FakeCollectionsService(collection = collection("a"))
            val moments = FakeMomentReadService(inCollection = listOf(moment("p1", "2026-09-19T12:00:00Z")))
            val writes = FakeMomentWriteService()
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(collections = service, moments = moments, writes = writes, refs = refs)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.requestDelete(moment("p1", "2026-09-19T12:00:00Z").toPathMoment())
            viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertEquals(listOf("p1"), writes.deletedMoments)
            assertEquals(1, refs.refreshCollectionsCount)
            assertEquals(2, service.loadCollectionCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    /** R8: keeping the moment sends the PEBBLE id to `delete_pebble`; the row leaves the list on reload. */
    @Test
    fun `deleting only the pebble keeps the moment`() =
        runTest {
            val service = FakeCollectionsService(collection = collection("a"))
            val moments = FakeMomentReadService(inCollection = listOf(moment("p1", "2026-09-19T12:00:00Z")))
            val writes = FakeMomentWriteService()
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(collections = service, moments = moments, writes = writes, refs = refs)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.requestDelete(moment("p1", "2026-09-19T12:00:00Z").toPathMoment())
            viewModel.confirmDelete(MomentDeleteChoice.PEBBLE_ONLY)
            advanceUntilIdle()

            assertEquals(listOf("pebble-1"), writes.deletedPebbles)
            assertTrue(writes.deletedMoments.isEmpty())
            assertEquals(1, refs.refreshCollectionsCount)
            assertEquals(2, service.loadCollectionCount)
        }

    @Test
    fun `a failed delete reports and leaves the list alone`() =
        runTest {
            val service =
                FakeCollectionsService(collection = collection("a"))
            val moments = FakeMomentReadService(inCollection = listOf(moment("p1", "2026-09-19T12:00:00Z")))
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(collections = service, moments = moments, writes = writes)
            viewModel.start("a")
            advanceUntilIdle()

            writes.failNext = IOException("offline")
            viewModel.requestDelete(moment("p1", "2026-09-19T12:00:00Z").toPathMoment())
            viewModel.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            assertEquals(1, service.loadCollectionCount)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    // MARK: - Returning from the edit form

    /**
     * The gap promotion opened (#852): `CollectionForm` is now a separate entry with no
     * callback back into this instance, so a name edited there has to be
     * picked up by a resume — same mechanism as
     * [CollectionsListViewModel.onResumed].
     */
    @Test
    fun `returning from the edit form re-reads the collection`() =
        runTest {
            val service = FakeCollectionsService(collection = collection("a"))
            val viewModel = viewModel(collections = service)
            viewModel.start("a")
            advanceUntilIdle()
            assertEquals(1, service.loadCollectionCount)

            // First resume: the screen just opened, `start` already loaded.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.loadCollectionCount)

            // Second: back from the edit form.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.loadCollectionCount)
        }
}
