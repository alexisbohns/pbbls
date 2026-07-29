package app.pbbls.android.features.path.models

import app.pbbls.android.features.pebblemedia.models.FormSnap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * [PebbleDraftPayload] is the M47 cross-surface contract: web, iOS and Android
 * all read and write this exact shape. These tests pin the parts that would
 * break silently — omitted keys, the offset date format, and the sanitizing
 * rules — plus the draft-vs-publish gate split.
 */
class PebbleDraftPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val soulA = "11111111-1111-1111-1111-111111111111"
    private val collectionA = "22222222-2222-2222-2222-222222222222"
    private val emotionA = "33333333-3333-3333-3333-333333333333"
    private val domainA = "44444444-4444-4444-4444-444444444444"
    private val glyphA = "55555555-5555-5555-5555-555555555555"

    private fun keysOf(payload: PebbleDraftPayload): Set<String> =
        json.parseToJsonElement(json.encodeToString(payload)).jsonObject.keys

    // --- omitted keys ---------------------------------------------------------

    @Test
    fun `an empty draft encodes only the always-defaulted keys`() {
        val keys = keysOf(PebbleDraftPayload.from(PebbleDraft()))

        // Present because the composer always holds a real value for them.
        assertTrue(keys.contains("happened_at"))
        assertTrue(keys.contains("visibility"))

        // Absent, not null: nothing has been set.
        for (key in
            listOf(
                "name", "description", "emotion_id", "domain_ids", "soul_ids",
                "collection_ids", "glyph_id", "snaps", "intensity", "positiveness",
            )
        ) {
            assertFalse("$key should be omitted, not encoded as null", keys.contains(key))
        }
    }

    @Test
    fun `a quick capture with only a name encodes just that name, trimmed`() {
        val payload = PebbleDraftPayload.from(PebbleDraft(name = "  Argument with Lea  "))
        assertEquals("Argument with Lea", payload.name)
        assertNull(payload.emotionId)
        assertFalse(payload.isEmpty)
    }

    @Test
    fun `whitespace and newline only fields are omitted entirely`() {
        val payload = PebbleDraftPayload.from(PebbleDraft(name = "   ", description = "\n\t "))
        assertNull(payload.name)
        assertNull(payload.description)
        assertTrue("a whitespace-only draft is not worth saving", payload.isEmpty)
    }

    // --- valence decomposition ------------------------------------------------

    @Test
    fun `valence is stored decomposed, and both keys are omitted when unset`() {
        val unset = PebbleDraftPayload.from(PebbleDraft(name = "x"))
        assertNull(unset.intensity)
        assertNull(unset.positiveness)

        val set = PebbleDraftPayload.from(PebbleDraft(name = "x", valence = Valence.HIGHLIGHT_LARGE))
        assertEquals(3, set.intensity)
        assertEquals(1, set.positiveness)
    }

    @Test
    fun `every valence round-trips through the decomposed pair`() {
        for (valence in Valence.entries) {
            assertEquals(valence, Valence.orNull(valence.positiveness, valence.intensity))
        }
    }

    @Test
    fun `an absent or out-of-grid pair leaves the valence unset rather than guessing`() {
        // Deliberately NOT fromOrDefault's NEUTRAL_MEDIUM fallback: a draft with
        // no valence must hydrate the picker as unset.
        assertNull(Valence.orNull(null, null))
        assertNull(Valence.orNull(0, null))
        assertNull(Valence.orNull(7, 2))
        assertNull(Valence.orNull(0, 0))
    }

    // --- round trip -----------------------------------------------------------

    @Test
    fun `a fully populated payload survives an encode-decode round trip`() {
        val happenedAt = OffsetDateTime.of(2026, 7, 25, 17, 20, 0, 0, ZoneOffset.UTC)
        val draft =
            PebbleDraft(
                happenedAt = happenedAt,
                name = "Full",
                description = "Everything set",
                emotionId = emotionA,
                domainId = domainA,
                valence = Valence.LOWLIGHT_SMALL,
                soulIds = listOf(soulA),
                collectionId = collectionA,
                glyphId = glyphA,
                visibility = Visibility.PUBLIC,
            )
        val original = PebbleDraftPayload.from(draft)
        val decoded = json.decodeFromString<PebbleDraftPayload>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals(happenedAt, decoded.happenedAt)
    }

    @Test
    fun `unknown keys and a missing date decode without throwing`() {
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """{"name":"From another surface","unknown_key":42}""",
            )
        assertEquals("From another surface", decoded.name)
        assertNull(decoded.happenedAt)
    }

    // --- hydration and sanitizing (design D7) --------------------------------

    @Test
    fun `hydration drops souls and collections the user no longer owns`() {
        val goneSoul = "99999999-9999-9999-9999-999999999999"
        val payload =
            PebbleDraftPayload(
                name = "Stale",
                soulIds = listOf(soulA, goneSoul),
                collectionIds = listOf("88888888-8888-8888-8888-888888888888"),
                emotionId = emotionA,
                domainIds = listOf(domainA),
            )
        val hydrated =
            payload.toDraft(
                KnownDraftIds(soulIds = setOf(soulA), collectionIds = setOf(collectionA)),
            )

        assertEquals(listOf(soulA), hydrated.soulIds)
        assertNull("a collection the user lost is dropped", hydrated.collectionId)
        // Reference data is never sanitized — it is seeded, not user-owned.
        assertEquals(emotionA, hydrated.emotionId)
        assertEquals(domainA, hydrated.domainId)
    }

    @Test
    fun `hydration restores happened_at verbatim rather than re-stamping it`() {
        val captured = OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)
        val hydrated =
            PebbleDraftPayload(name = "Captured hours ago", happenedAt = captured)
                .toDraft(KnownDraftIds(emptySet(), emptySet()))
        assertEquals(captured, hydrated.happenedAt)
    }

    @Test
    fun `an empty payload hydrates to the composer defaults`() {
        val hydrated = PebbleDraftPayload().toDraft(KnownDraftIds(emptySet(), emptySet()))
        assertTrue(hydrated.name.isEmpty())
        assertNull(hydrated.valence)
        assertEquals(Visibility.PRIVATE, hydrated.visibility)
        assertFalse(hydrated.isValid)
    }

    // --- the draft/publish gate split (design D5) ----------------------------

    @Test
    fun `isSavableAsDraft is looser than isValid`() {
        val blank = PebbleDraft()
        assertFalse("nothing typed yet", blank.isSavableAsDraft())
        assertFalse(blank.isValid)

        val named = blank.copy(name = "Just a name")
        assertTrue("a name alone is a valid draft", named.isSavableAsDraft())
        assertFalse("but not enough to publish", named.isValid)

        val publishable =
            named.copy(emotionId = emotionA, domainId = domainA, valence = Valence.NEUTRAL_MEDIUM)
        assertTrue(publishable.isValid)
    }

    @Test
    fun `a photo alone makes a draft savable`() {
        val snap = FormSnap.Existing(id = "snap-1", storagePath = "user-1/snap-1")
        assertTrue(PebbleDraft().isSavableAsDraft(snap, "user-1"))
    }

    @Test
    fun `an existing snap survives a round trip so a draft keeps its photo`() {
        val snap = FormSnap.Existing(id = "snap-1", storagePath = "user-1/snap-1")
        val payload = PebbleDraftPayload.from(PebbleDraft(name = "With a photo"), snap, "user-1")
        val decoded = json.decodeFromString<PebbleDraftPayload>(json.encodeToString(payload))

        assertEquals("snap-1", decoded.existingSnap?.id)
        assertEquals("user-1/snap-1", decoded.existingSnap?.storagePath)
    }
}
