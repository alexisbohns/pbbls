package app.pbbls.android.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.MomentPebble
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Inject

/**
 * The read page keeps its scroll across a trip to an entry pushed over it and back. Read to
 * its foot, it came back a bar's height higher (a held card scarcely taller than the screen,
 * at its top): the pushed entry has no bar, the bar snaps away while the page is still laid
 * out under the transition, and the grown viewport clamped the scroll that was then saved.
 */
@HiltAndroidTest
class DetailScrollTest : AppUiTest() {
    @Inject lateinit var moments: FakeMomentReadService

    private val scrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun range() = compose.onNode(scrollable).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun scroll(): Float = range().value()

    @Test
    fun `a card read to its foot is still there after Edit moment`() {
        val foot = readToTheFoot(pebble = UiFixtures.momentPebble())

        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).performClick()
        onText(R.string.edit_moment).performClick()
        // The edit entry covers the page: its ⋮ is gone with it.
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertDoesNotExist()
        pressBack()

        assertEquals(foot, scroll(), 0.5f)
    }

    @Test
    fun `a bare moment read to its foot is still there after Make it a pebble`() {
        val foot = readToTheFoot(pebble = null)

        onText(R.string.complete_cta).performSemanticsAction(SemanticsActions.OnClick)
        onText(R.string.record_valence_title).assertExists()
        pressBack()

        assertEquals(foot, scroll(), 0.5f)
    }

    /** Opens the moment from the Path, with words enough to scroll, and reads it to its foot. */
    private fun readToTheFoot(pebble: MomentPebble?): Float {
        moments.path =
            listOf(
                UiFixtures.pathMoment(
                    id = "moment-1",
                    pebble = if (pebble == null) null else UiFixtures.pathPebble(),
                    happenedAt = UiFixtures.thisWeek(),
                ),
            )
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail(id = "moment-1", description = LONG_DESCRIPTION, pebble = pebble))
        skipOnboarding()
        launch()
        signIn()
        openPath()
        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        compose.onNode(scrollable).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, Float.MAX_VALUE) }
        compose.waitForIdle()
        val foot = scroll()
        assertTrue("the page scrolls", foot > 0f)
        assertEquals("read to its foot", range().maxValue(), foot)
        return foot
    }

    private companion object {
        val LONG_DESCRIPTION = List(60) { "A line of the moment's words, number $it." }.joinToString("\n")
    }
}
