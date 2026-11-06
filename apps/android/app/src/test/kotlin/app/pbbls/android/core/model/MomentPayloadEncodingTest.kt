package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class MomentPayloadEncodingTest {
    private val snap =
        PebbleSnapPayload(
            id = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            storagePath = "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            sortOrder = 0,
        )
    private val at = OffsetDateTime.parse("2026-10-12T08:40:00.987+02:00")

    private fun encoded(payload: MomentSavePayload) = MomentWireJson.encodeToJsonElement(MomentSavePayload.serializer(), payload)

    @Test
    fun `capture sends name, whole-second date and the one snap`() {
        val expected =
            """
            {"name":"Swim at dawn","happened_at":"2026-10-12T06:40:00Z",
             "snaps":[{"id":"9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","storage_path":"3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","sort_order":0}]}
            """.trimIndent()
        assertEquals(Json.parseToJsonElement(expected), encoded(MomentSavePayload.capture("  Swim at dawn ", "", at, snap)))
    }

    @Test
    fun `a photo-only capture leaves the name out entirely`() {
        val expected =
            """{"happened_at":"2026-10-12T06:40:00Z","snaps":[{"id":"9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","storage_path":"3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d","sort_order":0}]}"""
        assertEquals(Json.parseToJsonElement(expected), encoded(MomentSavePayload.capture("   ", "  ", at, snap)))
    }

    @Test
    fun `a name-only capture leaves snaps out`() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"Walk","happened_at":"2026-10-12T06:40:00Z"}"""),
            encoded(MomentSavePayload.capture("Walk", "", at, null)),
        )
    }

    @Test
    fun `capture sends the words trimmed, between the name and the date`() {
        val expected =
            """{"name":"Coffee with Léa","description":"We sat by the canal.\nUntil the light went.","happened_at":"2026-10-12T06:40:00Z"}"""
        assertEquals(
            Json.parseToJsonElement(expected),
            encoded(MomentSavePayload.capture("Coffee with Léa", "  We sat by the canal.\nUntil the light went.\n ", at, null)),
        )
    }

    @Test
    fun `a blank description is left out, even beside a photo`() {
        val json = encoded(MomentSavePayload.capture("Walk", " \n ", at, snap)).toString()
        assertEquals(false, json.contains("description"))
    }

    @Test
    fun `an edit sends every moment key, empty strings to clear, and the whole snap set`() {
        val expected =
            """{"id":"m-1","name":"","description":"","happened_at":"2026-10-12T06:40:00Z","snaps":[]}"""
        assertEquals(
            Json.parseToJsonElement(expected),
            encoded(MomentSavePayload.edit(id = "m-1", name = " ", description = "\n", happenedAt = at, snaps = emptyList())),
        )
    }

    @Test
    fun `complete sends every pebble key and never a glyph, card or inline creation key`() {
        val answers =
            PebbleAnswers(
                valence = Valence.LOWLIGHT_LARGE,
                emotionId = "e-1",
                domainIds = listOf("d-1", "d-2"),
                soulIds = listOf("s-1", "s-2"),
                collectionId = null,
                visibility = Visibility.PRIVATE,
            )
        val expected =
            """
            {"moment_id":"m-1","emotion_id":"e-1","intensity":3,"positiveness":-1,"visibility":"private",
             "domain_ids":["d-1","d-2"],"soul_ids":["s-1","s-2"],"collection_ids":[]}
            """.trimIndent()
        assertEquals(
            Json.parseToJsonElement(expected),
            MomentWireJson.encodeToJsonElement(PebbleCompletePayload.serializer(), PebbleCompletePayload.from("m-1", answers)),
        )
    }

    private val draft =
        PebbleDraft(
            emotionId = "e-1",
            valence = Valence.LOWLIGHT_LARGE,
            domainId = "d-1",
            collectionId = "c-1",
            visibility = Visibility.PRIVATE,
            prefillDomainId = "d-1",
            prefillCollectionId = "c-1",
            prefilled = true,
        )

    private fun wire(p: PebbleCompletePayload) = MomentWireJson.encodeToString(PebbleCompletePayload.serializer(), p)

    @Test
    fun `an edit that left domains and collection alone omits both keys`() {
        assertEquals(
            """{"moment_id":"m","emotion_id":"e-1","intensity":3,"positiveness":-1,"visibility":"private","soul_ids":[]}""",
            wire(PebbleCompletePayload.edit("m", draft)),
        )
    }

    @Test
    fun `an edit that changed the domain replaces the set with the pick`() {
        assertEquals(
            """{"moment_id":"m","emotion_id":"e-1","intensity":3,"positiveness":-1,"visibility":"private","domain_ids":["d9"],"soul_ids":[]}""",
            wire(PebbleCompletePayload.edit("m", draft.copy(domainId = "d9"))),
        )
    }

    @Test
    fun `an end-screen visibility edit sends only that key`() {
        assertEquals(
            """{"moment_id":"m","visibility":"private"}""",
            wire(PebbleCompletePayload(momentId = "m", visibility = Visibility.PRIVATE)),
        )
    }

    @Test
    fun `a removed collection sends an empty list`() {
        assertEquals(
            """{"moment_id":"m","collection_ids":[]}""",
            wire(PebbleCompletePayload(momentId = "m", collectionIds = emptyList())),
        )
        val removed = PebbleCompletePayload.edit("m", draft.copy(collectionId = null))
        assertEquals(emptyList<String>(), removed.collectionIds)
        assertNull(removed.domainIds)
    }

    @Test
    fun `a draft with no prefill always sends both sets`() {
        val fresh = PebbleCompletePayload.edit("m", draft.copy(prefilled = false, prefillDomainId = null, prefillCollectionId = null))
        assertEquals(listOf("d-1"), fresh.domainIds)
        assertEquals(listOf("c-1"), fresh.collectionIds)
    }
}
