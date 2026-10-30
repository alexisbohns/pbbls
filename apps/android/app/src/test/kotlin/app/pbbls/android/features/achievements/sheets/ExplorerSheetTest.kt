package app.pbbls.android.features.achievements.sheets

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** The Explorer sheet (#1307): its rows push, open and pull only what the server lets them. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi", sdk = [PIXEL_TEST_SDK])
class ExplorerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val pushed = mutableListOf<AchievementSheet>()
    private val opened = mutableListOf<String>()
    private val pulled = mutableListOf<CompletionStack>()

    private fun show(page: AchievementsPage = AchievementsFixture.page) {
        compose.setContent {
            PebblesTheme {
                ExplorerSheet(
                    explorer = page.explorer!!,
                    emotions = page.emotions,
                    domains = page.domains,
                    lookups = AchievementsFixture.lookups,
                    onPush = { pushed += it },
                    onOpenMoment = { opened += it },
                    onPull = { pulled += it },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }

    @Test
    fun `the domains and emotions rows push their own sheet`() {
        show()
        compose
            .onNodeWithContentDescription(
                "Emotions, 23 of 38",
                substring = true,
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.onNodeWithContentDescription("Domains, 14 of 18", substring = true).performScrollTo().performClick()
        assertEquals(listOf(AchievementSheet.Emotions, AchievementSheet.Domains), pushed)
    }

    @Test
    fun `a six-firsts row opens its pebble`() {
        show()
        compose.onNodeWithContentDescription("Lowlight, Gym, finally", substring = true).performScrollTo().performClick()
        assertEquals(
            listOf(
                AchievementsFixture.page.explorer!!
                    .sixFirsts[3]
                    .pebble!!
                    .momentId,
            ),
            opened,
        )
    }

    @Test
    fun `an unnamed moment reads as untitled, and a snapshot without a moment id is not a button`() {
        val page = AchievementsFixture.page
        val explorer = page.explorer!!
        val gone = explorer.sixFirsts[0].let { it.copy(pebble = it.pebble!!.copy(momentId = null, name = null)) }
        show(page.copy(explorer = explorer.copy(sixFirsts = listOf(gone) + explorer.sixFirsts.drop(1))))
        compose
            .onNodeWithContentDescription("Small, Untitled", substring = true)
            .performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `a task or a first not done yet reads "not yet"`() {
        val page = AchievementsFixture.page
        val explorer = page.explorer!!
        val open = explorer.sixFirsts[0].copy(done = false, on = null, pebble = null, isNew = false)
        val task = explorer.firstFive[4].copy(done = false, on = null, subject = null, isNew = false)
        show(
            page.copy(
                explorer =
                    explorer.copy(
                        firstFive = explorer.firstFive.dropLast(1) + task,
                        sixFirsts =
                            listOf(open) + explorer.sixFirsts.drop(1),
                    ),
            ),
        )
        compose.onNodeWithContentDescription("Small, not yet").performScrollTo().assertExists()
        compose.onAllNodesWithContentDescription(", not yet", substring = true).assertCountEquals(2)
    }

    @Test
    fun `the ready mark pulls the six firsts' ceremony`() {
        // The design fixture's completion is already pulled; ready is the server's say.
        val page = AchievementsFixture.page
        val explorer = page.explorer!!
        show(page.copy(explorer = explorer.copy(completion = explorer.completion!!.copy(ready = true, pulledAt = null))))
        compose.onNodeWithContentDescription("The six firsts: the ceremony is ready.").performClick()
        assertEquals(listOf(CompletionStack.SIX_FIRSTS), pulled)
    }
}
