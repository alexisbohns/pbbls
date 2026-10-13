package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.features.path.PathFocusRequests
import app.pbbls.android.features.path.WeekHeaderFormatting
import app.pbbls.android.features.path.WeekRollBuilder
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
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

    private fun signedIn() {
        skipOnboarding()
        launch()
        signIn()
    }

    @Test
    fun `a loaded timeline shows this week's moments`() {
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = OffsetDateTime.now()))

        signedIn()

        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
    }

    /** A photo-only capture has no name and no pebble yet; it is still on the timeline (R3). */
    @Test
    fun `an untitled moment reads Untitled`() {
        path.path = listOf(UiFixtures.pathMoment(name = null, pebble = null, happenedAt = OffsetDateTime.now()))

        signedIn()

        onText(R.string.moment_untitled).assertIsDisplayed()
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
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = OffsetDateTime.now()))
        signedIn()
        onText(R.string.path_load_error).assertIsDisplayed()

        backgroundAndReturn()

        onText(R.string.path_load_error).assertDoesNotExist()
        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        assertEquals(2, path.loadPathCount)
    }

    @Test
    fun `a failed refresh keeps the timeline already on screen`() {
        path.path = listOf(UiFixtures.pathMoment(name = "Coffee with Mo", happenedAt = OffsetDateTime.now()))
        signedIn()

        path.failNext = IOException("offline")
        backgroundAndReturn()

        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
    }

    /**
     * R14 on a reload that brings new weeks: the requested moment's week wins.
     * The pager's swipe → focus reporter re-subscribes when the entries change,
     * and used to report the page it was still on, overwriting the request.
     */
    @Test
    fun `a focus request pages the path to that moment's week on the next load`() {
        val thisWeek = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = OffsetDateTime.now())
        path.path = listOf(thisWeek)
        signedIn()
        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()

        val threeWeeksBack =
            UiFixtures.pathMoment(id = "moment-old", name = "Lake swim", happenedAt = OffsetDateTime.now().minusWeeks(3))
        path.path = listOf(threeWeeksBack, thisWeek)
        focusRequests.request(threeWeeksBack.id)
        backgroundAndReturn()

        compose.onNodeWithText(weekHeader(threeWeeksBack.happenedAt)).assertIsDisplayed()
        compose.onNodeWithText("Lake swim").assertIsDisplayed()
        compose.onNodeWithText("Coffee with Mo").assertDoesNotExist()
    }

    /** The reload guard above must not swallow a real page change: a swipe still moves the focus. */
    @Test
    fun `swiping the pager back moves the focus to the previous week`() {
        val lastWeek =
            UiFixtures.pathMoment(id = "moment-old", name = "Lake swim", happenedAt = OffsetDateTime.now().minusWeeks(1))
        val thisWeek = UiFixtures.pathMoment(id = "moment-now", name = "Coffee with Mo", happenedAt = OffsetDateTime.now())
        path.path = listOf(lastWeek, thisWeek)
        signedIn()
        compose.onNodeWithText(weekHeader(thisWeek.happenedAt)).assertIsDisplayed()

        compose.onNodeWithTag(JourneyTags.PATH_WEEK_PAGER).performTouchInput { swipeRight() }
        compose.waitForIdle()

        compose.onNodeWithText(weekHeader(lastWeek.happenedAt)).assertIsDisplayed()
        compose.onNodeWithText("Lake swim").assertIsDisplayed()
    }

    private fun weekHeader(happenedAt: OffsetDateTime): String {
        val locale = Locale.getDefault()
        val day = happenedAt.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        return WeekHeaderFormatting
            .formatRange(WeekRollBuilder.weekStart(day), LocalDate.now(), locale)
            .uppercase(locale)
    }
}
