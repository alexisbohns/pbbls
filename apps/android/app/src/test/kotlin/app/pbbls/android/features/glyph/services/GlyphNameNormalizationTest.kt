package app.pbbls.android.features.glyph.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [GlyphService.normalizedName] — the iOS trim/empty→null rule shared by the
 * carve insert and rename (M43 D8: an empty rename CLEARS the name).
 */
class GlyphNameNormalizationTest {
    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("Wave", GlyphService.normalizedName("  Wave  "))
    }

    @Test
    fun `empty and whitespace-only become null`() {
        assertNull(GlyphService.normalizedName(""))
        assertNull(GlyphService.normalizedName("   "))
        assertNull(GlyphService.normalizedName(null))
    }

    @Test
    fun `interior whitespace is preserved`() {
        assertEquals("Two waves", GlyphService.normalizedName("Two waves"))
    }
}
