package app.pbbls.android.features.path.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [PebbleDraft.isValid] (the Save-button gate) and the
 * [PebbleDraft.from] edit-prefill factory — every mandatory field maps across, a
 * missing domain leaves the draft invalid, and the valence derives from the
 * detail's `(positiveness, intensity)` pair. The source detail is decoded from
 * JSON because [PebbleDetail]'s embedded-relation shape is what the decoder emits.
 */
class PebbleDraftTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun detail(
        description: String? = "Finally.",
        positiveness: Int = 1,
        intensity: Int = 3,
        visibility: String = "private",
        glyphId: String? = null,
        domains: String = """[{ "domain": { "id": "domain-1", "slug": "zoe", "name": "Work" } }]""",
        souls: String = "[]",
        collections: String = "[]",
    ): PebbleDetail =
        json.decodeFromString(
            """
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "name": "Shipped",
              "description": ${description?.let { "\"$it\"" } ?: "null"},
              "happened_at": "2026-04-14T15:42:00Z",
              "intensity": $intensity,
              "positiveness": $positiveness,
              "visibility": "$visibility",
              "glyph_id": ${glyphId?.let { "\"$it\"" } ?: "null"},
              "emotion": { "id": "emotion-1", "slug": "joy", "name": "Joy" },
              "pebble_domains": $domains,
              "pebble_souls": $souls,
              "collection_pebbles": $collections
            }
            """.trimIndent(),
        )

    @Test
    fun `a fresh draft is invalid`() {
        assertFalse(PebbleDraft().isValid)
    }

    @Test
    fun `a draft with name, emotion, domain and valence is valid`() {
        val draft =
            PebbleDraft(
                name = "Test",
                emotionId = "emotion-1",
                domainId = "domain-1",
                valence = Valence.HIGHLIGHT_LARGE,
            )
        assertTrue(draft.isValid)
    }

    @Test
    fun `each missing mandatory field invalidates the draft`() {
        val base =
            PebbleDraft(
                name = "Test",
                emotionId = "emotion-1",
                domainId = "domain-1",
                valence = Valence.HIGHLIGHT_LARGE,
            )
        assertFalse(base.copy(name = "   ").isValid)
        assertFalse(base.copy(emotionId = null).isValid)
        assertFalse(base.copy(domainId = null).isValid)
        assertFalse(base.copy(valence = null).isValid)
    }

    @Test
    fun `optional fields do not affect validity`() {
        val minimal =
            PebbleDraft(
                name = "Test",
                emotionId = "emotion-1",
                domainId = "domain-1",
                valence = Valence.NEUTRAL_SMALL,
            )
        assertTrue(minimal.isValid)
        assertTrue(minimal.copy(description = "", soulIds = emptyList(), collectionId = null, glyphId = null).isValid)
    }

    @Test
    fun `from detail populates every field of a fully-populated detail`() {
        val source =
            detail(
                description = "Finally.",
                positiveness = 1,
                intensity = 3,
                visibility = "public",
                souls = """[{ "soul": { "id": "soul-1", "name": "Me", "glyph_id": "glyph-9",
                    "glyphs": { "id": "glyph-9", "name": null, "strokes": [], "view_box": "0 0 200 200" } } }]""",
                collections = """[{ "collection": { "id": "collection-1", "name": "Wins" } }]""",
            )

        val draft = PebbleDraft.from(source)

        assertEquals("Shipped", draft.name)
        assertEquals("Finally.", draft.description)
        assertEquals(source.happenedAt, draft.happenedAt)
        assertEquals("emotion-1", draft.emotionId)
        assertEquals("domain-1", draft.domainId)
        assertEquals(listOf("soul-1"), draft.soulIds)
        assertEquals("collection-1", draft.collectionId)
        assertEquals(Valence.HIGHLIGHT_LARGE, draft.valence)
        assertEquals(Visibility.PUBLIC, draft.visibility)
        assertTrue(draft.isValid)
    }

    @Test
    fun `from detail maps a null description to an empty string`() {
        assertEquals("", PebbleDraft.from(detail(description = null)).description)
    }

    @Test
    fun `from detail keeps soulIds empty when the detail has no souls`() {
        assertTrue(PebbleDraft.from(detail(souls = "[]")).soulIds.isEmpty())
    }

    @Test
    fun `from detail preserves every soul in order`() {
        val source =
            detail(
                souls = """[
                    { "soul": { "id": "soul-1", "name": "Heloise", "glyph_id": "g1",
                      "glyphs": { "id": "g1", "name": null, "strokes": [], "view_box": "0 0 200 200" } } },
                    { "soul": { "id": "soul-2", "name": "Ingrid", "glyph_id": "g2",
                      "glyphs": { "id": "g2", "name": null, "strokes": [], "view_box": "0 0 200 200" } } }
                    ]""",
            )
        assertEquals(listOf("soul-1", "soul-2"), PebbleDraft.from(source).soulIds)
    }

    @Test
    fun `from detail leaves collectionId null when there are no collections`() {
        assertNull(PebbleDraft.from(detail(collections = "[]")).collectionId)
    }

    @Test
    fun `from detail leaves domainId null and the draft invalid when domains is empty`() {
        val draft = PebbleDraft.from(detail(domains = "[]"))
        assertNull(draft.domainId)
        assertFalse(draft.isValid)
    }

    @Test
    fun `from detail derives valence from positiveness and intensity`() {
        assertEquals(Valence.LOWLIGHT_MEDIUM, PebbleDraft.from(detail(positiveness = -1, intensity = 2)).valence)
    }

    @Test
    fun `from detail round-trips the glyph id`() {
        assertEquals("glyph-7", PebbleDraft.from(detail(glyphId = "glyph-7")).glyphId)
    }
}
