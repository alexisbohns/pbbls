package app.pbbls.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.Domain
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeRecordFlowMemory
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServicesModule
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * The Complete flow end to end (#1048, #1515): a bare moment's read page offers
 * "Make it a pebble", the valence, the emotion and the Life page are walked by tapping,
 * `complete_pebble` gets the moment id, the domains in tap order and the people, the success
 * screen shows the karma earned, and its exit returns to the read page.
 *
 * It does not assert the read page's refresh on return: under Robolectric the
 * re-shown sheet's lifecycle owner never reaches RESUMED, so its resume
 * refresh never fires, after Edit moment as much as after Complete.
 */
@HiltAndroidTest
class CompleteFlowTest : AppUiTest() {
    @Inject lateinit var moments: FakeMomentReadService

    @Inject lateinit var writes: FakeMomentWriteService

    @Inject lateinit var refs: FakeReferenceDataService

    @Inject lateinit var memory: FakeRecordFlowMemory

    @After
    fun clearCannedResponses() {
        FakeServicesModule.cannedResponses.clear()
    }

    /** Two bare moments on this week's Path, the first opened into Complete from its read page. */
    private fun openCompleteFromPath() {
        // Slugs with no catalog entry, so the tiles read the names below (D9 fallback).
        FakeServicesModule.cannedResponses["/v_emotions_with_palette"] =
            """
            [{"id":"emotion-calm","slug":"test-calm","name":"Calmish","emoji":"🙂","category_id":"cat-1",
              "category_slug":"test-cat","category_name":"Testing","primary_color":"#6B8F71FF",
              "secondary_color":"#A3C4A8FF","light_color":"#E4F0E6FF","surface_color":"#F3F8F4FF",
              "dark_color":"#2F4A34FF","shaded_color":"#4F6B54FF"}]
            """.trimIndent()
        refs.domains =
            listOf(
                Domain(id = "domain-1", slug = "test-domain", name = "Testworld", label = "Where tests live"),
                Domain(id = "domain-2", slug = "test-other", name = "Otherland", label = "Where others live"),
                Domain(id = "domain-3", slug = "test-third", name = "Thirdplace", label = "Where thirds live"),
                Domain(id = "domain-4", slug = "test-fourth", name = "Fourthspot", label = "Where fourths live"),
            )
        refs.souls = listOf(UiFixtures.soul(id = "soul-1", name = "Mo").copy(pebblesCount = 3))
        refs.hasLoaded = true
        moments.path =
            listOf(
                UiFixtures.pathMoment(id = "moment-1", name = "Swim", pebble = null, happenedAt = UiFixtures.thisWeek()),
                UiFixtures.pathMoment(id = "moment-2", name = "Hike", pebble = null, happenedAt = UiFixtures.thisWeek()),
            )
        moments.moments =
            mapOf(
                "moment-1" to UiFixtures.momentDetail(id = "moment-1", name = "Swim", pebble = null),
                "moment-2" to UiFixtures.momentDetail(id = "moment-2", name = "Hike", pebble = null),
            )
        skipOnboarding()
        launch()
        signIn()
        openPath()
        openFirstBareMoment()
    }

    private fun openFirstBareMoment() {
        compose.onAllNodesWithTag(JourneyTags.PATH_PEBBLE_ROW)[0].performClick()
        onText(R.string.complete_cta).performClick()
    }

    /** Valence, then the emotion, landing on Life. */
    private fun walkToLife() {
        compose
            .onNodeWithContentDescription(
                "${string(R.string.valence_group_medium_name)}, ${string(R.string.valence_highlight)}",
            ).performClick()
        onText(R.string.record_action_continue).performClick()
        compose.onNodeWithText("Calmish").performClick()
        onText(R.string.record_life_title).assertIsDisplayed()
    }

    @Test
    fun `an account's first pebble opens the valence help once and marks it seen`() {
        openCompleteFromPath()

        onText(R.string.record_valence_title).assertIsDisplayed()
        onText(R.string.valence_help_line_1).assertIsDisplayed()
        assertEquals(true, memory.isValenceHelpSeen("user-1"))
    }

    @Test
    fun `a bare moment becomes a pebble from its read page, two domains and a person on one page`() {
        // The account's first pebble would open the help sheet over the fan; the test above is that case.
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()

        onText(R.string.record_valence_title).assertIsDisplayed()
        // The fan arrives empty and Continue waits for a stone (D7): pick one first.
        compose.onNodeWithText(string(R.string.valence_ghost_prompt).uppercase()).assertIsDisplayed()
        walkToLife()
        // Nothing yet: the button waits, and there is no caption.
        compose.onNodeWithText("Where tests live").assertDoesNotExist()
        onText(R.string.complete_cta).assertIsNotEnabled()

        compose.onNodeWithText("Otherland").performClick()
        compose.onNodeWithText("Where others live").assertIsDisplayed()
        compose.onNodeWithText("Testworld").performClick()
        compose.onAllNodes(hasText("Mo") and isToggleable()).onFirst().performClick()
        onText(R.string.complete_cta).assertIsEnabled().performClick()

        val sent = writes.completed.single()
        assertEquals("moment-1", sent.momentId)
        assertEquals(listOf("domain-2", "domain-1"), sent.domainIds)
        assertEquals(listOf("soul-1"), sent.soulIds)
        assertEquals(emptyList<String>(), sent.collectionIds)
        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 4)).assertIsDisplayed()

        onText(R.string.richer_done).performClick()

        onText(R.string.richer_done).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertIsDisplayed()
    }

    @Test
    fun `a fourth domain changes nothing`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        walkToLife()

        listOf("Testworld", "Otherland", "Thirdplace", "Fourthspot").forEach { compose.onNodeWithText(it).performClick() }
        onText(R.string.complete_cta).performClick()

        assertEquals(listOf("domain-1", "domain-2", "domain-3"), writes.completed.single().domainIds)
    }

    @Test
    fun `a failed write keeps the page with its line, and the button retries`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        walkToLife()
        compose.onNodeWithText("Testworld").performClick()
        writes.failOnComplete = java.io.IOException("offline")

        onText(R.string.complete_cta).performClick()

        onText(R.string.complete_partial_failure).assertIsDisplayed()
        onText(R.string.record_life_title).assertIsDisplayed()
        assertEquals(0, writes.completed.size)

        onText(R.string.complete_cta).performClick()

        assertEquals("moment-1", writes.completed.single().momentId)
        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 4)).assertIsDisplayed()
    }

    @Test
    fun `the next flow shows what the last one picked as RECENT`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        onText(R.string.record_valence_title).assertIsDisplayed()
        walkToLife()
        onText(R.string.record_life_recent).assertDoesNotExist()
        compose.onNodeWithText("Otherland").performClick()
        onText(R.string.complete_cta).performClick()
        assertEquals(listOf("domain-2"), memory.recentDomainIds("user-1"))
        onText(R.string.richer_done).performClick()

        // A second flow, from the read page the first one returned to (the fake still serves the moment bare):
        // Otherland now sits in RECENT and in ALL.
        onText(R.string.complete_cta).performClick()
        walkToLife()
        onText(R.string.record_life_recent).assertIsDisplayed()
        compose.onAllNodes(hasText("Otherland") and isToggleable()).assertCountEquals(2)
    }
}
