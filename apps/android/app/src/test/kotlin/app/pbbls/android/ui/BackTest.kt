package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementNotificationService
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.FakePebbleDetailService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import javax.inject.Inject

/**
 * Back-handler precedence (#857). `NavDisplay` registers the handler that pops
 * the stack; a screen that registers its own `NavigationBackHandler` while it
 * is composed must win over it, and anything drawn above the display must win
 * over both. Each test sends the system back gesture through the activity's
 * dispatcher ([AppUiTest.pressBack]), so the platform decides — not the test.
 */
@HiltAndroidTest
class BackTest : AppUiTest() {
    @Inject lateinit var path: FakePathService

    @Inject lateinit var pebbleDetail: FakePebbleDetailService

    @Inject lateinit var achievements: AchievementNotificationService

    private fun onPath() {
        skipOnboarding()
        launch()
        signIn()
    }

    private fun openRecordFlow() {
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()
        onText(R.string.record_photo_title).assertIsDisplayed()
    }

    @Test
    fun `back on a later record step goes to the previous step, not off the cover`() {
        onPath()
        openRecordFlow()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_when_title).assertIsDisplayed()

        pressBack()

        onText(R.string.record_photo_title).assertIsDisplayed()
    }

    @Test
    fun `back on the first record step with nothing written closes the flow`() {
        onPath()
        openRecordFlow()

        pressBack()

        onText(R.string.record_photo_title).assertDoesNotExist()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).assertIsDisplayed()
    }

    @Test
    fun `back on the first record step with a draft asks before closing`() {
        onPath()
        openRecordFlow()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_action_continue).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Coffee with Mo")
        pressBack()
        pressBack()
        onText(R.string.record_photo_title).assertIsDisplayed()

        pressBack()

        onText(R.string.record_close_title).assertIsDisplayed()
        onText(R.string.record_close_keep_going).performClick()
        onText(R.string.record_photo_title).assertIsDisplayed()
    }

    @Test
    fun `a cover with no handler of its own is popped by the display`() {
        onPath()
        onText(R.string.tab_you).performClick()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        onText(R.string.settings_title).assertIsDisplayed()

        pressBack()

        onText(R.string.profile_title).assertIsDisplayed()
    }

    @Test
    fun `back on the edit cover dismisses it to the detail beneath`() {
        path.pebbles = listOf(UiFixtures.pebble())
        pebbleDetail.detail = UiFixtures.pebbleDetail()
        onPath()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        onText(R.string.pebble_detail_edit).performClick()
        onText(R.string.edit_pebble_title).assertIsDisplayed()

        pressBack()

        onText(R.string.edit_pebble_title).assertDoesNotExist()
        onText(R.string.pebble_detail_edit).assertIsDisplayed()
    }

    @Test
    fun `onboarding swallows back`() {
        launch()
        signIn()
        onText(R.string.onboarding_skip).assertIsDisplayed()

        pressBack()

        onText(R.string.onboarding_skip).assertIsDisplayed()
    }

    @Test
    fun `an achievement moment over a cover takes back before the cover does`() {
        onPath()
        onText(R.string.tab_you).performClick()
        compose.onNodeWithContentDescription(string(R.string.settings_title)).performClick()
        compose.runOnIdle {
            achievements.present(listOf(AchievementMomentCard(slug = "first-pebble", record = null, karmaGranted = 5)))
        }
        onText(R.string.achievement_moment_eyebrow).assertIsDisplayed()

        pressBack()

        onText(R.string.achievement_moment_eyebrow).assertDoesNotExist()
        onText(R.string.settings_title).assertIsDisplayed()

        pressBack()

        onText(R.string.profile_title).assertIsDisplayed()
    }
}
