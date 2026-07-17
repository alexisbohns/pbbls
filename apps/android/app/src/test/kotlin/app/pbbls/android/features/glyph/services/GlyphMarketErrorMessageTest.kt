package app.pbbls.android.features.glyph.services

import app.pbbls.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [glyphMarketErrorMessage] — the iOS `friendlyMessage` substring table, in
 * order, on the lowercased message (M43 D4). The raw Postgres error text must
 * reach this mapper intact.
 */
class GlyphMarketErrorMessageTest {
    @Test
    fun `each buy_glyph error maps to its copy`() {
        assertEquals(
            R.string.glyph_error_insufficient_karma,
            glyphMarketErrorMessage("ERROR: insufficient_karma (SQLSTATE P0001)"),
        )
        assertEquals(R.string.glyph_error_not_in_market, glyphMarketErrorMessage("not_in_market"))
        assertEquals(R.string.glyph_error_already_owned, glyphMarketErrorMessage("Already_Owned"))
        assertEquals(R.string.glyph_error_cannot_buy_own, glyphMarketErrorMessage("cannot_buy_own"))
    }

    @Test
    fun `unknown and null messages fall back to the generic copy`() {
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage("connection reset"))
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage(null))
    }

    @Test
    fun `insufficient_karma wins when multiple markers appear`() {
        // iOS checks in a fixed order; the spend_karma marker is first.
        assertEquals(
            R.string.glyph_error_insufficient_karma,
            glyphMarketErrorMessage("buy_glyph failed: insufficient_karma via not_in_market path"),
        )
    }
}
