package app.pbbls.android.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.R
import app.pbbls.android.core.model.LabConfig
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

/**
 * What leaves You keeps a home (#1383, spec D14), through the real UI: Connections
 * is reached from People's top bar ("Circle", where iOS has it), and Log out from
 * Settings. Both land before the You rebuild, so no build of the stack orphans them.
 * And You's glyph rows open the store on their own shelf (D17), and its Lab reaches the Lab
 * screen and an announcement's detail (D16).
 */
@HiltAndroidTest
class YouJourneyTest : AppUiTest() {
    @Inject lateinit var logs: FakeLogsService

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

    @Test
    fun `Owned glyphs opens the store on the Owned tab`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onText(R.string.you_glyphs_owned).performScrollTo().performClick()

        onText(R.string.glyphs_title).assertIsDisplayed()
        compose.onNode(hasText(string(R.string.glyph_tab_owned)) and isToggleable()).assertIsOn()
        onText(R.string.glyphs_empty_owned_title).assertIsDisplayed()
    }

    @Test
    fun `Settings logs out and replaces the stack with Welcome`() {
        signedIn()
        onTab(R.string.tab_you).performClick()
        onDescription(R.string.settings_title).performClick()
        onText(R.string.settings_title).assertIsDisplayed()

        onText(R.string.settings_log_out).performScrollTo().performClick()

        assertEquals(1, supabase.signOutCount)
        assertEquals("this device only", 0, supabase.signOutEverywhereCount)
        onText(R.string.welcome_log_in).assertIsDisplayed()
        onText(R.string.tab_you).assertIsNotDisplayed()
        onText(R.string.settings_title).assertDoesNotExist()
    }

    /** "All updates" opens the Lab screen, the only way from You to the roadmap and the backlog (D16c). */
    @Test
    fun `All updates opens the Lab`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onText(R.string.you_lab_all_updates).performScrollTo().performClick()

        // The Lab screen's own first block, which You does not draw.
        onText(R.string.lab_community_button).assertIsDisplayed()
        onYou().assertDoesNotExist()
    }

    @Test
    fun `the announcement opens its detail`() {
        val announcement = UiFixtures.log(title = "Cut from one stone")
        logs.announcements = listOf(announcement)
        logs.log = announcement
        signedIn()
        onTab(R.string.tab_you).performClick()

        compose.onNodeWithText("Cut from one stone").performScrollTo().performClick()

        compose.onNodeWithText("Cut from one stone, in short.").assertIsDisplayed()
        // The detail loads the log by the id You passed in its key.
        assertEquals(listOf(announcement.id), logs.logCalls)
        onYou().assertDoesNotExist()
    }

    /** The callout opens the invite outside the app, one button for the whole stone (D16d, D18). */
    @Test
    fun `the community callout opens the WhatsApp invite`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onText(R.string.you_community_title).performScrollTo().performClick()

        val started = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(LabConfig.WHATSAPP_INVITE_URL, started.dataString)
    }
}
