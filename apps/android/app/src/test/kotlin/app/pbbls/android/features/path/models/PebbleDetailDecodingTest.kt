package app.pbbls.android.features.path.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes the direct-select `PebbleDetail` shape (D7): the nested `emotion` and
 * `glyph` embeds, the junction-table wrappers (`pebble_domains` / `pebble_souls`
 * / `collection_pebbles`) flattened by the accessors, the joined soul glyph, the
 * `sort_order`-sorted snaps, the derived valence, and the render columns present
 * / absent. Also confirms [PebbleDraft.from] prefills an edit draft from a detail.
 */
class PebbleDetailDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(raw: String): PebbleDetail = json.decodeFromString(raw)

    /** Full embedded-select fixture with one of each relation; snaps out of order. */
    private fun fullRow(): PebbleDetail =
        decode(
            """
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "name": "Shipped the thing",
              "description": "Finally.",
              "happened_at": "2026-04-14T15:42:00Z",
              "intensity": 2,
              "positiveness": 1,
              "visibility": "private",
              "render_svg": "<svg/>",
              "render_version": "0.1.0",
              "glyph_id": "g1",
              "glyph": { "id": "g1", "name": "wave", "strokes": [], "view_box": "0 0 200 200" },
              "emotion": { "id": "e1", "slug": "joy", "name": "Joy", "color": "#FFD166" },
              "pebble_domains": [
                { "domain": { "id": "d1", "slug": "zoe", "name": "Work" } }
              ],
              "pebble_souls": [
                {
                  "soul": {
                    "id": "s1", "name": "Alex", "glyph_id": "sg1",
                    "glyphs": { "id": "sg1", "name": null, "strokes": [], "view_box": "0 0 200 200" }
                  }
                }
              ],
              "collection_pebbles": [
                { "collection": { "id": "c1", "name": "Wins" } }
              ],
              "snaps": [
                { "id": "snap-b", "storage_path": "user/b", "sort_order": 2 },
                { "id": "snap-a", "storage_path": "user/a", "sort_order": 0 },
                { "id": "snap-c", "storage_path": "user/c", "sort_order": 1 }
              ]
            }
            """.trimIndent(),
        )

    @Test
    fun `decodes and flattens the junction wrappers`() {
        val detail = fullRow()

        assertEquals("Shipped the thing", detail.name)
        assertEquals("Finally.", detail.description)
        assertEquals(Visibility.PRIVATE, detail.visibility)
        assertEquals("joy", detail.emotion.slug)
        assertEquals("g1", detail.glyph?.id)
        assertEquals(listOf("zoe"), detail.domains.map { it.slug })
        assertEquals(listOf("Alex"), detail.souls.map { it.name })
        assertEquals(listOf("Wins"), detail.collections.map { it.name })
        assertEquals(Valence.HIGHLIGHT_MEDIUM, detail.valence)
        // Snaps come back sorted ascending by sort_order.
        assertEquals(listOf("snap-a", "snap-c", "snap-b"), detail.sortedSnaps.map { it.id })
    }

    @Test
    fun `a soul embed carries its joined glyph strokes`() {
        val detail =
            decode(
                """
                {
                  "id": "p1", "name": "Test pebble", "description": null,
                  "happened_at": "2026-04-28T10:00:00Z",
                  "intensity": 2, "positiveness": 1, "visibility": "private",
                  "emotion": { "id": "e1", "slug": "joy", "name": "Joy" },
                  "pebble_souls": [
                    {
                      "soul": {
                        "id": "s1", "name": "Alex", "glyph_id": "g1",
                        "glyphs": {
                          "id": "g1", "name": null,
                          "strokes": [{ "d": "M0,0 L10,10", "width": 6.0 }],
                          "view_box": "0 0 200 200"
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )

        val soul = detail.souls.single()
        assertEquals("g1", soul.glyph.id)
        assertEquals(1, soul.glyph.strokes.size)
        assertEquals("M0,0 L10,10", soul.glyph.strokes.first().d)
    }

    @Test
    fun `empty and absent join arrays decode as empty`() {
        val detail =
            decode(
                """
                {
                  "id": "p1", "name": "Quiet moment", "description": null,
                  "happened_at": "2026-04-14T08:00:00Z",
                  "intensity": 1, "positiveness": 0, "visibility": "private",
                  "emotion": { "id": "e1", "slug": "serenity", "name": "Calm" }
                }
                """.trimIndent(),
            )

        assertNull(detail.description)
        assertNull(detail.renderSvg)
        assertNull(detail.renderVersion)
        assertNull(detail.glyph)
        assertTrue(detail.domains.isEmpty())
        assertTrue(detail.souls.isEmpty())
        assertTrue(detail.collections.isEmpty())
        assertTrue(detail.sortedSnaps.isEmpty())
        assertEquals(Valence.NEUTRAL_SMALL, detail.valence)
    }

    @Test
    fun `multiple domains flatten in order`() {
        val detail =
            decode(
                """
                {
                  "id": "p1", "name": "Cross-domain moment", "description": null,
                  "happened_at": "2026-04-14T08:00:00Z",
                  "intensity": 2, "positiveness": -1, "visibility": "public",
                  "emotion": { "id": "e1", "slug": "sadness", "name": "Sad" },
                  "pebble_domains": [
                    { "domain": { "id": "d1", "slug": "zoe", "name": "Work" } },
                    { "domain": { "id": "d2", "slug": "asphaleia", "name": "Health" } }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(listOf("Work", "Health"), detail.domains.map { it.name })
        assertEquals(Visibility.PUBLIC, detail.visibility)
        assertEquals(Valence.LOWLIGHT_MEDIUM, detail.valence)
    }

    @Test
    fun `PebbleDraft prefills from the decoded detail`() {
        val detail = fullRow()
        val draft = PebbleDraft.from(detail)

        assertEquals(detail.name, draft.name)
        assertEquals(detail.emotion.id, draft.emotionId)
        assertEquals("d1", draft.domainId)
        assertEquals("c1", draft.collectionId)
        assertEquals(Valence.HIGHLIGHT_MEDIUM, draft.valence)
        assertEquals(1, draft.soulIds.size)
        assertTrue(draft.isValid)
    }
}
