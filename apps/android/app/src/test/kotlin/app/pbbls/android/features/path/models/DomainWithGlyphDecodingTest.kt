package app.pbbls.android.features.path.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mirrors iOS `DomainWithGlyphDecodingTests`. `ReferenceDataService` reads
 * `v_domains_with_glyph` (M58 D6), which flattens each domain's default glyph
 * onto the row — so [Domain] has to decode a row with the glyph, a row whose
 * `default_glyph_id` is null (the left join yields nulls), and the older
 * glyph-free projection.
 */
class DomainWithGlyphDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesARowCarryingItsDefaultGlyph() {
        val domain =
            json.decodeFromString<Domain>(
                """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "slug": "health",
                  "name": "Health",
                  "label": "Your body, energy, and physical well-being",
                  "strokes": [{"d": "M40 40 L 160 160", "width": 6.0}],
                  "view_box": "0 0 200 200"
                }
                """.trimIndent(),
            )
        assertEquals("health", domain.slug)
        assertEquals(1, domain.strokes?.size)
        assertEquals("M40 40 L 160 160", domain.strokes?.first()?.d)
        assertEquals("0 0 200 200", domain.viewBox)
    }

    /** The view left-joins `glyphs`, so a domain with no default glyph yields nulls. */
    @Test
    fun decodesARowWithNullGlyphColumns() {
        val domain =
            json.decodeFromString<Domain>(
                """
                {
                  "id": "22222222-2222-2222-2222-222222222222",
                  "slug": "work",
                  "name": "Work",
                  "label": "Your job, career, and professional life",
                  "strokes": null,
                  "view_box": null
                }
                """.trimIndent(),
            )
        assertNull(domain.strokes)
        assertNull(domain.viewBox)
    }

    /** Absent keys must decode too — the form's menu picker selects neither column. */
    @Test
    fun decodesARowWithTheGlyphColumnsAbsentEntirely() {
        val domain =
            json.decodeFromString<Domain>(
                """
                {
                  "id": "33333333-3333-3333-3333-333333333333",
                  "slug": "travel",
                  "name": "Travel",
                  "label": "Exploring new places and horizons"
                }
                """.trimIndent(),
            )
        assertNull(domain.strokes)
        assertNull(domain.viewBox)
        assertEquals("Travel", domain.name)
    }

    /** The view also selects `default_glyph_id`, which no client models. */
    @Test
    fun ignoresColumnsTheModelDoesNotCarry() {
        val domain =
            json.decodeFromString<Domain>(
                """
                {
                  "id": "44444444-4444-4444-4444-444444444444",
                  "slug": "money",
                  "name": "Finance",
                  "label": "Money, stability, and material security",
                  "default_glyph_id": "55555555-5555-5555-5555-555555555555"
                }
                """.trimIndent(),
            )
        assertEquals("money", domain.slug)
    }
}
