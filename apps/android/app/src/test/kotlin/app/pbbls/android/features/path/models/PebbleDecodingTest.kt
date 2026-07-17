package app.pbbls.android.features.path.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Decodes realistic `path_pebbles()` JSON — nested `emotion` object, absent
 * and null optionals, and the timestamptz offset variants PostgREST can emit.
 */
class PebbleDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes a full row with nested emotion`() {
        val row =
            """
            {
              "id": "0e8b5a52-2f6e-4e2b-9d3e-111111111111",
              "name": "Morning walk",
              "happened_at": "2026-07-08T14:23:45.123456+00:00",
              "created_at": "2026-07-08T15:00:00+00:00",
              "intensity": 2,
              "positiveness": 1,
              "render_svg": "<svg viewBox=\"0 0 260 260\"></svg>",
              "emotion": {
                "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "slug": "joyful",
                "name": "Joyful"
              },
              "first_snap_path": "user-id/snap-id"
            }
            """.trimIndent()

        val pebble = json.decodeFromString<Pebble>(row)

        assertEquals("Morning walk", pebble.name)
        assertEquals(2, pebble.intensity)
        assertEquals(1, pebble.positiveness)
        assertEquals(Valence.HIGHLIGHT_MEDIUM, pebble.valence)
        assertEquals("joyful", pebble.emotion?.slug)
        assertEquals("user-id/snap-id", pebble.firstSnapPath)
        assertEquals(
            OffsetDateTime.parse("2026-07-08T14:23:45.123456+00:00").toInstant(),
            pebble.happenedAt.toInstant(),
        )
    }

    @Test
    fun `decodes null and absent optionals`() {
        // render_svg explicitly null, emotion/first_snap_path absent.
        val row =
            """
            {
              "id": "0e8b5a52-2f6e-4e2b-9d3e-222222222222",
              "name": "Quiet evening",
              "happened_at": "2026-07-08T20:00:00+00:00",
              "created_at": "2026-07-08T20:05:00+00:00",
              "intensity": 1,
              "positiveness": 0,
              "render_svg": null
            }
            """.trimIndent()

        val pebble = json.decodeFromString<Pebble>(row)

        assertNull(pebble.renderSvg)
        assertNull(pebble.emotion)
        assertNull(pebble.firstSnapPath)
        assertEquals(Valence.NEUTRAL_SMALL, pebble.valence)
    }

    @Test
    fun `timestamp serializer accepts offset variants`() {
        // +00:00 (the PostgREST default), a non-UTC offset, Z, no fraction.
        for (stamp in listOf(
            "2026-07-08T14:23:45.123456+00:00",
            "2026-07-08T14:23:45+02:00",
            "2026-07-08T14:23:45Z",
            "2026-12-31T23:59:59+00:00",
        )) {
            val row =
                """
                {
                  "id": "x", "name": "x",
                  "happened_at": "$stamp", "created_at": "$stamp",
                  "intensity": 2, "positiveness": 0
                }
                """.trimIndent()
            val pebble = json.decodeFromString<Pebble>(row)
            assertEquals(OffsetDateTime.parse(stamp), pebble.happenedAt)
        }
    }

    @Test
    fun `emotion palette view row maps only when complete and parseable`() {
        val goodRow =
            json.decodeFromString<EmotionWithPaletteRow>(
                """
                {
                  "id": "e1", "slug": "joyful", "name": "Joyful", "emoji": "😊",
                  "category_id": "c1", "category_slug": "joy", "category_name": "Joy",
                  "primary_color": "#7B5E99FF", "secondary_color": "#AE91CCFF",
                  "light_color": "#F2EFF5FF", "surface_color": "#7B5E991A",
                  "dark_color": "#2A2138FF", "shaded_color": "#4A3A5CFF"
                }
                """.trimIndent(),
            )
        val mapped = goodRow.toEmotionWithPalette()
        assertEquals("joyful", mapped?.slug)
        assertEquals("#7B5E99FF", mapped?.palette?.primaryHex)
        assertEquals("#4A3A5CFF", mapped?.palette?.shadedHex)

        // A null column (view types are all-nullable) drops the row.
        assertNull(goodRow.copy(name = null).toEmotionWithPalette())
        // The dark_color slot (#599) is required like the rest of the palette.
        assertNull(goodRow.copy(darkColor = null).toEmotionWithPalette())
        // The shaded_color slot (#605) is required like the rest of the palette.
        assertNull(goodRow.copy(shadedColor = null).toEmotionWithPalette())
        // An unparseable hex drops the row.
        assertNull(goodRow.copy(primaryColor = "oops").toEmotionWithPalette())
    }
}
