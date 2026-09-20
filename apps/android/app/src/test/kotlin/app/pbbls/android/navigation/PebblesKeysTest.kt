package app.pbbls.android.navigation

import app.pbbls.android.features.auth.AuthMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every key must survive a serialization round-trip, because that is exactly
 * what `rememberNavBackStack` does to restore a stack after process death. A
 * key that throws here is a screen that does not come back.
 */
class PebblesKeysTest {
    private val json = Json

    private fun roundTrip(key: PebblesKey): PebblesKey =
        json.decodeFromString(PebblesKey.serializer(), json.encodeToString(PebblesKey.serializer(), key))

    @Test
    fun `every key round-trips through the polymorphic serializer`() {
        val keys: List<PebblesKey> =
            listOf(
                PebblesKey.Path,
                PebblesKey.People,
                PebblesKey.Collections,
                PebblesKey.You,
                PebblesKey.SoulDetail("soul-1"),
                PebblesKey.CollectionDetail("col-1"),
                PebblesKey.Connections,
                PebblesKey.Glyphs,
                PebblesKey.Achievements,
                PebblesKey.Lab,
                PebblesKey.LabAnnouncement("log-1"),
                PebblesKey.LabLogList("announcements"),
                PebblesKey.RecordFlow(resumeDraftId = null),
                PebblesKey.RecordFlow(resumeDraftId = "draft-1"),
                PebblesKey.CreatePebble(resumeDraftId = null),
                PebblesKey.CreatePebble(resumeDraftId = "draft-1"),
                PebblesKey.Drafts,
                PebblesKey.PebbleDetail("pebble-1"),
                PebblesKey.EditPebble("pebble-1"),
                PebblesKey.Settings,
                PebblesKey.SoulForm(soulId = null),
                PebblesKey.SoulForm(soulId = "soul-1"),
                PebblesKey.CollectionForm(collectionId = null),
                PebblesKey.CollectionForm(collectionId = "col-1"),
                PebblesKey.Invite,
                PebblesKey.AcceptInvite("token-1"),
                PebblesKey.GlyphCarve,
                PebblesKey.Onboarding,
                PebblesKey.Welcome,
                PebblesKey.Auth(AuthMode.LOGIN),
                PebblesKey.Auth(AuthMode.SIGNUP),
            )

        keys.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `the four tabs are TopLevelKeys and every TopLevelKey is a BarKey`() {
        val tabs = listOf(PebblesKey.Path, PebblesKey.People, PebblesKey.Collections, PebblesKey.You)
        tabs.forEach {
            assertTrue("$it should be a TopLevelKey", it is TopLevelKey)
            assertTrue("$it should be a BarKey", it is BarKey)
        }
        assertEquals(tabs, PebblesKey.tabs)
    }

    @Test
    fun `modal keys are not BarKeys`() {
        val modals: List<PebblesKey> =
            listOf(
                PebblesKey.RecordFlow(null),
                PebblesKey.CreatePebble(null),
                PebblesKey.Drafts,
                PebblesKey.PebbleDetail("p"),
                PebblesKey.EditPebble("p"),
                PebblesKey.Settings,
                PebblesKey.SoulForm(null),
                PebblesKey.CollectionForm(null),
                PebblesKey.Invite,
                PebblesKey.AcceptInvite("t"),
                PebblesKey.GlyphCarve,
                PebblesKey.Onboarding,
                PebblesKey.Welcome,
                PebblesKey.Auth(AuthMode.LOGIN),
            )
        modals.forEach { assertTrue("$it must not render the bar", it !is BarKey) }
    }

    @Test
    fun `browse pushes render the bar but are not tabs`() {
        val pushes: List<PebblesKey> =
            listOf(
                PebblesKey.SoulDetail("s"),
                PebblesKey.CollectionDetail("c"),
                PebblesKey.Connections,
                PebblesKey.Glyphs,
                PebblesKey.Achievements,
                PebblesKey.Lab,
                PebblesKey.LabAnnouncement("l"),
                PebblesKey.LabLogList("m"),
            )
        pushes.forEach {
            assertTrue("$it should render the bar", it is BarKey)
            assertTrue("$it must not be a tab", it !is TopLevelKey)
        }
    }
}
