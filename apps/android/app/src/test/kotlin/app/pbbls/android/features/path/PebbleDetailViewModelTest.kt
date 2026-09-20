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

/** The pushed detail entry's load and resume-refresh contract (#849, #852). */
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

            viewModel.start("pebble-1")
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

            viewModel.start("pebble-1")
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
            val service = FakePebbleDetailService(detail())
            val viewModel = PebbleDetailViewModel(service)

            viewModel.start("pebble-1")
            advanceUntilIdle()
            viewModel.start("pebble-1")
            advanceUntilIdle()

            assertEquals(1, service.loadCalls.size)
        }

    /**
     * `EditPebble` is a separate entry with no callback back into this instance
     * (#852) — an edit saved there has to be picked up on resume.
     */
    @Test
    fun `resuming after the first time refetches the same pebble`() =
        runTest {
            val service = FakePebbleDetailService(detail(name = "A walk"))
            val viewModel = PebbleDetailViewModel(service)
            viewModel.start("pebble-1")
            advanceUntilIdle()

            // First resume: `start` already loaded, so this is a no-op.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.loadCalls.size)

            service.detail = detail(name = "A longer walk")
            viewModel.onResumed()
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
            viewModel.start("pebble-1")
            advanceUntilIdle()

            service.detail = detail(id = "pebble-2", name = "Another")
            viewModel.start("pebble-2")
            advanceUntilIdle()

            assertEquals(listOf("pebble-1", "pebble-2"), service.loadCalls)
        }
}
