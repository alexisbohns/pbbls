package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.PathMoment
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.inject.Inject

/**
 * Deleting a moment (#857, #1048): the Path long-press menu and the read
 * page's ⋮ menu (R7), the R8 dialog with its one or two choices, the
 * `delete_moment` / `delete_pebble` writes, and the failure notice (R15) — end
 * to end through the real screens and ViewModels against the write fake.
 */
@HiltAndroidTest
class PebbleDeleteTest : AppUiTest() {
    @Inject lateinit var path: FakeMomentReadService

    @Inject lateinit var writes: FakeMomentWriteService

    private val completed = UiFixtures.pathMoment(id = "moment-1", name = "Coffee with Mo", happenedAt = UiFixtures.thisWeek())

    /** How the Spine's blank stone ends its sentence ("Opens the moment."); a pebble's says "Opens the pebble." (R4). */
    private val blankStoneSentence: String
        get() = string(R.string.path_spine_blank_a11y, "", "", "", "").substringAfterLast(". ").trim()

    private fun askToDelete(moment: PathMoment = completed) {
        path.path = listOf(moment)
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performTouchInput { longClick() }
        onText(R.string.pebble_delete).performClick()
    }

    @Test
    fun `deleting the moment too removes it and refreshes the timeline`() {
        askToDelete()
        onText(R.string.delete_pebble_title).assertIsDisplayed()
        val loadsBefore = path.loadPathCount
        path.path = emptyList()

        onText(R.string.delete_moment_too).performClick()

        assertEquals(listOf("moment-1"), writes.deletedMoments)
        assertTrue(writes.deletedPebbles.isEmpty())
        assertTrue(path.loadPathCount > loadsBefore)
        onText(R.string.delete_pebble_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertDoesNotExist()
    }

    @Test
    fun `a completed row offers both choices, and keeping the moment deletes only the pebble`() {
        askToDelete()
        onText(R.string.delete_pebble_message).assertIsDisplayed()
        onText(R.string.delete_moment_too).assertIsDisplayed()
        path.path = listOf(completed.copy(pebble = null))

        onText(R.string.delete_pebble_keep_moment).performClick()

        assertEquals(listOf("pebble-1"), writes.deletedPebbles)
        assertTrue(writes.deletedMoments.isEmpty())
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
        // The stone and its row both read the blank sentence; either proves the pebble is gone.
        compose.onAllNodesWithContentDescription(blankStoneSentence, substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun `a bare moment offers only the moment`() {
        askToDelete(completed.copy(pebble = null))
        onText(R.string.delete_moment_title).assertIsDisplayed()
        onText(R.string.pebble_delete_confirm_message).assertIsDisplayed()
        onText(R.string.delete_pebble_keep_moment).assertDoesNotExist()

        onText(R.string.delete_moment).performClick()

        assertEquals(listOf("moment-1"), writes.deletedMoments)
        assertTrue(writes.deletedPebbles.isEmpty())
    }

    @Test
    fun `cancelling deletes nothing`() {
        askToDelete()

        onText(R.string.action_cancel).performClick()

        assertTrue(writes.deletedMoments.isEmpty())
        assertTrue(writes.deletedPebbles.isEmpty())
        onText(R.string.delete_pebble_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }

    @Test
    fun `a failed delete says so and leaves the moment on the timeline`() {
        askToDelete()
        writes.failNext = IOException("offline")

        onText(R.string.delete_moment_too).performClick()

        onText(R.string.moment_delete_error).assertIsDisplayed()
        onText(R.string.action_cancel).performClick()
        onText(R.string.moment_delete_error).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertIsDisplayed()
    }

    @Test
    fun `the read page's menu deletes the moment and closes the page`() {
        path.path = listOf(completed)
        path.moments = mapOf("moment-1" to UiFixtures.momentDetail(id = "moment-1", name = "Coffee with Mo"))
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).performClick()
        onText(R.string.action_delete).performClick()
        onText(R.string.delete_pebble_title).assertIsDisplayed()
        path.path = emptyList()

        onText(R.string.delete_moment_too).performClick()

        assertEquals(listOf("moment-1"), writes.deletedMoments)
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).assertDoesNotExist()
    }

    @Test
    fun `the read page's failed delete keeps the page and says so`() {
        path.path = listOf(completed)
        path.moments = mapOf("moment-1" to UiFixtures.momentDetail(id = "moment-1", name = "Coffee with Mo"))
        skipOnboarding()
        launch()
        signIn()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).performClick()
        onText(R.string.action_delete).performClick()
        writes.failNext = IOException("offline")

        onText(R.string.delete_moment_too).performClick()

        onText(R.string.moment_delete_error).assertIsDisplayed()
        onText(R.string.action_cancel).performClick()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertIsDisplayed()
    }
}
