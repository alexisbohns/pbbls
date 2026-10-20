package app.pbbls.android.features.path.card

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.Visibility
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The footer's turn is one button named for where it leads, and the privacy reads once. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CardFooterTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the turn is one button that turns to the named face`() {
        var turns = 0
        compose.setContent {
            PebblesTheme {
                CardFooter(Visibility.PRIVATE, CardFaceTarget.PEBBLE, stone = Color.Gray, onTurn = { turns++ })
            }
        }
        val action = context.getString(R.string.card_turn_action_to_pebble)
        compose
            .onNodeWithContentDescription(action)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, turns)
        // Its printed words are folded into the button, not read again on their own.
        compose.onNodeWithText(context.getString(R.string.card_turn_to_pebble)).assertDoesNotExist()
    }

    @Test
    fun `privacy reads as one sentence`() {
        compose.setContent {
            PebblesTheme {
                CardFooter(Visibility.SECRET, CardFaceTarget.MOMENT, stone = Color.Gray, onTurn = {})
            }
        }
        val grade = context.getString(R.string.visibility_secret)
        val privacy =
            context.getString(R.string.visibility_badge_a11y, grade) + ". " +
                context.getString(R.string.record_privacy_secret_explanation)
        compose.onNodeWithContentDescription(privacy).assertExists()
        compose.onNodeWithText(grade).assertDoesNotExist()
    }
}
