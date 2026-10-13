package app.pbbls.android.features.path

import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The moment read page's load and resume-refresh contract (#849, #852, #1048). */
class PebbleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the moment`() =
        runTest {
            val moment = UiFixtures.momentDetail()
            val service = FakeMomentReadService(moments = mapOf("moment-1" to moment))
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("moment-1")
            assertEquals(PebbleDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(moment, (viewModel.uiState.value as PebbleDetailUiState.Content).detail)
        }

    /** A photo-only capture with no pebble yet is a moment the page must open (D3). */
    @Test
    fun `loads a moment without a pebble or a name`() =
        runTest {
            val bare = UiFixtures.momentDetail(pebble = null, name = null)
            val service = FakeMomentReadService(moments = mapOf("moment-1" to bare))
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("moment-1")
            advanceUntilIdle()

            val detail = (viewModel.uiState.value as PebbleDetailUiState.Content).detail
            assertNull(detail.pebble)
            assertNull(detail.name)
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail()))
            service.failNext = IOException("offline")
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("moment-1")
            advanceUntilIdle()
            assertEquals(PebbleDetailUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is PebbleDetailUiState.Content)
        }

    /** Rotation, as far as a JVM test carries it: re-running `start` with the same id does not refetch. */
    @Test
    fun `starting with the same id does not refetch`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail()))
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("moment-1")
            advanceUntilIdle()
            viewModel.start("moment-1")
            advanceUntilIdle()

            assertEquals(1, service.loadedMomentIds.size)
        }

    /**
     * Edit moment and Edit pebble are separate entries with no callback back
     * into this instance (#852) — an edit saved there has to be picked up on resume.
     */
    @Test
    fun `resuming after the first time refetches the same moment`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "A walk")))
            val viewModel = PebbleDetailViewModel(service)
            viewModel.start("moment-1")
            advanceUntilIdle()

            // First resume: `start` already loaded, so this is a no-op.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.loadedMomentIds.size)

            service.moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "A longer walk"))
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(listOf("moment-1", "moment-1"), service.loadedMomentIds)
            assertEquals(
                "A longer walk",
                (viewModel.uiState.value as PebbleDetailUiState.Content).detail.name,
            )
        }

    @Test
    fun `opening a different moment loads it`() =
        runTest {
            val service =
                FakeMomentReadService(
                    moments =
                        mapOf(
                            "moment-1" to UiFixtures.momentDetail(id = "moment-1"),
                            "moment-2" to UiFixtures.momentDetail(id = "moment-2", name = "Another"),
                        ),
                )
            val viewModel = PebbleDetailViewModel(service)
            viewModel.start("moment-1")
            advanceUntilIdle()

            viewModel.start("moment-2")
            advanceUntilIdle()

            assertEquals(listOf("moment-1", "moment-2"), service.loadedMomentIds)
        }
}
