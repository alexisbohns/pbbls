package app.pbbls.android.features.home

import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import app.pbbls.android.testing.FakeHomeService
import app.pbbls.android.testing.FakePathWrapService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Home's reads (#1383; iOS `HomeModelTests` where shared): the name, the month (D9), the week tap (D10). */
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = FakeHomeService()
    private val wraps = FakePathWrapService()
    private val tabIntents = TabIntents()

    private fun viewModel() = HomeViewModel(home, wraps, tabIntents)

    /** The server's own month, moved to the live one, [pebbles] telling two reads apart. */
    private fun month(pebbles: Int): MonthWrap =
        WrapJson
            .decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText())
            .toModel()
            .copy(month = YearMonth.now(), pebbles = pebbles)

    @Test
    fun `nothing is read before the first resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `the name lands on resume`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alexis", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed name read keeps the empty name`() =
        runTest {
            // The service's failure answers "" (it logs and swallows), so the fake's "" is that read.
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("", viewModel.uiState.value.name)
        }

    @Test
    fun `a failed read on a later resume keeps the name already shown`() =
        runTest {
            home.name = "Alexis"
            val viewModel = viewModel()
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
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            home.name = "Alex"
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals("Alex", viewModel.uiState.value.name)
        }

    // MARK: - The month (D9)

    @Test
    fun `the month paints from the device's copy, then from the network`() =
        runTest {
            val kept = month(pebbles = 1)
            val fresh = month(pebbles = 2)
            wraps.keptMonths[YearMonth.now()] = kept
            wraps.months[YearMonth.now()] = fresh
            val gate = CompletableDeferred<Unit>()
            wraps.monthGate = gate
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(HomeSection.Ready(kept), viewModel.uiState.value.month)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(HomeSection.Ready(fresh), viewModel.uiState.value.month)
            assertEquals(listOf(YearMonth.now()), wraps.monthCalls)
        }

    @Test
    fun `a failed read with no copy hides the month`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(HomeSection.Loading, viewModel.uiState.value.month)

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Failed, viewModel.uiState.value.month)
        }

    @Test
    fun `a failed read keeps the device's copy`() =
        runTest {
            val kept = month(pebbles = 1)
            wraps.keptMonths[YearMonth.now()] = kept
            val viewModel = viewModel()

            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Ready(kept), viewModel.uiState.value.month)
        }

    @Test
    fun `a failed read on a later resume keeps the month already shown`() =
        runTest {
            val fresh = month(pebbles = 2)
            wraps.months[YearMonth.now()] = fresh
            val viewModel = viewModel()
            viewModel.onResumed()
            advanceUntilIdle()

            wraps.months.clear()
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(HomeSection.Ready(fresh), viewModel.uiState.value.month)
            assertEquals(2, wraps.monthCalls.size)
        }

    // MARK: - The week tap (D10)

    @Test
    fun `a week tap leaves the Path a focus on that week`() =
        runTest {
            val viewModel = viewModel()
            val monday = LocalDate.of(2026, 10, 12)

            viewModel.onWeek(monday)

            assertEquals(TabIntent.FocusWeek(monday), tabIntents.pending.value)
        }
}
