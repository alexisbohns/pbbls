package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.features.achievements.AchievementSheet
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The year sheet (#1307): the listed months read their served counts, the next step closes the table. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class YearSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val year = AchievementsFixture.page.years.single()

    @Test
    fun `the listed months read their counts, the next step closes the table`() {
        compose.setContent {
            PebblesTheme {
                YearSheet(year, otherYears = emptyList(), lookups = AchievementsFixture.lookups, onPush = {
                }, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose
            .onNodeWithContentDescription(
                "August. Days with a moment: 16. With a pebble: 14. Full weeks filled: 4 of 4.",
            ).performScrollTo()
            .assertExists()
        compose
            .onNodeWithContentDescription(
                "November. Days with a moment: 1. With a pebble: 0. No full week yet.",
            ).performScrollTo()
            .assertExists()
        compose
            .onNodeWithText(
                "Thirteen more days with a moment, one in each full week, bring November to the inner disc.",
            ).performScrollTo()
            .assertExists()
        compose.onNodeWithText("None yet. 2026 is your first year here.").performScrollTo().assertExists()
    }

    @Test
    fun `an earlier year pushes its own sheet`() {
        val pushed = mutableListOf<AchievementSheet>()
        compose.setContent {
            PebblesTheme {
                YearSheet(year, otherYears = listOf(2025), lookups = AchievementsFixture.lookups, onPush = {
                    pushed += it
                }, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose.onNodeWithContentDescription("2025", substring = true).performScrollTo().performClick()
        assertEquals(listOf<AchievementSheet>(AchievementSheet.Year(2025)), pushed)
    }
}
