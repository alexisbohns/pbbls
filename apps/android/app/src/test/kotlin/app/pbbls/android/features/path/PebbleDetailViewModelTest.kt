package app.pbbls.android.features.path

import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.testing.FakePebbleDetailService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/** The read cover's load and reload-key contract (#849). */
class PebbleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun detail(
        id: String = "pebble-1",
        name: String = "A walk",
    ) = PebbleDetail(
        id = id,
        name = name,
        happenedAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
        intensity = 2,
        positiveness = 1,
        visibility = Visibility.SECRET,
        emotion = EmotionRef(id = "emotion-1", slug = "calm", name = "Calm"),
    )

    @Test
    fun `loads the pebble`() =
        runTest {
            val service = FakePebbleDetailService(detail())
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("pebble-1", reloadKey = 0)
            assertEquals(PebbleDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(
                "A walk",
                (viewModel.uiState.value as PebbleDetailUiState.Content).detail.name,
            )
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val service = FakePebbleDetailService(detail())
            service.failNext = IOException("offline")
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("pebble-1", reloadKey = 0)
            advanceUntilIdle()
            assertEquals(PebbleDetailUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is PebbleDetailUiState.Content)
        }

    /**
     * Rotation: the screen's effect re-runs with the same (id, key) pair and
     * must not flash the spinner back over a page being read.
     */
    @Test
    fun `the same id and key does not refetch`() =
        runTest {
            val service = FakePebbleDetailService(detail())
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("pebble-1", reloadKey = 0)
            advanceUntilIdle()
            viewModel.start("pebble-1", reloadKey = 0)
            advanceUntilIdle()

            assertEquals(1, service.loadCalls.size)
        }

    /** An edit saved: same pebble, different contents. */
    @Test
    fun `a bumped reload key refetches the same pebble`() =
        runTest {
            val service = FakePebbleDetailService(detail(name = "A walk"))
            val viewModel = PebbleDetailViewModel(service)
            viewModel.start("pebble-1", reloadKey = 0)
            advanceUntilIdle()

            service.detail = detail(name = "A longer walk")
            viewModel.start("pebble-1", reloadKey = 1)
            advanceUntilIdle()

            assertEquals(listOf("pebble-1", "pebble-1"), service.loadCalls)
            assertEquals(
                "A longer walk",
                (viewModel.uiState.value as PebbleDetailUiState.Content).detail.name,
            )
        }

    @Test
    fun `opening a different pebble loads it`() =
        runTest {
            val service = FakePebbleDetailService(detail(id = "pebble-1"))
            val viewModel = PebbleDetailViewModel(service)
            viewModel.start("pebble-1", reloadKey = 0)
            advanceUntilIdle()

            service.detail = detail(id = "pebble-2", name = "Another")
            viewModel.start("pebble-2", reloadKey = 0)
            advanceUntilIdle()

            assertEquals(listOf("pebble-1", "pebble-2"), service.loadCalls)
        }
}
