package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.AchievementsFixture
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.achievements.EmotionsSection
import app.pbbls.android.testing.PIXEL_TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Emotions sheet (#1307): the served families in order, met emotions first, the rest quiet. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class EmotionsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun show(emotions: EmotionsSection) {
        compose.setContent {
            PebblesTheme {
                EmotionsSheet(
                    emotions = emotions,
                    lookups = AchievementsFixture.lookups,
                    onOpenMoment = { opened += it },
                    onPull = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    @Test
    fun `a met emotion opens its pebble and says it is new, an unmet one is a quiet name`() {
        val emotions = AchievementsFixture.page.emotions!!
        show(emotions)
        compose.onNodeWithContentDescription("Sadness, Grandma’s call went quiet", substring = true).performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("new since you last looked", substring = true).assertCountEquals(2)
        compose.onNodeWithText("since March 2").assertExists()
        compose.onNodeWithContentDescription("Not yet: Anger, Disgust, Jealous").performScrollTo().assertExists()
        assertEquals(
            listOf(
                emotions.seats
                    .first { it.isNew && it.seat == 29 }
                    .pebble!!
                    .momentId,
            ),
            opened,
        )
    }

    @Test
    fun `a family and an emotion the device does not know keep their rows under a fallback name`() {
        val emotions = AchievementsFixture.page.emotions!!
        val first = emotions.families.first()
        val unknown =
            first.copy(
                family = null,
                met = first.met.mapIndexed { i, s -> if (i == 0) s.copy(emotionId = "emotion-unknown", pebble = null) else s },
            )
        show(emotions.copy(families = listOf(unknown) + emotions.families.drop(1)))
        compose.onNodeWithText("Other emotions").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("An emotion", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun `a family slug this device has no word for reads as other emotions, never the slug`() {
        val emotions = AchievementsFixture.page.emotions!!
        val first = emotions.families.first()
        show(emotions.copy(families = listOf(first.copy(family = "awe")) + emotions.families.drop(1)))
        compose.onNodeWithText("Other emotions").performScrollTo().assertExists()
        compose.onNodeWithText("awe").assertDoesNotExist()
    }
}
