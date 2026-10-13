package app.pbbls.android.features.profile

import app.pbbls.android.R
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeSoulsService
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

/** The soul detail's load, rotation-guard and moment-delete contract (#849, #1048). */
class SoulDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun soul(id: String) =
        SoulWithGlyph(
            id = id,
            name = "Soul $id",
            glyphId = "glyph-$id",
            glyph = Glyph(id = "glyph-$id", strokes = emptyList(), viewBox = "0 0 200 200"),
        )

    private fun moment(id: String): MomentDetail = UiFixtures.momentDetail(id = id, name = "Moment $id")

    private fun viewModel(
        souls: FakeSoulsService = FakeSoulsService(),
        moments: FakeMomentReadService = FakeMomentReadService(),
        writes: FakeMomentWriteService = FakeMomentWriteService(),
        refs: FakeReferenceDataService = FakeReferenceDataService(),
    ) = SoulDetailViewModel(souls, moments, writes, refs)

    @Test
    fun `starts Loading and resolves to Content`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val fixtures = listOf(moment("m1"), UiFixtures.momentDetail(id = "m2", name = null, pebble = null))
            val viewModel = viewModel(souls = service, moments = FakeMomentReadService(withSoul = fixtures))

            viewModel.start("a")
            assertEquals(SoulDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulDetailUiState.Content
            assertEquals("a", state.soul.id)
            assertEquals(fixtures.map { it.toPathMoment() }, state.moments)
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

    // MARK: - Delete

    /**
     * The write this ViewModel exists for: the delete ran in
     * `rememberCoroutineScope`, so popping between the request and the reload
     * left a pebble on screen the server had already dropped.
     */
    @Test
    fun `confirmDelete deletes the moment and reloads`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val writes = FakeMomentWriteService()
            val refs = FakeReferenceDataService()
            val moments = FakeMomentReadService(withSoul = listOf(moment("m1")))
            val viewModel = viewModel(souls = service, moments = moments, writes = writes, refs = refs)
            viewModel.start("a")
            advanceUntilIdle()

            viewModel.requestDelete(moment("m1").toPathMoment())
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("m1"), writes.deletedMoments)
            assertEquals(1, refs.refreshSoulsCount)
            assertEquals(2, service.loadSoulCount)
            assertNull(viewModel.covers.value.pendingDeletion)
        }

    @Test
    fun `a failed delete reports and leaves the list alone`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val writes = FakeMomentWriteService()
            val viewModel = viewModel(souls = service, writes = writes)
            viewModel.start("a")
            advanceUntilIdle()

            writes.failNext = IOException("offline")
            viewModel.requestDelete(moment("m1").toPathMoment())
            viewModel.confirmDelete()
            advanceUntilIdle()

            assertTrue(viewModel.covers.value.didDeleteFail)
            assertEquals(1, service.loadSoulCount)

            viewModel.dismissDeleteError()
            assertFalse(viewModel.covers.value.didDeleteFail)
        }

    /** A failed refresh keeps the content that is already on screen. */
    @Test
    fun `a failed reload keeps the detail`() =
        runTest {
            val service = FakeSoulsService(soul = soul("a"))
            val viewModel = viewModel(souls = service, moments = FakeMomentReadService(withSoul = listOf(moment("m1"))))
            viewModel.start("a")
            advanceUntilIdle()

            // The first resume is the screen opening; the second is a return
            // from the pebble editor or the soul form, and reloads.
            viewModel.onResumed()
            service.failNext = IOException("offline")
            viewModel.onResumed()
            advanceUntilIdle()

            val state = viewModel.uiState.value as SoulDetailUiState.Content
            assertEquals(listOf("m1"), state.moments.map { it.id })
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
            val service = FakeSoulsService(soul = soul("a"))
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
