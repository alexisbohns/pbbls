package app.pbbls.android.core.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.pbbls.android.core.data.AchievementRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The families the first five and the achievements page add (#1307) read as words, never their
 * slug: the first five's from this client's strings (the catalog seeds no override for them), the
 * six firsts and the completions from the catalog's own titles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AchievementCopyNewFamiliesTest {
    @get:Rule
    val compose = createComposeRule()

    private fun record(
        slug: String,
        family: String,
        titleEn: String? = null,
    ) = AchievementRecord(
        id = slug,
        slug = slug,
        family = family,
        sortOrder = 0,
        karmaReward = 0,
        isActive = true,
        titleEn = titleEn,
    )

    @Test
    fun `the first five's families read as words, with a description`() {
        compose.setContent {
            Column {
                Text(achievementTitle(record("first-moment", "first_moment")))
                Text(achievementTitle(record("first-pebble", "first_pebble")))
                Text(achievementTitle(record("blue-pebble", "first_five")))
                Text(achievementDescription(record("first-moment", "first_moment")).orEmpty())
                Text(achievementDescription(record("first-pebble", "first_pebble")).orEmpty())
                Text(achievementDescription(record("blue-pebble", "first_five")).orEmpty())
            }
        }
        compose.onNodeWithText("First moment").assertExists()
        compose.onNodeWithText("First pebble").assertExists()
        compose.onNodeWithText("The blue pebble").assertExists()
        compose.onNodeWithText("Record your first moment.").assertExists()
        compose.onNodeWithText("Complete your first pebble.").assertExists()
        compose.onNodeWithText("Claim it once your first five are done.").assertExists()
    }

    @Test
    fun `the six firsts and the completions take the catalog's seeded titles`() {
        compose.setContent {
            Column {
                Text(achievementTitle(record("first-stone-lowlight", "first_stone", titleEn = "First lowlight")))
                Text(achievementTitle(record("complete-six-firsts", "set_complete", titleEn = "The six firsts")))
            }
        }
        compose.onNodeWithText("First lowlight").assertExists()
        compose.onNodeWithText("The six firsts").assertExists()
    }
}
