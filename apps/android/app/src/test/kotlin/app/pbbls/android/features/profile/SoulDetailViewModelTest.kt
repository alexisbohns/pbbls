package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSoulsService
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

/** The soul detail's load, rotation-guard and pebble-delete contract (#849). */
class SoulDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: OffsetDateTime = OffsetDateTime.parse("2026-09-19T12:00:00Z")

    private fun soul(id: String) =
        SoulWithGlyph(
            id = id,
            name = "Soul $id",
            glyphId = "glyph-$id",
            glyph = Glyph(id = "glyph-$id", strokes = emptyList(), viewBox = "0 0 200 200"),
        )

    private fun pebble(id: String) =
        Pebble(
            id = id,
            name = "Pebble $id",
            happenedAt = now,
            createdAt = now,
            intensity = 2,
            positiveness = 1,
        )

    private fun viewModel(
        souls: FakeSoulsService = FakeSoulsService(),
        writes: FakePebbleWriteService = FakePebbleWriteService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = SoulDetailViewModel(souls, writes, refs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val viewModel = viewModel(souls = service)

            viewModel.start("a")
            assertEquals(SoulDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulDetailUiState.Content
            assertEquals("a", state.soul.id)
            assertEquals(listOf("p1"), state.pebbles.map { it.id })
        }

    /**
     * The rotation criterion. The screen's `LaunchedEffect(soulId)` re-runs on
     * every configuration change; the guard is what keeps that free.
     */
    @Test
    fun `start is idempotent for the same soul`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val viewModel = viewModel(souls = service)

            viewModel.start("a")
            advanceUntilIdle()
            repeat(3) { viewModel.start("a") }
            advanceUntilIdle()

            assertEquals(1, service.loadSoulCount)
        }

    @Test
    fun `a failed load becomes Error, and retry recovers`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            service.failNext = IOException("offline")
            val viewModel = viewModel(souls = service)

            viewModel.start("a")
            advanceUntilIdle()
            assertEquals(
                R.string.soul_detail_load_error,
                (viewModel.uiState.value as SoulDetailUiState.Error).messageRes,
            )

            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is SoulDetailUiState.Content)
        }

    /** A load with no `start` has no id to fetch, so it must not touch the server. */
    @Test
    fun `retry before start is a no-op`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val viewModel = viewModel(souls = service)

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(0, service.loadSoulCount)
            assertEquals(SoulDetailUiState.Loading, viewModel.uiState.value)
        }

    // MARK: - Pebble delete

    /**
     * The write this ViewModel exists for: the delete ran in
     * `rememberCoroutineScope`, so popping between the request and the reload
     * left a pebble on screen the server had already dropped.
     */
    @Test
    fun `confirmDelete deletes the pebble and reloads`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val writes = FakePebbleWriteService()
            val refs = FakeReferenceDataService()
            val viewModel = viewModel(souls = service, writes = writes, refs = refs)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.requestDelete(pebble("p1"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("p1"), writes.deletedPebbleIds)
            assertEquals(1, refs.refreshSoulsCount)
            assertEquals(2, service.loadSoulCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    @Test
    fun `a failed delete reports and leaves the list alone`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val writes = FakePebbleWriteService()
            val viewModel = viewModel(souls = service, writes = writes)
            viewModel.start("a")
            advanceUntilIdle()

            writes.failNext = IOException("offline")
            viewModel.requestDelete(pebble("p1"))
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            assertEquals(1, service.loadSoulCount)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    // MARK: - Covers

    @Test
    fun `saving an edited pebble closes its cover and reloads`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val viewModel = viewModel(souls = service)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.openPebble("p1")
            assertEquals("p1", viewModel.covers.value.editingPebbleId)

            viewModel.onPebbleSaved()
            advanceUntilIdle()

            assertNull(viewModel.covers.value.editingPebbleId)
            assertEquals(2, service.loadSoulCount)
        }

    /** A failed refresh keeps the content that is already on screen. */
    @Test
    fun `a failed reload keeps the detail`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val viewModel = viewModel(souls = service)
            viewModel.start("a")
            advanceUntilIdle()

            service.failNext = IOException("offline")
            viewModel.onPebbleSaved()
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulDetailUiState.Content
            assertEquals(listOf("p1"), state.pebbles.map { it.id })
        }

    // MARK: - Returning from the edit form

    /**
     * The gap promotion opened (#852): `SoulForm` is now a separate entry with no
     * callback back into this instance, so a name edited there has to be
     * picked up by a resume — same mechanism as [SoulsListViewModel.onResumed].
     */
    @Test
    fun `returning from the edit form re-reads the soul`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"), pebbles = listOf(pebble("p1")))
            val viewModel = viewModel(souls = service)
            viewModel.start("a")
            advanceUntilIdle()
            assertEquals(1, service.loadSoulCount)

            // First resume: the screen just opened, `start` already loaded.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.loadSoulCount)

            // Second: back from the edit form.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(2, service.loadSoulCount)
        }
}
