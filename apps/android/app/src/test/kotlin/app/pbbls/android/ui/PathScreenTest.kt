package app.pbbls.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.features.path.PathFocusRequests
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Path's load, error and recovery through the real screen (#857).
 *
 * Path's error state has no retry button — on iOS either (`PathView`), so this
 * is parity, not a gap this suite papers over. Recovery is the resume refresh:
 * leaving the app and coming back re-reads the timeline.
 */
@HiltAndroidTest
class PathScreenTest : AppUiTest() {
    @Inject lateinit var path: FakeMomentReadService

    @Inject lateinit var focusRequests: PathFocusRequests

    /**
     * A moment's row on the Spine. The entry reads as one button whose description opens
     * with the name, so there is no text node to find; the row is the tagged ancestor.
     */
    private fun onMoment(name: String): SemanticsNodeInteraction =
        compose.onNode(hasTestTag(JourneyTags.PATH_PEBBLE_ROW) and hasAnyDescendant(hasContentDescription(name, substring = true)))

    private fun signedIn() {
        skipOnboarding()
        launch()
        signIn()
        openPath()
    }

    @Test
    fun `a loaded timeline shows this week's moments`() {
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek()))

        signedIn()

        onMoment("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
    }

    /** A photo-only capture has no name and no pebble yet; it is still on the timeline (R3). */
    @Test
    fun `an untitled moment reads Untitled`() {
        path.path = listOf(UiFixtures.pathMoment(name = null, pebble = null, happenedAt = UiFixtures.thisWeek()))

        signedIn()

        onMoment(string(R.string.path_spine_untitled)).assertIsDisplayed()
    }

    @Test
    fun `a failed first load shows the error, and no new-pebble button`() {
        path.failNext = IOException("offline")

        signedIn()

        onText(R.string.path_load_error).assertIsDisplayed()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertDoesNotExist()
    }

    @Test
    fun `coming back to the app after a failed load recovers the timeline`() {
        path.failNext = IOException("offline")
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek()))
        signedIn()
        onText(R.string.path_load_error).assertIsDisplayed()

        backgroundAndReturn()

        onText(R.string.path_load_error).assertDoesNotExist()
        onMoment("Coffee with Mo").assertIsDisplayed()
        assertEquals(2, path.loadPathCount)
    }

    @Test
    fun `a failed refresh keeps the timeline already on screen`() {
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek()))
        signedIn()

        path.failNext = IOException("offline")
        backgroundAndReturn()

        onMoment("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
    }

    /**
     * R14 on a reload that brings new weeks: the requested moment's week wins.
     * The pager's swipe → focus reporter re-subscribes when the entries change,
     * and used to report the page it was still on, overwriting the request.
     */
    @Test
    fun `a focus request pages the path to that moment's week on the next load`() {
        val thisWeek = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek())
        path.path = listOf(thisWeek)
        signedIn()
        onMoment("Coffee with Mo").assertIsDisplayed()

        val threeWeeksBack =
            UiFixtures.pathMoment(id = "moment-old", name = "Lake swim", happenedAt = UiFixtures.thisWeek(3))
        path.path = listOf(threeWeeksBack, thisWeek)
        focusRequests.request(threeWeeksBack.id)
        backgroundAndReturn()

        compose.onNodeWithText(weekHeader(threeWeeksBack.happenedAt)).assertIsDisplayed()
        onMoment("Lake swim").assertIsDisplayed()
        onMoment("Coffee with Mo").assertDoesNotExist()
    }

    /**
     * The pager animates through the weeks in between; those passing pages are
     * not the user's choice and must not re-steer it short of the target.
     */
    @Test
    fun `a focus request pages past the weeks in between`() {
        val thisWeek = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek())
        val between = UiFixtures.pathMoment(id = "moment-mid", name = "Book club", happenedAt = UiFixtures.thisWeek(2))
        path.path = listOf(between, thisWeek)
        signedIn()
        onMoment("Coffee with Mo").assertIsDisplayed()

        val farBack =
            UiFixtures.pathMoment(id = "moment-far", name = "Lake swim", happenedAt = UiFixtures.thisWeek(5))
        path.path = listOf(farBack, between, thisWeek)
        focusRequests.request(farBack.id)
        backgroundAndReturn()

        compose.onNodeWithText(weekHeader(farBack.happenedAt)).assertIsDisplayed()
        onMoment("Lake swim").assertIsDisplayed()
    }

    /** The reload guard above must not swallow a real page change: a swipe still moves the focus. */
    @Test
    fun `swiping the pager back moves the focus to the previous week`() {
        val lastWeek =
            UiFixtures.pathMoment(id = "moment-old", name = "Lake swim", happenedAt = UiFixtures.thisWeek(1))
        val thisWeek = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek())
        path.path = listOf(lastWeek, thisWeek)
        signedIn()
        compose.onNodeWithText(weekHeader(thisWeek.happenedAt)).assertIsDisplayed()

        compose.onNodeWithTag(JourneyTags.PATH_WEEK_PAGER).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithText(weekHeader(lastWeek.happenedAt)).assertIsDisplayed()
        onMoment("Lake swim").assertIsDisplayed()
    }

    /** The week line's "week 41" (#1207, D3): what names the page a moment is on. */
    private fun weekHeader(happenedAt: OffsetDateTime): String {
        val day = happenedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        return string(R.string.path_wrap_week, WeekRollBuilder.isoWeekNumber(WeekRollBuilder.weekStart(day)))
    }
}
