package app.pbbls.android.features.home.guides

import app.pbbls.android.core.model.home.GuideIllustration
import app.pbbls.android.testing.FakeHomeService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.TestGuides
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A guide's page (#1383 D13): found by its slug, or "This reading isn't available". */
class GuideViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stones = TestGuides.guide("stones", 1, GuideIllustration.STONES)
    private val souls = TestGuides.guide("souls", 2, GuideIllustration.SOULS)
    private val home = FakeHomeService(guideRows = listOf(stones, souls))

    @Test
    fun `the page loads before the read lands`() =
        runTest {
            val viewModel = GuideViewModel(home)

            assertEquals(GuideUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `the slug's reading opens`() =
        runTest {
            val viewModel = GuideViewModel(home)

            viewModel.start("souls")
            advanceUntilIdle()

            assertEquals(GuideUiState.Found(souls), viewModel.uiState.value)
        }

    @Test
    fun `an unknown slug is a missing reading`() =
        runTest {
            val viewModel = GuideViewModel(home)

            viewModel.start("tides")
            advanceUntilIdle()

            assertEquals(GuideUiState.Missing, viewModel.uiState.value)
        }

    @Test
    fun `a failed read is a missing reading, not a spinner that never ends`() =
        runTest {
            home.failNextGuides = RuntimeException("offline")
            val viewModel = GuideViewModel(home)

            viewModel.start("stones")
            advanceUntilIdle()

            assertEquals(GuideUiState.Missing, viewModel.uiState.value)
        }

    @Test
    fun `a second start for the same slug reads nothing again`() =
        runTest {
            val viewModel = GuideViewModel(home)

            viewModel.start("stones")
            advanceUntilIdle()
            viewModel.start("stones")
            advanceUntilIdle()

            assertEquals(1, home.guidesCalls)
            assertEquals(GuideUiState.Found(stones), viewModel.uiState.value)
        }
}
