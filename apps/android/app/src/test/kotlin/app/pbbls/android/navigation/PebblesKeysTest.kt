package app.pbbls.android.navigation

import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.model.GlyphStroke
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Every key must survive a serialization round-trip, because that is exactly
 * what `rememberNavBackStack` does to restore a stack after process death. A
 * key that throws here is a screen that does not come back.
 */
class PebblesKeysTest {
    private val json = Json

    private val sampleItem =
        GlyphGridItem(
            glyph = Glyph(id = "g1", name = "Wave", strokes = emptyList(), viewBox = "0 0 200 200", userId = "u1"),
            price = 12,
            owned = false,
            createdAt = OffsetDateTime.parse("2026-09-01T10:00:00Z"),
            acquiredAt = null,
        )

    /** A real stroke, and an acquisition time with sub-seconds and a non-UTC offset. */
    private val ownedItem =
        GlyphGridItem(
            glyph =
                Glyph(
                    id = "g2",
                    strokes = listOf(GlyphStroke(d = "M30,100 Q70,40 100,100 Q130,160 170,100", width = 6.0)),
                    viewBox = "0 0 200 200",
                ),
            price = 7,
            owned = true,
            createdAt = null,
            acquiredAt = OffsetDateTime.parse("2026-09-01T10:00:00.123456+02:00"),
        )

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
                PebblesKey.Capture,
                PebblesKey.PebbleDetail(momentId = "m-1"),
                PebblesKey.PebbleDetail(momentId = "m-1", face = CardFace.VERSO),
                PebblesKey.GlyphDetail(sampleItem),
                PebblesKey.GlyphDetail(ownedItem),
                PebblesKey.EditPebble(momentId = "m-1"),
                PebblesKey.EditMoment(momentId = "m-1"),
                PebblesKey.CompletePebble(momentId = "m-1"),
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
    fun `a back stack saved before the split still decodes`() {
        val saved = """{"type":"app.pbbls.android.navigation.PebblesKey.PebbleDetail","pebbleId":"m-1"}"""
        val key = Json.decodeFromString(PebblesKey.serializer(), saved)
        assertEquals(PebblesKey.PebbleDetail(momentId = "m-1"), key)
    }

    @Test
    fun `a detail key saved before faces decodes as the recto`() {
        assertEquals(
            PebblesKey.PebbleDetail("m1", CardFace.RECTO),
            Json.decodeFromString<PebblesKey.PebbleDetail>("""{"pebbleId":"m1"}"""),
        )
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
                PebblesKey.Capture,
                PebblesKey.EditPebble(momentId = "m-1"),
                PebblesKey.EditMoment(momentId = "m-1"),
                PebblesKey.CompletePebble(momentId = "m-1"),
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
                PebblesKey.PebbleDetail("p"),
                PebblesKey.GlyphDetail(sampleItem),
            )
        pushes.forEach {
            assertTrue("$it should render the bar", it is BarKey)
            assertTrue("$it must not be a tab", it !is TopLevelKey)
        }
    }
}
