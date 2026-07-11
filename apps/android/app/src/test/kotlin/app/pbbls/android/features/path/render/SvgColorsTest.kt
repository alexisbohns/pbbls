package app.pbbls.android.features.path.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgColorsTest {
    // A trimmed-down composed pebble: three layers, one currentColor stroke
    // each — the multi-occurrence case the compositor always produces.
    private val composedSvg =
        """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
          <g id="layer:shape">
            <path id="shape:stroke-0" d="M 10 10 L 230 10" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:fossil" opacity="0.3">
            <path id="fossil:stroke-0" d="M 50 50 L 190 190" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:glyph" transform="translate(40, 40) scale(0.8)">
            <path id="glyph:stroke-0" d="M 0 0 L 200 200" fill="none" stroke="currentColor"/>
          </g>
        </svg>
        """.trimIndent()

    @Test
    fun `injectStrokeColor replaces every currentColor occurrence`() {
        val result = SvgColors.injectStrokeColor(composedSvg, "#7B5E99")

        assertFalse(result.contains("currentColor"))
        assertEquals(3, Regex("#7B5E99").findAll(result).count())
    }

    @Test
    fun `injectStrokeColor leaves markup without currentColor untouched`() {
        val svg = """<svg><path stroke="#000000"/></svg>"""

        assertEquals(svg, SvgColors.injectStrokeColor(svg, "#7B5E99"))
    }

    @Test
    fun `injectOutlineFill replaces the sentinel fill`() {
        val outline = """<svg><path fill="none"/><path fill="#FF00FF"/></svg>"""

        val result = SvgColors.injectOutlineFill(outline, "#7B5E99")

        assertFalse(result.contains(SvgColors.OUTLINE_FILL_SENTINEL))
        assertTrue(result.contains("""fill="#7B5E99""""))
        // The unrelated fill="none" survives.
        assertTrue(result.contains("""fill="none""""))
    }

    @Test
    fun `substitutions compose - stroke and fill land independently`() {
        val svg = """<svg><path stroke="currentColor" fill="#FF00FF"/></svg>"""

        val result =
            SvgColors.injectOutlineFill(
                SvgColors.injectStrokeColor(svg, "#AE91CC"),
                "#F2EFF5",
            )

        assertEquals("""<svg><path stroke="#AE91CC" fill="#F2EFF5"/></svg>""", result)
    }
}
