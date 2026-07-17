package app.pbbls.android.features.glyph.models

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Market wire rows — mirrors iOS `GlyphSwapDecodingTests` plus the embedded
 * shapes the three tab queries return (M43 D4).
 */
class GlyphMarketDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `buy_glyph result decodes entitlement id and balance`() {
        val decoded =
            json.decodeFromString<BuyGlyphResult>(
                """{ "entitlement_id": "11111111-1111-1111-1111-111111111111", "balance": 33 }""",
            )
        assertEquals("11111111-1111-1111-1111-111111111111", decoded.entitlementId)
        assertEquals(33, decoded.balance)
    }

    @Test
    fun `mine row derives its price from the first approved and listed submission`() {
        val row =
            json.decodeFromString<MineGlyphRow>(
                """
                { "id": "g1", "name": "Wave", "strokes": [], "view_box": "0 0 200 200",
                  "user_id": "u1", "created_at": "2026-07-16T12:00:00+00:00",
                  "glyph_submissions": [
                    { "price": 5, "status": "pending", "listed": true },
                    { "price": 8, "status": "approved", "listed": false },
                    { "price": 13, "status": "approved", "listed": true }
                  ] }
                """.trimIndent(),
            )
        assertEquals(13, row.listedPrice)
        assertEquals("Wave", row.toGlyph().name)
    }

    @Test
    fun `mine row without submissions prices at zero and decodes absent keys`() {
        val row =
            json.decodeFromString<MineGlyphRow>(
                """{ "id": "g2", "strokes": [], "view_box": "0 0 200 200" }""",
            )
        assertEquals(0, row.listedPrice)
        assertNull(row.userId)
        assertNull(row.createdAt)
    }

    @Test
    fun `owned row nests the glyph under glyphs with both created_at levels`() {
        val row =
            json.decodeFromString<OwnedGlyphRow>(
                """
                { "price_paid": 21, "created_at": "2026-07-16T10:00:00+00:00",
                  "glyphs": { "id": "g3", "strokes": [], "view_box": "0 0 200 200",
                              "user_id": "creator", "created_at": "2026-07-01T09:00:00+00:00" } }
                """.trimIndent(),
            )
        assertEquals(21, row.pricePaid)
        assertEquals("g3", row.glyph.id)
        assertTrue(row.acquiredAt!!.isAfter(row.glyph.createdAt!!))
    }

    @Test
    fun `market row decodes fractional-second timestamps`() {
        val row =
            json.decodeFromString<MarketGlyphRow>(
                """
                { "id": "g4", "user_id": "creator", "strokes": [], "view_box": "0 0 200 200",
                  "created_at": "2026-07-16T12:34:56.1+00:00", "price": 3, "owned": false }
                """.trimIndent(),
            )
        assertEquals(3, row.price)
        assertEquals(2026, row.createdAt!!.year)
    }
}
