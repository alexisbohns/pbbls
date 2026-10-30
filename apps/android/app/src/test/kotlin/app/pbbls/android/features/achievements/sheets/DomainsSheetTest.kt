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

/** The Domains sheet (#1307): the insight, the register by arrival and newest first, the not yet. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class DomainsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the register numbers by arrival, newest first, and opens its pebble`() {
        val opened = mutableListOf<String>()
        val domains = AchievementsFixture.page.domains!!
        compose.setContent {
            PebblesTheme {
                DomainsSheet(domains, AchievementsFixture.lookups, { opened += it }, {}, Modifier.verticalScroll(rememberScrollState()))
            }
        }
        compose.onNodeWithText("Your pebbles reach most parts of your life. Your insights have plenty to read.").assertExists()
        compose.onNodeWithContentDescription("No. 14, Events", substring = true).performScrollTo().performClick()
        assertEquals(
            listOf(
                domains.entered
                    .first()
                    .pebble!!
                    .momentId,
            ),
            opened,
        )
        compose.onNodeWithContentDescription("No. 01", substring = true).performScrollTo().assertExists()
        compose.onNodeWithText("Weather").performScrollTo().assertExists()
    }

    @Test
    fun `a domain the server sent without an id keeps its row under a fallback name`() {
        val domains = AchievementsFixture.page.domains!!
        val entered = domains.entered.mapIndexed { i, e -> if (i == 0) e.copy(domainId = null) else e }
        compose.setContent {
            PebblesTheme {
                DomainsSheet(
                    domains.copy(entered = entered),
                    AchievementsFixture.lookups,
                    {},
                    {},
                    Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
        compose.onNodeWithContentDescription("No. 14, A domain", substring = true).performScrollTo().assertExists()
    }
}
