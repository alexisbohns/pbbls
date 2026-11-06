package app.pbbls.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.R
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeSoulsService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * Deleting a soul from the People tab: a usual soul is both a tile and a row (S1), and a long press
 * on one of them opens one delete menu, then the confirm dialog, then the delete.
 */
@HiltAndroidTest
class SoulDeleteTest : AppUiTest() {
    @Inject lateinit var souls: FakeSoulsService

    @Test
    fun `a long press on a usual soul's row opens one menu and deletes it`() {
        souls.souls = listOf(UiFixtures.soul(id = "soul-1", name = "Mo").copy(pebblesCount = 3))
        skipOnboarding()
        launch()
        signIn()
        compose.onNode(hasText(string(R.string.tab_people)) and isSelectable()).performClick()

        // The tile comes first in the list, the row after it.
        val named = compose.onAllNodesWithText("Mo")
        named.assertCountEquals(2)
        named[1].performTouchInput { longClick() }

        compose.onAllNodesWithText(string(R.string.pebble_delete)).assertCountEquals(1)
        onText(R.string.pebble_delete).performClick()

        compose.onNodeWithText(string(R.string.pebble_delete_confirm_title, "Mo")).assertIsDisplayed()
        onText(R.string.pebble_delete).performClick()
        compose.waitForIdle()

        assertEquals(listOf("soul-1"), souls.deleteCalls)
    }
}
