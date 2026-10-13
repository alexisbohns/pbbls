package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * Deleting a moment from Path (#857, #1048): the long-press menu, the
 * confirmation, the `delete_moment` write, and the failure notice (R15) — end to end through `PathScreen` and
 * `PathViewModel` against the write fake.
 */
@HiltAndroidTest
class PebbleDeleteTest : AppUiTest() {
    @Inject lateinit var path: FakeMomentReadService

    @Inject lateinit var writes: FakeMomentWriteService

    private val name = "Coffee with Mo"
    private val moment = UiFixtures.pathMoment(id = "moment-1", name = name, happenedAt = OffsetDateTime.now())

    private fun askToDelete() {
        path.path = listOf(moment)
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performTouchInput { longClick() }
        onText(R.string.pebble_delete).performClick()
        onText(R.string.pebble_delete_confirm_title, name).assertIsDisplayed()
    }

    @Test
    fun `confirming deletes the moment and refreshes the timeline`() {
        askToDelete()
        val loadsBefore = path.loadPathCount
        path.path = emptyList()

        onText(R.string.pebble_delete).performClick()

        assertEquals(listOf("moment-1"), writes.deletedMoments)
        assertTrue(path.loadPathCount > loadsBefore)
        onText(R.string.pebble_delete_confirm_title, name).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertDoesNotExist()
    }

    @Test
    fun `cancelling deletes nothing`() {
        askToDelete()

        onText(R.string.action_cancel).performClick()

        assertEquals(emptyList<String>(), writes.deletedMoments)
        onText(R.string.pebble_delete_confirm_title, name).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }

    @Test
    fun `a failed delete says so and leaves the moment on the timeline`() {
        askToDelete()
        writes.failNext = IOException("offline")

        onText(R.string.pebble_delete).performClick()

        onText(R.string.moment_delete_error).assertIsDisplayed()
        onText(R.string.action_cancel).performClick()
        onText(R.string.moment_delete_error).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }
}
