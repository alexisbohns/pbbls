package app.pbbls.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.R
import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeAchievementsService
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * The page through the real app (#1307): You → a shelf badge → Achievements, the retroactive grant before the read,
 * a card opens its sheet and marks it seen, Explorer's Emotions row pushes a second sheet, and back
 * pops one sheet at a time. Back goes through Espresso, to the focused window: a sheet's dialog.
 */
@HiltAndroidTest
class AchievementsPageTest : AppUiTest() {
    @Inject lateinit var achievements: FakeAchievementsService

    private fun openAchievements() {
        achievements.served = AchievementsFixture.page
        skipOnboarding()
        launch()
        signIn()
        compose.onNode(hasText(string(R.string.tab_you)) and isSelectable()).performClick()
        // You's shelf is badge cards since #1383 (D14.6); the fixture's third is "100 pebbles".
        val badge = context.resources.getQuantityString(R.plurals.achievement_family_pebble_count_title, 100, 100)
        compose.onNodeWithText(badge).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the page asks for the grant, then reads, and draws its cards`() {
        openAchievements()
        // You's shelf grants and reads first (D18); the page itself grants, then reads.
        assertEquals(listOf("check", "page"), achievements.calls.takeLast(2))
        compose.onNodeWithContentDescription("249 days, since Sunday, March 1, 2026").assertExists()
        compose.onNodeWithContentDescription("Explorer: the first five, done", substring = true).assertExists()
    }

    @Test
    fun `a card opens its sheet, Explorer pushes Emotions, back pops one at a time`() {
        openAchievements()
        compose.onNodeWithContentDescription("Explorer: the first five, done", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf(AchievementCard.EXPLORER), achievements.seen)

        // The Explorer sheet's row: the card's label carries ". 2 new." after the same words.
        compose.onNodeWithContentDescription("Emotions, 23 of 38").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), achievements.seen)
        // The card and the Emotions sheet's rosace say the same.
        compose.onAllNodesWithContentDescription("Emotions, 23 of 38. 2 new.").assertCountEquals(2)

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Emotions, 23 of 38. 2 new.").assertCountEquals(1)
        compose.onNodeWithContentDescription("Emotions, 23 of 38").assertExists()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Emotions, 23 of 38").assertDoesNotExist()
        onText(R.string.achievements_title).assertExists()
    }
}
