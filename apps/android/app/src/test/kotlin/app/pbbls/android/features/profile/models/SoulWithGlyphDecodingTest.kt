package app.pbbls.android.features.profile.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the raw PostgREST [SoulRow] — the joined `glyphs` object plus the
 * `pebbles_count` aggregate PostgREST returns as `[{ "count": N }]` — and checks
 * [SoulRow.toSoulWithGlyph] flattens it into the clean [SoulWithGlyph] domain
 * type, including the aggregate wrapper and its absent-array zero fallback
 * (defensive — every shipped select now asks for the count, #563).
 */
class SoulWithGlyphDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes an embedded glyph and the pebbles_count aggregate`() {
        val row =
            json.decodeFromString<SoulRow>(
                """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "name": "Sam",
                  "glyph_id": "22222222-2222-2222-2222-222222222222",
                  "glyphs": {
                    "id": "22222222-2222-2222-2222-222222222222",
                    "name": "wave",
                    "strokes": [{ "d": "M0,0 L10,10", "width": 6.0 }],
                    "view_box": "0 0 200 200"
                  },
                  "pebbles_count": [{ "count": 12 }]
                }
                """.trimIndent(),
            )

        val soul = row.toSoulWithGlyph()
        assertEquals("11111111-1111-1111-1111-111111111111", soul.id)
        assertEquals("Sam", soul.name)
        assertEquals("22222222-2222-2222-2222-222222222222", soul.glyphId)
        val strokes = soul.glyph.strokes
        assertEquals(soul.glyphId, soul.glyph.id)
        assertEquals("wave", soul.glyph.name)
        assertEquals("0 0 200 200", soul.glyph.viewBox)
        assertEquals(1, strokes.size)
        assertEquals("M0,0 L10,10", strokes.first().d)
        assertEquals(12, soul.pebblesCount)
    }

    @Test
    fun `a system-default glyph decodes with a null name and no strokes`() {
        val row =
            json.decodeFromString<SoulRow>(
                """
                {
                  "id": "s1", "name": "Alex", "glyph_id": "g1",
                  "glyphs": { "id": "g1", "name": null, "strokes": [], "view_box": "0 0 200 200" },
                  "pebbles_count": [{ "count": 0 }]
                }
                """.trimIndent(),
            )

        val soul = row.toSoulWithGlyph()
        assertNull(soul.glyph.name)
        assertTrue(soul.glyph.strokes.isEmpty())
        assertEquals(0, soul.pebblesCount)
    }

    @Test
    fun `an absent pebbles_count aggregate decodes to zero`() {
        // Defensive: a select that omits the aggregate must still decode.
        val row =
            json.decodeFromString<SoulRow>(
                """
                {
                  "id": "s1", "name": "Kai", "glyph_id": "g1",
                  "glyphs": { "id": "g1", "name": null, "strokes": [], "view_box": "0 0 200 200" }
                }
                """.trimIndent(),
            )
        assertEquals(0, row.toSoulWithGlyph().pebblesCount)
    }
}
