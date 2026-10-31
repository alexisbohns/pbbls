package app.pbbls.android.features.home

import app.pbbls.android.testing.FakeHomeService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Home's reads (#1383; iOS `HomeModelTests` where shared). This part: the name. */
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = FakeHomeService()

    @Test
    fun `nothing is read before the first resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = HomeViewModel(home)
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `the name lands on resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = HomeViewModel(home)

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alexis", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed name read keeps the empty name`() =
        runTest {
            // The service's failure answers "" (it logs and swallows), so the fake's "" is that read.
            val viewModel = HomeViewModel(home)

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed read on a later resume keeps the name already shown`() =
        runTest {
            home.name = "Alexis"
            val viewModel = HomeViewModel(home)
            viewModel.onResumed()
            advanceUntilIdle()

            home.name = ""
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alexis", viewModel.uiState.value.name)
        }

    @Test
    fun `a later resume picks up a changed name`() =
        runTest {
            home.name = "Alexis"
            val viewModel = HomeViewModel(home)
            viewModel.onResumed()
            advanceUntilIdle()

            home.name = "Alex"
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alex", viewModel.uiState.value.name)
        }
}
