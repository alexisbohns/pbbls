package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.FakePebbleWriteService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.inject.Inject

/**
 * Deleting a pebble from Path (#857): the long-press menu, the confirmation,
 * the write, and the failure notice — end to end through `PathScreen` and
 * `PathViewModel` against the write fake.
 */
@HiltAndroidTest
class PebbleDeleteTest : AppUiTest() {
    @Inject lateinit var path: FakePathService

    @Inject lateinit var writes: FakePebbleWriteService

    private val pebble = UiFixtures.pebble(id = "pebble-1", name = "Coffee with Mo")

    private fun askToDelete() {
        path.pebbles = listOf(pebble)
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performTouchInput { longClick() }
        onText(R.string.pebble_delete).performClick()
        onText(R.string.pebble_delete_confirm_title, pebble.name).assertIsDisplayed()
    }

    @Test
    fun `confirming deletes the pebble and refreshes the timeline`() {
        askToDelete()
        val loadsBefore = path.loadCount
        path.pebbles = emptyList()

        onText(R.string.pebble_delete).performClick()

        assertEquals(listOf("pebble-1"), writes.deletedPebbleIds)
        assertTrue(path.loadCount > loadsBefore)
        onText(R.string.pebble_delete_confirm_title, pebble.name).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertDoesNotExist()
    }

    @Test
    fun `cancelling deletes nothing`() {
        askToDelete()

        onText(R.string.action_cancel).performClick()

        assertEquals(emptyList<String>(), writes.deletedPebbleIds)
        onText(R.string.pebble_delete_confirm_title, pebble.name).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }

    @Test
    fun `a failed delete says so and leaves the pebble on the timeline`() {
        askToDelete()
        writes.failNext = IOException("offline")

        onText(R.string.pebble_delete).performClick()

        onText(R.string.pebble_delete_error).assertIsDisplayed()
        onText(R.string.action_cancel).performClick()
        onText(R.string.pebble_delete_error).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }
}
