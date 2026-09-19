package app.pbbls.android.features.glyph.services

import app.pbbls.android.R
import app.pbbls.android.services.DataError
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [glyphMarketErrorMessage] over the decoded [DataError] (#850).
 *
 * **This test used to feed decorated strings** — `"ERROR: insufficient_karma
 * (SQLSTATE P0001)"`, and `"Already_Owned"` for case-insensitivity — because
 * the mapper substring-scanned the exception text. Neither shape is real:
 * that decoration is psql's rendering, and PostgREST's HTTP body carries the
 * condition alone, exactly as `raise exception 'insufficient_karma'` emitted
 * it (`PostgrestImpl.parseErrorResponse` passes `body.message` straight to
 * `RestException.error`). The mapper now matches that value exactly, so the
 * fixtures are the values the server actually sends.
 */
class GlyphMarketErrorMessageTest {
    private fun conflict(code: String) = glyphMarketErrorMessage(DataError.Conflict(code))

    @Test
    fun `each buy_glyph condition maps to its copy`() {
        assertEquals(R.string.glyph_error_insufficient_karma, conflict("insufficient_karma"))
        assertEquals(R.string.glyph_error_not_in_market, conflict("not_in_market"))
        assertEquals(R.string.glyph_error_already_owned, conflict("already_owned"))
        assertEquals(R.string.glyph_error_cannot_buy_own, conflict("cannot_buy_own"))
    }

    @Test
    fun `an unrecognised condition falls back to the generic copy`() {
        assertEquals(R.string.glyph_error_generic, conflict("some_new_condition"))
    }

    /**
     * The regression the exact match buys: a condition name appearing anywhere
     * *other* than as the server's answer must not be mistaken for it. Under the
     * old substring scan the request URL and headers were part of the searched
     * text.
     */
    @Test
    fun `a condition name embedded in other text is not a match`() {
        assertEquals(R.string.glyph_error_generic, conflict("buy_glyph failed: insufficient_karma"))
    }

    @Test
    fun `offline gets its own copy rather than the generic one`() {
        assertEquals(R.string.error_offline, glyphMarketErrorMessage(DataError.Network))
    }

    @Test
    fun `every non-conflict case has copy`() {
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage(DataError.Unauthorized))
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage(DataError.NotFound))
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage(DataError.Quota))
        assertEquals(R.string.glyph_error_generic, glyphMarketErrorMessage(DataError.Unknown(null)))
    }
}
