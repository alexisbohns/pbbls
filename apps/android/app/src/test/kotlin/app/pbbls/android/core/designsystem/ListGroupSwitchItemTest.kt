package app.pbbls.android.core.designsystem

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A switch row is ONE control for TalkBack: the row carries the switch role and its on/off state,
 * and the `Switch` inside is not a second toggleable node.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListGroupSwitchItemTest {
    @get:Rule
    val compose = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    @Test
    fun `the row is the one toggleable node, with the switch role`() {
        var checked by mutableStateOf(false)
        compose.setContent {
            PebblesTheme {
                ListGroup(
                    header = HEADER,
                    items =
                        listOf {
                            ListGroupSwitchItem(
                                headline = HEADLINE,
                                supporting = SUPPORTING,
                                checked = checked,
                                onCheckedChange = { checked = it },
                            )
                        },
                )
            }
        }

        assertEquals(1, compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(isToggleable(), useUnmergedTree = true).fetchSemanticsNodes().size)
        val row = compose.onNode(isToggleable() and isSwitch and hasText(HEADLINE) and hasText(SUPPORTING))
        row.assertIsOff()

        row.performClick()

        assertEquals(true, checked)
        row.assertIsOn()
    }

    @Test
    fun `a disabled row does not toggle`() {
        var checked by mutableStateOf(false)
        compose.setContent {
            PebblesTheme {
                ListGroup(
                    items =
                        listOf {
                            ListGroupSwitchItem(
                                headline = HEADLINE,
                                checked = checked,
                                onCheckedChange = { checked = it },
                                enabled = false,
                            )
                        },
                )
            }
        }

        val row = compose.onNode(isToggleable() and isSwitch)
        row.assertIsNotEnabled()
        row.performClick()
        assertFalse(checked)
    }

    private companion object {
        const val HEADER = "Haptics"
        const val HEADLINE = "Vibrations"
        const val SUPPORTING = "Pebbles taps back."
    }
}
