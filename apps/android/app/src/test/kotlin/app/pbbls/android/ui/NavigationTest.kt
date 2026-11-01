package app.pbbls.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSoulsService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.inject.Inject

/**
 * Every navigation key reachable from the screen that opens it (#857), through
 * the real UI: a tap on the parent, the real `Navigator` call bound in
 * `PebblesEntryProvider`, the real entry on screen. `NavigatorTest` proves the
 * stack arithmetic; this proves each callback is wired to the key it should be,
 * which nothing else does — a lambda bound to the wrong key compiles.
 *
 * `Onboarding` and `AcceptInvite` are pushed by `RootScreen`, not by a tap, and
 * are covered in [RootGateTest].
 *
 * Each test arms only the fake its parent needs to show the trigger.
 */
@HiltAndroidTest
class NavigationTest : AppUiTest() {
    @Inject lateinit var moments: FakeMomentReadService

    @Inject lateinit var profile: FakeProfileService

    @Inject lateinit var souls: FakeSoulsService

    @Inject lateinit var collections: FakeCollectionsService

    @Inject lateinit var glyphMarket: FakeGlyphMarketService

    @Inject lateinit var logs: FakeLogsService

    private fun signedIn() {
        skipOnboarding()
        launch()
        signIn()
    }

    /** A bar item, told apart from same-named screen text by being selectable. */
    private fun onTab(id: Int): SemanticsNodeInteraction = compose.onNode(hasText(string(id)) and isSelectable())

    private fun onDescription(id: Int): SemanticsNodeInteraction = compose.onNodeWithContentDescription(string(id))

    // ---- The bar ----

    @Test
    fun `every tab is reachable from the bar`() {
        signedIn()
        onTab(R.string.tab_home).assertIsSelected()
        for (tab in listOf(R.string.tab_path, R.string.tab_people, R.string.tab_collections, R.string.tab_you, R.string.tab_home)) {
            onTab(tab).performClick()
            onTab(tab).assertIsSelected()
        }
    }

    // ---- Path ----

    @Test
    fun `Path opens a moment, and its menu opens Edit moment and Edit pebble`() {
        moments.path = listOf(UiFixtures.pathMoment(happenedAt = UiFixtures.thisWeek()))
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail())
        signedIn()
        openPath()

        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        onDescription(R.string.more_actions_a11y).assertIsDisplayed().performClick()
        onText(R.string.edit_moment).performClick()
        onText(R.string.edit_moment).assertIsDisplayed()
        onText(R.string.create_name_placeholder).assertIsDisplayed()

        pressBack()

        onDescription(R.string.more_actions_a11y).performClick()
        onText(R.string.edit_pebble_title).performClick()
        onText(R.string.edit_pebble_title).assertIsDisplayed()
        onText(R.string.create_mood_header).assertIsDisplayed()
    }

    /** A moment without your pebble has nothing for Edit pebble to open (R7). */
    @Test
    fun `a bare moment's menu offers Edit moment only`() {
        moments.path = listOf(UiFixtures.pathMoment(pebble = null, happenedAt = UiFixtures.thisWeek()))
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail(pebble = null))
        signedIn()
        openPath()

        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        onDescription(R.string.more_actions_a11y).performClick()

        onText(R.string.edit_moment).assertIsDisplayed()
        onText(R.string.edit_pebble_title).assertDoesNotExist()
    }

    @Test
    fun `new pebble opens the capture flow`() {
        signedIn()
        openPath()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()

        onText(R.string.record_photo_title).assertIsDisplayed()
        onText(R.string.record_valence_title).assertDoesNotExist()
    }

    // ---- You ----

    @Test
    fun `Profile opens Settings`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onDescription(R.string.settings_title).performClick()

        onText(R.string.settings_title).assertIsDisplayed()
    }

    @Test
    fun `Profile's Souls tile switches to the People tab`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onText(R.string.souls_title).performClick()

        onTab(R.string.tab_people).assertIsSelected()
        onDescription(R.string.souls_add_a11y).assertIsDisplayed()
    }

    @Test
    fun `Profile's Collections header switches to the Collections tab`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        compose
            .onNode(hasText(string(R.string.profile_collections_header)) and hasAnyAncestor(hasScrollAction()))
            .performScrollTo()
            .performClick()

        onTab(R.string.tab_collections).assertIsSelected()
        onDescription(R.string.collections_add_a11y).assertIsDisplayed()
    }

    @Test
    fun `Profile opens Glyphs, Connections, Achievements and Lab`() {
        signedIn()
        val pushes =
            listOf(
                R.string.glyphs_title,
                R.string.connections_title,
                R.string.achievements_title,
                R.string.lab_title,
            )
        for (title in pushes) {
            onTab(R.string.tab_you).performClick()
            onText(title).performScrollTo().performClick()
            onText(title).assertIsDisplayed()
            onText(R.string.profile_title).assertDoesNotExist()
            pressBack()
            onText(R.string.profile_title).assertIsDisplayed()
        }
    }

    @Test
    fun `Profile opens a collection's detail`() {
        profile.collections = listOf(UiFixtures.collection(name = "Summer"))
        collections.collections = profile.collections
        signedIn()
        onTab(R.string.tab_you).performClick()

        compose.onNodeWithText("Summer").performScrollTo().performClick()

        onText(R.string.collection_detail_empty_message).assertIsDisplayed()
    }

    @Test
    fun `Profile with no collections opens the collection form`() {
        signedIn()
        onTab(R.string.tab_you).performClick()

        onText(R.string.profile_collection_new).performScrollTo().performClick()

        onText(R.string.action_cancel).assertIsDisplayed()
        onText(R.string.profile_collection_new).assertIsDisplayed()
    }

    // ---- People ----

    @Test
    fun `People opens a soul, and the soul opens its edit form`() {
        souls.souls = listOf(UiFixtures.soul(name = "Mo"))
        signedIn()
        onTab(R.string.tab_people).performClick()

        compose.onNodeWithText("Mo").performClick()
        onText(R.string.soul_detail_empty_message).assertIsDisplayed()
        onText(R.string.pebble_detail_edit).performClick()

        onText(R.string.soul_edit_title).assertIsDisplayed()
    }

    /** Soul rows open the moment's read page, not the edit form (R9). */
    @Test
    fun `a soul's moment row opens the read page`() {
        souls.souls = listOf(UiFixtures.soul(name = "Mo"))
        moments.withSoul = listOf(UiFixtures.momentDetail(name = "Coffee with Mo"))
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "Coffee with Mo"))
        signedIn()
        onTab(R.string.tab_people).performClick()
        compose.onNodeWithText("Mo").performClick()

        compose.onNodeWithText("Coffee with Mo").performClick()

        onDescription(R.string.more_actions_a11y).assertIsDisplayed()
        assertEquals(listOf("moment-1"), moments.loadedMomentIds)
    }

    @Test
    fun `People opens the soul form`() {
        signedIn()
        onTab(R.string.tab_people).performClick()

        onDescription(R.string.souls_add_a11y).performClick()

        onText(R.string.create_soul_title).assertIsDisplayed()
    }

    // ---- Collections ----

    @Test
    fun `Collections opens a collection, and the collection opens its edit form`() {
        collections.collections = listOf(UiFixtures.collection(name = "Summer"))
        signedIn()
        onTab(R.string.tab_collections).performClick()

        compose.onNodeWithText("Summer").performClick()
        onText(R.string.collection_detail_empty_message).assertIsDisplayed()
        onText(R.string.pebble_detail_edit).performClick()

        onText(R.string.collection_edit_title).assertIsDisplayed()
    }

    /** Collection rows open the moment's read page, not the edit form (R9). */
    @Test
    fun `a collection's moment row opens the read page`() {
        collections.collections = listOf(UiFixtures.collection(name = "Summer"))
        moments.inCollection = listOf(UiFixtures.momentDetail(name = "Beach day"))
        moments.moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "Beach day"))
        signedIn()
        onTab(R.string.tab_collections).performClick()
        compose.onNodeWithText("Summer").performClick()

        compose.onNodeWithText("Beach day").performClick()

        onDescription(R.string.more_actions_a11y).assertIsDisplayed()
        assertEquals(listOf("moment-1"), moments.loadedMomentIds)
    }

    @Test
    fun `Collections opens the collection form`() {
        signedIn()
        onTab(R.string.tab_collections).performClick()

        onDescription(R.string.collections_add_a11y).performClick()

        onText(R.string.profile_collection_new).assertIsDisplayed()
    }

    // ---- Glyphs ----

    @Test
    fun `Glyphs opens the carve screen`() {
        signedIn()
        onTab(R.string.tab_you).performClick()
        onText(R.string.glyphs_title).performClick()

        onDescription(R.string.glyphs_carve_a11y).performClick()

        onText(R.string.carve_title).assertIsDisplayed()
    }

    @Test
    fun `Glyphs opens a community glyph's detail`() {
        glyphMarket.community = listOf(UiFixtures.communityGlyph(name = "Spiral"))
        signedIn()
        onTab(R.string.tab_you).performClick()
        onText(R.string.glyphs_title).performClick()
        onText(R.string.glyph_tab_commu).performClick()

        compose.onNodeWithText("Spiral").performClick()

        onText(R.string.glyph_drawer_swap).assertIsDisplayed()
    }

    // ---- Connections ----

    @Test
    fun `Connections opens Invite`() {
        signedIn()
        onTab(R.string.tab_you).performClick()
        onText(R.string.connections_title).performClick()

        onText(R.string.connections_invite_action).performClick()

        onText(R.string.connections_invite_explainer).assertIsDisplayed()
    }

    // ---- Lab ----

    @Test
    fun `Lab opens an announcement`() {
        val announcement = UiFixtures.log(title = "Pebbles on Android")
        logs.announcements = listOf(announcement)
        logs.log = announcement
        signedIn()
        onTab(R.string.tab_you).performClick()
        onText(R.string.lab_title).performScrollTo().performClick()

        compose.onNodeWithText("Pebbles on Android").performClick()

        compose.onNodeWithText("Pebbles on Android, in short.").assertIsDisplayed()
    }

    @Test
    fun `Lab opens a full log list`() {
        logs.changelog = listOf(UiFixtures.log(species = LogSpecies.FEATURE, title = "Faster Path"))
        signedIn()
        onTab(R.string.tab_you).performClick()
        onText(R.string.lab_title).performScrollTo().performClick()

        onText(R.string.lab_see_all).performScrollTo().performClick()

        onText(R.string.lab_section_changelog).assertIsDisplayed()
    }

    // ---- The signed-out funnel ----

    @Test
    fun `Welcome opens sign-up`() {
        launch()
        signOut()

        onText(R.string.welcome_create_account).performClick()

        compose.onNodeWithTag(JourneyTags.AUTH_SUBMIT).assertIsDisplayed()
        onText(R.string.auth_submit_signup).assertIsDisplayed()
    }

    @Test
    fun `Welcome opens log-in`() {
        launch()
        signOut()

        onText(R.string.welcome_log_in).performClick()

        onText(R.string.auth_submit_login).assertIsDisplayed()
    }
}
