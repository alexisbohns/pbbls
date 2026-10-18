package app.pbbls.android.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class PathMomentDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val rows =
        """
        [
          {
            "id": "7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70",
            "name": "Swim at dawn",
            "happened_at": "2026-10-12T06:40:00+00:00",
            "created_at": "2026-10-12T07:02:13.481516+00:00",
            "first_snap_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
            "pebble": {
              "id": "c2b1a090-8f7e-4d6c-b5a4-938271605f4e",
              "intensity": 3,
              "positiveness": 1,
              "visibility": "private",
              "emotion": {"id": "e1000000-0000-4000-8000-000000000001", "slug": "joy", "name": "Joy"},
              "glyph": {
                "id": "91c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e",
                "name": "Wave",
                "strokes": [{"d": "M 20 100 Q 100 20 180 100", "width": 6}, {"d": "M 50 150 L 150 150", "width": 6.5}],
                "view_box": "0 0 200 200",
                "source": "collection"
              }
            }
          },
          {
            "id": "0b9e8d7c-6b5a-4f3e-9d2c-1b0a9f8e7d6c",
            "name": null,
            "happened_at": "2026-10-11T18:05:27.5+00:00",
            "created_at": "2026-10-11T18:06:00+00:00",
            "first_snap_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555",
            "pebble": null
          },
          {
            "id": "5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d",
            "name": "Coffee with Ana",
            "happened_at": "2026-10-10T09:00:00Z",
            "created_at": "2026-10-10T09:15:00+00:00",
            "first_snap_path": null,
            "pebble": {
              "id": "d4c3b2a1-0f9e-4d8c-b7a6-5f4e3d2c1b0a",
              "intensity": 1,
              "positiveness": -1,
              "visibility": "secret",
              "emotion": null,
              "glyph": null
            }
          }
        ]
        """.trimIndent()

    private val decoded by lazy { json.decodeFromString<List<PathMoment>>(rows) }

    @Test
    fun `a completed moment carries its pebble and derived glyph`() {
        val moment = decoded[0]
        assertTrue(moment.isCompleted)
        assertEquals("Swim at dawn", moment.name)
        assertEquals(Valence.HIGHLIGHT_LARGE, moment.valence)
        assertEquals(Visibility.PRIVATE, moment.pebble?.visibility)
        assertEquals("joy", moment.pebble?.emotion?.slug)
        val glyph = moment.pebble?.glyph!!
        assertEquals("0 0 200 200", glyph.viewBox)
        assertEquals(2, glyph.strokes.size)
        assertEquals(6.5, glyph.strokes[1].width, 0.0)
        assertEquals("collection", glyph.source)
    }

    @Test
    fun `a photo-only moment has a null name and no pebble`() {
        val moment = decoded[1]
        assertNull(moment.name)
        assertNull(moment.pebble)
        assertFalse(moment.isCompleted)
        assertEquals(Valence.NEUTRAL_MEDIUM, moment.valence)
        assertEquals("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555", moment.firstSnapPath)
        assertEquals(OffsetDateTime.parse("2026-10-11T18:05:27.5Z").toInstant(), moment.happenedAt.toInstant())
    }

    @Test
    fun `a pebble may come without emotion or glyph`() {
        val pebble = decoded[2].pebble!!
        assertNull(pebble.emotion)
        assertNull(pebble.glyph)
        assertEquals(Valence.LOWLIGHT_SMALL, pebble.valence)
        assertNull(decoded[2].firstSnapPath)
    }

    @Test
    fun `absent optional keys decode like explicit nulls`() {
        val minimal = """{"id":"x","happened_at":"2026-10-12T06:40:00Z","created_at":"2026-10-12T06:40:00Z"}"""
        val moment = json.decodeFromString<PathMoment>(minimal)
        assertNull(moment.name)
        assertNull(moment.firstSnapPath)
        assertNull(moment.pebble)
    }

    // Rows in the exact shape path_moments() returns, with and without the #1121 souls key.
    private val withSouls =
        """
        {"id":"m1","name":"Flag march","happened_at":"2026-10-05T08:15:00+00:00","created_at":"2026-10-05T08:16:02.123456+00:00",
         "first_snap_path":"u1/s1","pebble":{"id":"p1","intensity":3,"positiveness":1,"visibility":"secret",
         "emotion":{"id":"e1","slug":"pride","name":"Pride"},"glyph":null,
         "souls":[{"id":"s-elio","name":"Elio","glyph_id":"g2"},{"id":"s-mara","name":"Mara","glyph_id":"g1"}]}}
        """.trimIndent()

    private val beforeSouls =
        """
        {"id":"m2","name":null,"happened_at":"2026-10-05T08:15:00Z","created_at":"2026-10-05T08:16:02Z",
         "first_snap_path":null,"pebble":{"id":"p2","intensity":1,"positiveness":0,"visibility":"secret","emotion":null,"glyph":null}}
        """.trimIndent()

    @Test
    fun `decodes souls in server order`() {
        val m = json.decodeFromString<PathMoment>(withSouls)
        assertEquals(listOf("Elio", "Mara"), m.pebble!!.souls.map { it.name })
        assertEquals("g1", m.pebble!!.souls[1].glyphId)
    }

    @Test
    fun `a row from before the key decodes with no souls`() {
        val m = json.decodeFromString<PathMoment>(beforeSouls)
        assertTrue(m.pebble!!.souls.isEmpty())
        assertNull(m.name)
    }
}
