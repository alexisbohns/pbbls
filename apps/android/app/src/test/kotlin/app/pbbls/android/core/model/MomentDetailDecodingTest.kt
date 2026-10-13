package app.pbbls.android.core.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentDetailDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val ownCompleted =
        """
        {
          "id": "7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70",
          "user_id": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f",
          "name": "Swim at dawn",
          "description": "Cold, then not.",
          "happened_at": "2026-10-12T06:40:00+00:00",
          "hidden_at": null,
          "created_at": "2026-10-12T07:02:13.481516+00:00",
          "updated_at": "2026-10-12T07:02:13.481516+00:00",
          "snaps": [
            {"id": "aa000000-0000-4000-8000-000000000002", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/aa000000-0000-4000-8000-000000000002", "sort_order": 1},
            {"id": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "sort_order": 0}
          ],
          "pebble": {
            "id": "c2b1a090-8f7e-4d6c-b5a4-938271605f4e",
            "emotion_id": "e1000000-0000-4000-8000-000000000001",
            "intensity": 3,
            "positiveness": 1,
            "visibility": "public",
            "created_at": "2026-10-12T07:02:13.481516+00:00",
            "updated_at": "2026-10-12T07:02:13.481516+00:00",
            "emotion": {"id": "e1000000-0000-4000-8000-000000000001", "slug": "joy", "name": "Joy", "color": "#F6C445FF"},
            "glyph": {"id": "b2000000-0000-4000-8000-000000000003", "name": null, "strokes": [{"d": "M 10 10 L 190 190", "width": 6}], "view_box": "0 0 200 200", "source": "domain"},
            "cards": [],
            "souls": [{"id": "50000000-0000-4000-8000-000000000004", "name": "Ana"}],
            "domains": [{"id": "d0000000-0000-4000-8000-000000000005", "slug": "health", "name": "Health", "label": "Body, sleep, movement"}],
            "collections": [{"id": "c0000000-0000-4000-8000-000000000006", "name": "Sea days", "glyph_id": "91c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"}]
          }
        }
        """.trimIndent()

    private val ownPhotoOnly =
        """
        {
          "id": "0b9e8d7c-6b5a-4f3e-9d2c-1b0a9f8e7d6c",
          "user_id": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f",
          "name": null,
          "description": null,
          "happened_at": "2026-10-11T18:05:27+00:00",
          "hidden_at": null,
          "created_at": "2026-10-11T18:06:00.000001+00:00",
          "updated_at": "2026-10-11T18:06:00.000001+00:00",
          "snaps": [{"id": "11111111-2222-4333-8444-555555555555", "storage_path": "3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/11111111-2222-4333-8444-555555555555", "sort_order": 0}],
          "pebble": null
        }
        """.trimIndent()

    private val connectionsMoment =
        """
        {
          "id": "f0000000-0000-4000-8000-000000000007",
          "user_id": "99999999-0000-4000-8000-000000000008",
          "name": "Their trip",
          "description": null,
          "happened_at": "2026-10-09T12:00:00+00:00",
          "hidden_at": null,
          "created_at": "2026-10-09T12:30:00+00:00",
          "updated_at": "2026-10-09T12:30:00+00:00",
          "snaps": [],
          "pebble": null
        }
        """.trimIndent()

    @Test
    fun `an own completed moment decodes every nested relation`() {
        val moment = json.decodeFromString<MomentDetail>(ownCompleted)
        val pebble = moment.pebble!!
        assertEquals("Cold, then not.", moment.description)
        assertEquals(Visibility.PUBLIC, pebble.visibility)
        assertEquals("joy", pebble.emotion.slug)
        assertEquals(listOf("Ana"), pebble.souls.map { it.name })
        assertEquals(listOf("health"), pebble.domains.map { it.slug })
        assertEquals(listOf("Sea days"), pebble.collections.map { it.name })
        assertEquals("domain", pebble.glyph?.source)
        assertNull(pebble.glyph?.name)
        assertEquals(Valence.HIGHLIGHT_LARGE, pebble.valence)
    }

    @Test
    fun `snaps sort by sort_order and the first one is the path photo`() {
        val moment = json.decodeFromString<MomentDetail>(ownCompleted)
        assertEquals(listOf(0, 1), moment.sortedSnaps.map { it.sortOrder })
        assertEquals("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", moment.toPathMoment().firstSnapPath)
    }

    @Test
    fun `an own photo-only moment has explicit nulls and no pebble`() {
        val moment = json.decodeFromString<MomentDetail>(ownPhotoOnly)
        assertNull(moment.name)
        assertNull(moment.description)
        assertNull(moment.pebble)
        assertEquals(1, moment.snaps.size)
    }

    @Test
    fun `a connection's moment has no snaps and no pebble of yours`() {
        val moment = json.decodeFromString<MomentDetail>(connectionsMoment)
        assertTrue(moment.snaps.isEmpty())
        assertNull(moment.pebble)
    }

    @Test
    fun `toPathMoment keeps what a row needs`() {
        val row = json.decodeFromString<MomentDetail>(ownCompleted).toPathMoment()
        assertEquals("7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70", row.id)
        assertEquals("c2b1a090-8f7e-4d6c-b5a4-938271605f4e", row.pebble?.id)
        assertEquals("joy", row.pebble?.emotion?.slug)
        assertEquals("b2000000-0000-4000-8000-000000000003", row.pebble?.glyph?.id)
    }
}
