package app.pbbls.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.PebbleCompletePayload
import app.pbbls.android.core.model.Visibility
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
        armFixtures()
        skipOnboarding()
        launch()
        signIn()
        openPath()
        openFirstBareMoment()
    }

    private fun armFixtures() {
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

    // ---- Make it richer: the end screen's rows edit the finished pebble (D12) ----

    private fun completeFromLife() {
        compose.onNodeWithText("Testworld").performClick()
        onText(R.string.complete_cta).performClick()
        onText(R.string.richer_done).assertIsDisplayed()
    }

    @Test
    fun `the end screen shows its rows, a visibility change is one partial write, and Done lands on the read page`() {
        memory.markValenceHelpSeen("user-1")
        refs.collections = listOf(PebbleCollection(id = "c-1", name = "Wins"))
        openCompleteFromPath()
        walkToLife()
        completeFromLife()

        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 4)).assertIsDisplayed()
        onText(R.string.richer_section).assertIsDisplayed()
        onText(R.string.richer_add_collection).assertIsDisplayed()
        onText(R.string.richer_add_words).assertIsDisplayed()
        // No chrome on the end screen: nothing to close but Done.
        compose.onNodeWithContentDescription(string(R.string.action_close)).assertDoesNotExist()
        // A moment from the Path offers no Record another.
        onText(R.string.richer_record_another).assertDoesNotExist()

        onText(R.string.richer_who_can_see).performClick()
        onText(R.string.visibility_public).performClick()
        compose.waitForIdle()

        assertEquals(2, writes.completed.size)
        assertEquals(PebbleCompletePayload("moment-1", visibility = Visibility.PUBLIC), writes.completed.last())
        // The row now reads the new value, beside its title.
        compose.onNode(hasText(string(R.string.richer_who_can_see)) and hasText(string(R.string.visibility_public))).assertExists()

        onText(R.string.richer_done).performClick()
        onText(R.string.richer_done).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertIsDisplayed()
    }

    @Test
    fun `Add to a collection is absent when the account has none`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        walkToLife()
        completeFromLife()

        onText(R.string.richer_who_can_see).assertIsDisplayed()
        onText(R.string.richer_add_collection).assertDoesNotExist()
    }

    @Test
    fun `back on the end screen leaves it, and nothing was lost`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        walkToLife()
        completeFromLife()

        pressBack()

        onText(R.string.richer_done).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertIsDisplayed()
    }

    @Test
    fun `Record another from a capture opens a fresh photo step with nothing left over`() {
        memory.markValenceHelpSeen("user-1")
        armFixtures()
        writes.nextMomentId = "moment-9"
        moments.moments = moments.moments + ("moment-9" to UiFixtures.momentDetail(id = "moment-9", name = "Walk", pebble = null))
        skipOnboarding()
        launch()
        signIn()
        openPath()
        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()
        onText(R.string.capture_just_write).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Walk")
        onText(R.string.capture_save_and_pebble).performClick()
        walkToLife()
        completeFromLife()

        onText(R.string.richer_record_another).performClick()
        compose.waitForIdle()

        onText(R.string.record_photo_title).assertIsDisplayed()
        onText(R.string.richer_done).assertDoesNotExist()
        onText(R.string.capture_just_write).performClick()
        // The first capture's name is gone: the save waits for one.
        onText(R.string.capture_just_save).assertIsNotEnabled()
        assertEquals(listOf("Walk"), writes.saved.map { it.name })
    }

    @Test
    fun `a bare moment says You started this once Complete was closed with answers kept`() {
        memory.markValenceHelpSeen("user-1")
        openCompleteFromPath()
        compose
            .onNodeWithContentDescription(
                "${string(R.string.valence_group_medium_name)}, ${string(R.string.valence_highlight)}",
            ).performClick()
        compose.onNodeWithContentDescription(string(R.string.action_close)).performClick()
        compose.waitForIdle()

        // Back on the bare page, which reads again on its next entry: leave it and open it afresh.
        pressBack()
        compose.onAllNodesWithTag(JourneyTags.PATH_PEBBLE_ROW)[0].performClick()

        onText(R.string.bare_place_started).assertIsDisplayed()
        onText(R.string.complete_cta).assertDoesNotExist()
        // Continue resumes the kept answers: the valence is answered, so it lands on the emotion.
        onText(R.string.record_action_continue).performClick()
        onText(R.string.record_emotion_title).assertIsDisplayed()
    }

    @Test
    fun `Add a few words on the bare page opens Edit moment`() {
        armFixtures()
        skipOnboarding()
        launch()
        signIn()
        openPath()
        compose.onAllNodesWithTag(JourneyTags.PATH_PEBBLE_ROW)[0].performClick()

        onText(R.string.richer_add_words).performClick()

        onText(R.string.edit_moment).assertIsDisplayed()
    }
}
