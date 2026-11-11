package app.pbbls.android.features.home.guides

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import app.pbbls.android.core.model.home.GuideIllustration
import org.junit.Assert.assertEquals
import org.junit.Test

/** The About rows' chip icons (#1383 D13): one per illustration, and the pebble for none. */
class GuideIconTest {
    @Test
    fun `each illustration has its chip icon`() {
        val expected =
            mapOf(
                GuideIllustration.PEBBLES to PbblsGlyph.PEBBLE_ROUND,
                GuideIllustration.STONES to PbblsGlyph.STONES_ALIGNED_BOTTOM,
                GuideIllustration.EMOTIONS to PbblsGlyph.STONES_TRACK,
                GuideIllustration.VALENCE to PbblsGlyph.SCALE,
                GuideIllustration.DOMAINS to PbblsGlyph.ABSTRACT_LANDSCAPE,
                GuideIllustration.COLLECTIONS to PbblsGlyph.COLLECTION,
                GuideIllustration.SOULS to PbblsGlyph.PEOPLE,
                GuideIllustration.GLYPHS to PbblsGlyph.PEBBLE_GLYPH,
                GuideIllustration.KARMA to PbblsGlyph.KARMA_DOT,
            )

        assertEquals(GuideIllustration.entries.toSet(), expected.keys)
        expected.forEach { (illustration, icon) -> assertEquals("$illustration", icon, illustration.chipIcon()) }
    }

    @Test
    fun `a guide without an illustration wears the round pebble`() {
        val none: GuideIllustration? = null

        assertEquals(PbblsGlyph.PEBBLE_ROUND, none.chipIcon())
    }

    @Test
    fun `only the glyphs guide draws its chip dotted`() {
        GuideIllustration.entries.forEach { illustration ->
            val expected = if (illustration == GuideIllustration.GLYPHS) GlyphForm.DOTTED else GlyphForm.REGULAR
            assertEquals("$illustration", expected, illustration.chipForm())
        }
        val none: GuideIllustration? = null
        assertEquals(GlyphForm.REGULAR, none.chipForm())
    }
}
