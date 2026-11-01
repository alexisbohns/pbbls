package app.pbbls.android.features.home.wraps

import app.pbbls.android.R
import app.pbbls.android.core.data.DisplayPreferences
import app.pbbls.android.core.data.HapticsPreference
import app.pbbls.android.core.data.HomeShortcutsPreference
import app.pbbls.android.core.data.TabIntent
import app.pbbls.android.core.data.TabIntents
import app.pbbls.android.core.model.home.HomeWrapMonth
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import app.pbbls.android.features.home.HomeSection
import app.pbbls.android.features.home.HomeViewModel
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeHomeService
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakePathWrapService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.inMemorySettings
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** The wrap sheet (#1383 D12; iOS `HomeWrapSheet`): the month's state, the unfold, the page, the week tap. */
class HomeWrapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val october = YearMonth.of(2026, 10)
    private val revealedAt = Instant.parse("2026-11-01T09:00:00Z")
    private val folded = HomeWrapMonth(october, count = 23, revealedAt = null, cover = null)

    private val home = FakeHomeService(months = listOf(folded), revealedAt = revealedAt)
    private val wraps = FakePathWrapService()
    private val tabIntents = TabIntents()
    private val settings = inMemorySettings()

    private fun viewModel() = HomeWrapViewModel(home, wraps, tabIntents, DisplayPreferences(settings), HapticsPreference(settings))

    /** The server's own month, moved to October. */
    private fun octoberWrap(): MonthWrap =
        WrapJson
            .decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText())
            .toModel()
            .copy(month = october)

    @Test
    fun `a folded month opens folded`() =
        runTest {
            val viewModel = viewModel()

            viewModel.start(october)
            assertEquals(HomeWrapUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(HomeWrapUiState.Folded(folded), viewModel.uiState.value)
            assertTrue("a folded month reads no page", wraps.monthCalls.isEmpty())
        }

    @Test
    fun `a revealed month opens on its page`() =
        runTest {
            home.months = listOf(folded.copy(revealedAt = revealedAt))
            wraps.months[october] = octoberWrap()
            val viewModel = viewModel()

            viewModel.start(october)
            advanceUntilIdle()

            assertEquals(
                HomeWrapUiState.Revealed(folded.copy(revealedAt = revealedAt), HomeSection.Ready(octoberWrap())),
                viewModel.uiState.value,
            )
        }

    @Test
    fun `unfold flips the sheet to the page, and Home's card on its next read`() =
        runTest {
            wraps.months[october] = octoberWrap()
            val sheet = viewModel()
            val homeViewModel =
                HomeViewModel(
                    home,
                    wraps,
                    tabIntents,
                    HomeShortcutsPreference(settings),
                    HapticsPreference(settings),
                    FakeAchievementsService(),
                    FakeMomentReadService(),
                )
            homeViewModel.onResumed()
            sheet.start(october)
            advanceUntilIdle()

            sheet.unfold()
            advanceUntilIdle()

            assertEquals(listOf(october), home.reveals)
            val state = sheet.uiState.value
            assertTrue("the sheet is revealed: $state", state is HomeWrapUiState.Revealed)
            assertEquals(HomeSection.Ready(octoberWrap()), (state as HomeWrapUiState.Revealed).deck)

            // Home's card follows through the server, on the resume after the sheet.
            homeViewModel.onResumed()
            advanceUntilIdle()
            val card = (homeViewModel.uiState.value.wraps as HomeSection.Ready).value.months.single()
            assertFalse("Home's card is unfolded", card.isFolded)
        }

    @Test
    fun `a failed unfold keeps the month folded and says so`() =
        runTest {
            home.failNextReveal = RuntimeException("offline")
            val viewModel = viewModel()
            viewModel.start(october)
            advanceUntilIdle()

            viewModel.unfold()
            assertEquals(HomeWrapUiState.Folded(folded, Unfold.RUNNING), viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(HomeWrapUiState.Folded(folded, Unfold.FAILED), viewModel.uiState.value)

            // A second try goes through.
            viewModel.unfold()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is HomeWrapUiState.Revealed)
        }

    @Test
    fun `a failed read of the months shows the error, and Try again reads them back`() =
        runTest {
            home.failNextWraps = RuntimeException("offline")
            val viewModel = viewModel()
            viewModel.start(october)
            advanceUntilIdle()
            assertEquals(HomeWrapUiState.Error(R.string.home_wraps_error), viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()
            assertEquals(HomeWrapUiState.Folded(folded), viewModel.uiState.value)
        }

    @Test
    fun `a failed page with no copy says so, and Try again reads it back`() =
        runTest {
            home.months = listOf(folded.copy(revealedAt = revealedAt))
            val viewModel = viewModel()
            viewModel.start(october)
            advanceUntilIdle()
            assertEquals(HomeSection.Failed, (viewModel.uiState.value as HomeWrapUiState.Revealed).deck)

            wraps.months[october] = octoberWrap()
            viewModel.retry()
            advanceUntilIdle()
            assertEquals(HomeSection.Ready(octoberWrap()), (viewModel.uiState.value as HomeWrapUiState.Revealed).deck)
        }

    @Test
    fun `a month no longer among Home's closes the sheet`() =
        runTest {
            home.months = emptyList()
            val viewModel = viewModel()
            val effects = recordEffects(viewModel.effects)

            viewModel.start(october)
            advanceUntilIdle()

            assertEquals(listOf(HomeWrapEffect.Close), effects.values)
            effects.stop()
        }

    @Test
    fun `a week tap leaves the Path a focus on that week`() =
        runTest {
            val viewModel = viewModel()
            val monday = LocalDate.of(2026, 10, 12)

            viewModel.onWeek(monday)

            assertEquals(TabIntent.FocusWeek(monday), tabIntents.pending.value)
        }
}
