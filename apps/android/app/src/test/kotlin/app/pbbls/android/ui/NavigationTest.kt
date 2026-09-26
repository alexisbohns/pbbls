package app.pbbls.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.model.LogSpecies
import app.pbbls.android.core.model.PebbleDraftPayload
import app.pbbls.android.testing.AppUiTest
import app.pbbls.android.testing.FakeCollectionsService
import app.pbbls.android.testing.FakeGlyphMarketService
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.FakePathService
import app.pbbls.android.testing.FakePebbleDetailService
import app.pbbls.android.testing.FakePebbleDraftsService
import app.pbbls.android.testing.FakeProfileService
import app.pbbls.android.testing.FakeSoulsService
import app.pbbls.android.testing.UiFixtures
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import java.time.OffsetDateTime
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
    @Inject lateinit var path: FakePathService

    @Inject lateinit var pebbleDetail: FakePebbleDetailService

    @Inject lateinit var drafts: FakePebbleDraftsService

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
        for (tab in listOf(R.string.tab_people, R.string.tab_collections, R.string.tab_you, R.string.tab_path)) {
            onTab(tab).performClick()
            onTab(tab).assertIsSelected()
        }
    }

    // ---- Path ----

    @Test
    fun `Path opens a pebble's detail, and the detail opens its edit form`() {
        path.pebbles = listOf(UiFixtures.pebble())
        pebbleDetail.detail = UiFixtures.pebbleDetail()
        signedIn()

        compose.onNodeWithTag(JourneyTags.PATH_PEBBLE_ROW).performClick()
        onText(R.string.pebble_detail_edit).assertIsDisplayed().performClick()

        onText(R.string.edit_pebble_title).assertIsDisplayed()
    }

    @Test
    fun `new pebble opens the record flow`() {
        signedIn()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performClick()

        onText(R.string.record_photo_title).assertIsDisplayed()
    }

    @Test
    fun `long-pressing new pebble opens the all-at-once composer`() {
        // A pebble this week, so the empty week's own "New pebble" button is not on screen too.
        path.pebbles = listOf(UiFixtures.pebble())
        signedIn()

        compose.onNodeWithTag(JourneyTags.NEW_PEBBLE).performTouchInput { longClick() }

        onText(R.string.create_new_pebble).assertIsDisplayed()
    }

    @Test
    fun `the drafts entry opens Drafts`() {
        drafts.records +=
            PebbleDraftRecord(id = "draft-1", payload = PebbleDraftPayload(name = "Half a thought"), updatedAt = OffsetDateTime.now())
        signedIn()

        onText(R.string.drafts_entry, 1).performClick()

        onText(R.string.drafts_title).assertIsDisplayed()
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
