package app.pbbls.android.features.achievements.page

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The page (#1307): one labelled button per card, the ready mark apart from it, a missing section
 * hiding its card. 360 dp wide, so the figures fit a narrow phone; 3200 dp tall, so the list
 * composes every card and `assertDoesNotExist` proves a card hidden, not off screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h3200dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class AchievementsPageContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<AchievementSheet>()
    private val pulled = mutableListOf<CompletionStack>()

    private val page = AchievementsFixture.page

    /** The six firsts whole and offered: a ready mark on Explorer. */
    private val ready =
        page.copy(explorer = page.explorer!!.copy(completion = page.explorer!!.completion!!.copy(ready = true, pulledAt = null)))

    private fun show(
        page: AchievementsPage = this.page,
        pulling: Boolean = false,
    ) {
        compose.setContent {
            PebblesTheme {
                AchievementsPageContent(
                    page,
                    AchievementsFixture.lookups,
                    onOpen = { opened += it },
                    onPull = { pulled += it },
                    pulling = pulling,
                )
            }
        }
    }

    @Test
    fun `each card is one labelled button that opens its sheet`() {
        show()
        compose.onNodeWithContentDescription("Explorer: the first five, done", substring = true).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription("Emotions, 23 of 38. 2 new.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Domains, 14 of 18.", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Pebbles. 137 pebbles.", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("The year 2026. On the rim: August.", substring = true).performScrollTo().performClick()
        assertEquals(
            listOf(
                AchievementSheet.Explorer,
                AchievementSheet.Emotions,
                AchievementSheet.Domains,
                AchievementSheet.Pebbles,
                AchievementSheet.Year(2026),
            ),
            opened,
        )
    }

    @Test
    fun `the header reads the days kept since the start as one heading`() {
        show()
        compose.onNodeWithContentDescription("249 days, since Sunday, March 1, 2026").assertExists()
        compose.onNodeWithText("249").assertDoesNotExist()
    }

    @Test
    fun `the ready mark pulls on its own, apart from the card`() {
        show(ready)
        compose.onNodeWithContentDescription("The six firsts: the ceremony is ready.").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(CompletionStack.SIX_FIRSTS), pulled)
        assertEquals(emptyList<AchievementSheet>(), opened)
    }

    @Test
    fun `while a pull runs the ready mark waits`() {
        show(ready, pulling = true)
        compose.onNodeWithContentDescription("The six firsts: the ceremony is ready.").assertIsNotEnabled().performClick()
        assertEquals(emptyList<CompletionStack>(), pulled)
    }

    @Test
    fun `a completion pulled or not offered shows no mark`() {
        show()
        compose.onNodeWithContentDescription("the ceremony is ready", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a missing section hides its card and nothing else`() {
        show(page.copy(emotions = null, pebbles = null))
        compose.onNodeWithContentDescription("Emotions,", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pebbles.", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Explorer:", substring = true).assertExists()
    }
}
