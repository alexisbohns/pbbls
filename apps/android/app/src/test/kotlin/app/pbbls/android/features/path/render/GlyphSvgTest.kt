package app.pbbls.android.features.path.render

import app.pbbls.android.features.glyph.models.GlyphStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the glyph -> [PebbleSvg] contract for [buildGlyphSvg]: the emitted
 * markup carries the view box, each stroke's path data, a `currentColor`
 * placeholder stroke, and the locale-free `Double` width; and a round-trip
 * through [SvgColors.injectStrokeColor] substitutes the palette hex with no
 * `currentColor` left behind. Pure JVM — no Android runtime.
 */
class GlyphSvgTest {
    private val strokes =
        listOf(
            GlyphStroke(d = "M0,0 L10,10", width = 6.0),
            GlyphStroke(d = "M20,20 Q40,0 60,20", width = 3.5),
        )

    @Test
    fun `buildGlyphSvg emits the view box, both strokes, and the currentColor placeholder`() {
        val markup = buildGlyphSvg(strokes, "0 0 200 200")

        assertTrue(markup, markup.contains("viewBox=\"0 0 200 200\""))
        assertTrue(markup, markup.contains("d=\"M0,0 L10,10\""))
        assertTrue(markup, markup.contains("d=\"M20,20 Q40,0 60,20\""))
        assertTrue(markup, markup.contains("stroke=\"currentColor\""))
        // Double.toString keeps the width locale-free ("6.0", never "6,0").
        assertTrue(markup, markup.contains("stroke-width=\"6.0\""))
        assertTrue(markup, markup.contains("stroke-width=\"3.5\""))
        // One <path> element per stroke.
        assertEquals(2, Regex("<path").findAll(markup).count())
    }

    @Test
    fun `injectStrokeColor swaps every placeholder for the palette hex`() {
        val colored = SvgColors.injectStrokeColor(buildGlyphSvg(strokes, "0 0 200 200"), "#123456")

        assertTrue(colored, colored.contains("stroke=\"#123456\""))
        assertFalse(colored, colored.contains("currentColor"))
    }
}
