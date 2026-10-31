package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.AchievementsPage
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stacked sheets (#1307): Explorer pushes Domains on top, dismissing pops only the top. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class AchievementSheetHostTest {
    @get:Rule
    val compose = createComposeRule()

    private var stack by mutableStateOf(listOf<AchievementSheet>(AchievementSheet.Explorer))

    private fun show(page: AchievementsPage = AchievementsFixture.page) {
        compose.setContent {
            PebblesTheme {
                AchievementSheetHost(
                    sheets = stack,
                    page = page,
                    lookups = AchievementsFixture.lookups,
                    onPush = { stack = stack + it },
                    onDismiss = { dismissed -> stack = stack.subList(0, stack.indexOf(dismissed)) },
                    onOpenMoment = {},
                    onPull = {},
                )
            }
        }
    }

    @Test
    fun `explorer pushes domains on top, and dismissing pops only the top`() {
        show()
        compose.onNodeWithContentDescription("Domains, 14 of 18", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(AchievementSheet.Explorer, AchievementSheet.Domains), stack)
        compose.onNodeWithText("the register").assertExists()

        Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf<AchievementSheet>(AchievementSheet.Explorer), stack)
        compose.onNodeWithText("the register").assertDoesNotExist()
    }

    @Test
    fun `a sheet whose section left the payload is skipped, not drawn empty`() {
        stack = listOf(AchievementSheet.Explorer, AchievementSheet.Pebbles)
        show(AchievementsFixture.page.copy(pebbles = null))
        compose.onNodeWithText("five rings").assertExists()
        compose.onNodeWithText("Milestones").assertDoesNotExist()
    }
}
