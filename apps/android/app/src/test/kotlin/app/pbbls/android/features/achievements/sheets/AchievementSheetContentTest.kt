package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each sheet identity renders its section; one whose section left the payload renders nothing (#1307). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class AchievementSheetContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each identity renders its section, a missing section renders nothing`() {
        val page = AchievementsFixture.page.copy(pebbles = null)
        compose.setContent {
            PebblesTheme {
                Column {
                    AchievementSheetContent(AchievementSheet.Domains, page, AchievementsFixture.lookups, {}, {}, {})
                    AchievementSheetContent(AchievementSheet.Pebbles, page, AchievementsFixture.lookups, {}, {}, {})
                    AchievementSheetContent(AchievementSheet.Year(2019), page, AchievementsFixture.lookups, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Domains").assertExists()
        compose.onNodeWithText("Pebbles").assertDoesNotExist()
        compose.onNodeWithText("2019").assertDoesNotExist()
    }
}
