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
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Pebbles sheet (#1307): the served levels open their pebble, the next says what is left. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class PebblesSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `reached milestones open their pebble, the next says what is left, then every 500`() {
        val opened = mutableListOf<String>()
        val page = AchievementsFixture.page
        compose.setContent {
            PebblesTheme {
                PebblesSheet(
                    page.pebbles!!,
                    page.glyphsCarved,
                    page.glyphSales,
                    { opened += it },
                    Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        compose.onNodeWithText("137 pebbles kept.").assertExists()
        compose.onNodeWithContentDescription("137 pebbles. Five stones stand; the sixth rises toward 250.").assertExists()
        compose.onNodeWithContentDescription("100, Pebble number 100", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("250, 113 to go", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("then every 500").performScrollTo().assertExists()
        compose.onNodeWithText("The buyer is never named.").performScrollTo().assertExists()
        assertEquals(
            listOf(
                page.pebbles!!
                    .levels
                    .last()
                    .pebble!!
                    .momentId,
            ),
            opened,
        )
    }
}
