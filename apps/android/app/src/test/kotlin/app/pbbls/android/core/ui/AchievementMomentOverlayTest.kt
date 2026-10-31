package app.pbbls.android.core.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.R
import app.pbbls.android.core.data.AchievementMoment
import app.pbbls.android.core.data.AchievementMomentCard
import app.pbbls.android.core.data.AchievementRecord
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The unlock moment's card (#1307): a named title and body win over the catalog row; karma 0 has no line. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [PIXEL_TEST_SDK])
class AchievementMomentOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(card: AchievementMomentCard) {
        compose.setContent {
            PebblesTheme {
                AchievementMomentOverlay(AchievementMoment(card, 1, 1, isLast = true), onAdvance = {}, onDismiss = {}, hapticsOn = false)
            }
        }
    }

    @Test
    fun `a ceremony is titled by its set, and a pull paid nothing shows no karma line`() {
        val row =
            AchievementRecord(
                id = "c6",
                slug = "complete-six-firsts",
                family = "set_complete",
                sortOrder = 0,
                karmaReward = 30,
                isActive = true,
            )
        show(
            AchievementMomentCard(
                "complete-six-firsts",
                row,
                karmaGranted = 0,
                titleRes = R.string.achievements_ceremony_six_firsts_title,
                bodyRes = R.string.achievements_ceremony_six_firsts_body,
            ),
        )
        compose.onNodeWithText("The six firsts").assertExists()
        compose.onNodeWithText("complete-six-firsts").assertDoesNotExist()
        compose.onNodeWithText("karma", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the server's karma shows when the pull paid it`() {
        show(AchievementMomentCard("complete-emotions", null, karmaGranted = 30, titleRes = R.string.achievements_ceremony_emotions_title))
        compose.onNodeWithText("Every emotion").assertExists()
        compose.onNodeWithText("30", substring = true).assertExists()
    }
}
