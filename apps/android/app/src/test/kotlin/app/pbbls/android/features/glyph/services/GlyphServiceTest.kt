package app.pbbls.android.features.glyph.services

import app.pbbls.android.features.glyph.models.Glyph
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GlyphService.withEntitled] merge contract (#562): own+system glyphs keep
 * their server order, entitled glyphs append after them, and duplicates are
 * dropped defensively.
 */
class GlyphServiceTest {
    private fun glyph(
        id: String,
        userId: String? = null,
    ) = Glyph(id = id, strokes = emptyList(), viewBox = "0 0 200 200", userId = userId)

    @Test
    fun `appends entitled glyphs after own and system`() {
        val base = listOf(glyph("own", userId = "me"), glyph("system"))
        val entitled = listOf(glyph("bought", userId = "creator"))
        assertEquals(
            listOf("own", "system", "bought"),
            GlyphService.withEntitled(base, entitled).map { it.id },
        )
    }

    @Test
    fun `drops entitled glyphs already present and de-dupes within entitled`() {
        val base = listOf(glyph("a"))
        val entitled = listOf(glyph("a"), glyph("b"), glyph("b"))
        assertEquals(
            listOf("a", "b"),
            GlyphService.withEntitled(base, entitled).map { it.id },
        )
    }

    @Test
    fun `empty entitled list leaves the base untouched`() {
        val base = listOf(glyph("a"), glyph("b"))
        assertEquals(base, GlyphService.withEntitled(base, emptyList()))
    }
}
