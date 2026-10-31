package app.pbbls.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.Domain
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.FakeReferenceDataService
import app.pbbls.android.testing.FakeServicesModule
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * The Complete flow end to end (#1048): a bare moment's read page offers
 * "Make it a pebble", the six steps are walked by tapping, `complete_pebble`
 * gets the moment id, the success screen shows the karma earned, and its exit
 * returns to the read page.
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

    @After
    fun clearCannedResponses() {
        FakeServicesModule.cannedResponses.clear()
    }

    @Test
    fun `a bare moment becomes a pebble from its read page`() {
        // Slugs with no catalog entry, so the tiles read the names below (D9 fallback).
        FakeServicesModule.cannedResponses["/v_emotions_with_palette"] =
            """
            [{"id":"emotion-calm","slug":"test-calm","name":"Calmish","emoji":"🙂","category_id":"cat-1",
              "category_slug":"test-cat","category_name":"Testing","primary_color":"#6B8F71FF",
              "secondary_color":"#A3C4A8FF","light_color":"#E4F0E6FF","surface_color":"#F3F8F4FF",
              "dark_color":"#2F4A34FF","shaded_color":"#4F6B54FF"}]
            """.trimIndent()
        refs.domains = listOf(Domain(id = "domain-1", slug = "test-domain", name = "Testworld", label = "Where tests live"))
        moments.path = listOf(UiFixtures.pathMoment(id = "moment-1", name = "Swim", pebble = null, happenedAt = UiFixtures.thisWeek()))
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail(id = "moment-1", name = "Swim", pebble = null))
        skipOnboarding()
        launch()
        signIn()
        openPath()

        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        onText(R.string.complete_cta).performClick()

        onText(R.string.record_valence_title).assertIsDisplayed()
        onText(R.string.record_action_continue).performClick()
        compose.onNodeWithText("Calmish").performClick()
        compose.onNodeWithText("Testworld").performClick()
        onText(R.string.record_souls_title).assertIsDisplayed()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_collection_title).assertIsDisplayed()
        onText(R.string.record_action_skip).performClick()
        onText(R.string.record_privacy_title).assertIsDisplayed()
        onText(R.string.complete_cta).performClick()

        assertEquals("moment-1", writes.completed.single().momentId)
        compose.onNodeWithContentDescription(string(R.string.record_karma_a11y, 4)).assertIsDisplayed()

        onText(R.string.record_success_exit).performClick()

        onText(R.string.record_success_exit).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.more_actions_a11y)).assertIsDisplayed()
    }
}
