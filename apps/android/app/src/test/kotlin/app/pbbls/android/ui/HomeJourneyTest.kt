package app.pbbls.android.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.wrap.DayWeight
import app.pbbls.android.core.model.wrap.MonthWeek
import app.pbbls.android.core.model.wrap.MonthWrapDto
import app.pbbls.android.core.model.wrap.WrapJson
import app.pbbls.android.core.model.wrap.toModel
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakePathWrapService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Home through the real app (#1383): a tap on a week of the month grid opens the Path on that week
 * (D8–D10), and the quick moment and New pebble chain into Complete (D7).
 */
@HiltAndroidTest
class HomeJourneyTest : AppUiTest() {
    @Inject lateinit var path: FakeMomentReadService

    @Inject lateinit var wraps: FakePathWrapService

    @Inject lateinit var writes: FakeMomentWriteService

    private fun onTab(id: Int): SemanticsNodeInteraction = compose.onNode(hasText(string(id)) and isSelectable())

    /** A moment's row on the Spine (as `PathScreenTest` finds it). */
    private fun onMoment(name: String): SemanticsNodeInteraction =
        compose.onNode(hasTestTag(JourneyTags.PATH_PEBBLE_ROW) and hasAnyDescendant(hasContentDescription(name, substring = true)))

    @Test
    fun `a week of Home's grid opens the Path on that week`() {
        val monday = WeekRollBuilder.weekStart(LocalDate.now())
        val earlier = monday.minusWeeks(3)
        val coffee = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek())
        val swim = UiFixtures.pathMoment(id = "moment-old", name = "Lake swim", happenedAt = UiFixtures.thisWeek(3))
        path.path = listOf(swim, coffee)
        // The live month as the server sends it, its columns set to the two weeks the feed holds: the earlier one
        // reads "1–7 …", the live one "8–14 …" (a column names its days by their place in the month).
        wraps.months[YearMonth.now()] =
            WrapJson
                .decodeFromString<MonthWrapDto>(requireNotNull(javaClass.getResource("/month-full.json")).readText())
                .toModel()
                .copy(
                    month = YearMonth.now(),
                    cover = null,
                    weeks =
                        listOf(
                            MonthWeek(earlier, 0, 6, 1, emptyList(), List(7) { DayWeight(0, 0) }),
                            MonthWeek(monday, 7, 13, 1, emptyList(), List(7) { DayWeight(0, 0) }),
                        ),
                )
        skipOnboarding()
        launch()
        signIn()
        onTab(R.string.tab_home).assertIsSelected()

        compose
            .onNode(
                hasAnyAncestor(hasTestTag(JourneyTags.HOME)) and
                    SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button) and
                    hasContentDescription("1–7 ", substring = true),
            ).performClick()
        compose.waitForIdle()

        onTab(R.string.tab_path).assertIsSelected()
        compose.onNodeWithText(weekHeader(earlier)).assertIsDisplayed()
        onMoment("Lake swim").assertIsDisplayed()
        onMoment("Coffee with Mo").assertDoesNotExist()
    }

    @Test
    fun `a quick moment saved with Save and open lands on Complete, and leaving it lands on Home`() {
        writes.nextMomentId = "moment-quick"
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.home_shortcut_quick).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Coffee with Mo")
        onText(R.string.home_quick_save_open).performClick()
        compose.waitForIdle()

        onText(R.string.record_valence_title).assertIsDisplayed()
        assertEquals("Coffee with Mo", writes.saved.single().name)

        pressBack()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
        onText(R.string.home_quick_save_open).assertDoesNotExist()
    }

    @Test
    fun `New pebble from Home offers one save, which chains Capture into Complete`() {
        writes.nextMomentId = "moment-new"
        skipOnboarding()
        launch()
        signIn()

        onText(R.string.home_shortcut_pebble).performClick()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_action_continue).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Walk")
        onText(R.string.capture_save).assertDoesNotExist()
        onText(R.string.capture_make_pebble_now).performClick()

        onText(R.string.record_valence_title).assertIsDisplayed()
        assertEquals("Walk", writes.saved.single().name)

        // Complete replaced Capture, so leaving it lands back on Home.
        pressBack()
        compose.onNodeWithTag(JourneyTags.HOME).assertIsDisplayed()
    }

    /** The week line's "week 41" (#1207, D3): what names the page a moment is on. */
    private fun weekHeader(monday: LocalDate): String = string(R.string.path_wrap_week, WeekRollBuilder.isoWeekNumber(monday))
}
