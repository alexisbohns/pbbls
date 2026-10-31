package app.pbbls.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.pbbls.android.R
import app.pbbls.android.testing.AppUiTest
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * What leaves You keeps a home (#1383, spec D14), through the real UI: Connections
 * is reached from People's top bar ("Circle", where iOS has it), and Log out from
 * Settings. Both land before the You rebuild, so no build of the stack orphans them.
 */
@HiltAndroidTest
class YouJourneyTest : AppUiTest() {
    private fun signedIn() {
        skipOnboarding()
        launch()
        signIn()
    }

    /** A bar item, told apart from same-named screen text by being selectable. */
    private fun onTab(id: Int): SemanticsNodeInteraction = compose.onNode(hasText(string(id)) and isSelectable())

    private fun onDescription(id: Int): SemanticsNodeInteraction = compose.onNodeWithContentDescription(string(id))

    @Test
    fun `People opens Connections from Circle`() {
        signedIn()
        onTab(R.string.tab_people).performClick()

        onDescription(R.string.people_circle_a11y).assertIsDisplayed().performClick()

        onText(R.string.connections_title).assertIsDisplayed()
        // A push inside People, not a tab switch.
        onTab(R.string.tab_people).assertIsSelected()
    }
}
