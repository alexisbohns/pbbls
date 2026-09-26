package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
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
    @Inject lateinit var path: FakePathService

    private fun signedIn() {
        skipOnboarding()
        launch()
        signIn()
    }

    @Test
    fun `a loaded timeline shows this week's pebbles`() {
        path.pebbles = listOf(UiFixtures.pebble(name = "Coffee with Mo"))

        signedIn()

        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
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
        path.pebbles = listOf(UiFixtures.pebble(name = "Coffee with Mo"))
        signedIn()
        onText(R.string.path_load_error).assertIsDisplayed()

        backgroundAndReturn()

        onText(R.string.path_load_error).assertDoesNotExist()
        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        assertEquals(2, path.loadCount)
    }

    @Test
    fun `a failed refresh keeps the timeline already on screen`() {
        path.pebbles = listOf(UiFixtures.pebble(name = "Coffee with Mo"))
        signedIn()

        path.failNext = IOException("offline")
        backgroundAndReturn()

        compose.onNodeWithText("Coffee with Mo").assertIsDisplayed()
        onText(R.string.path_load_error).assertDoesNotExist()
    }
}
