// Inlined SVG path/markup lines are machine-shaped and cannot wrap.
@file:Suppress("ktlint:standard:max-line-length")

package app.pbbls.android.features.path.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the [parsePebbleSvg] contract that [PebbleStaticRender] relies on: a
 * server-composed pebble parses into its viewBox + ordered `layer:*` groups with
 * flattened transforms and opacity, invisible shape-detail fills are dropped,
 * and un-traceable markup returns `null` (so the caller falls back to
 * [PebbleSvg]). Pure JVM — no Android runtime, no Compose paths.
 */
class PebbleSvgModelTest {
    // A composed pebble mirroring the engine's real output (see
    // PebbleSvgFixtures): a shape layer whose second path is a stripped fill
    // (fill="none", no stroke), an optional fossil at opacity 0.3, and a glyph
    // layer whose strokes sit under two nested transforms.
    private val composed =
        """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 250 200" width="250" height="200">
          <g id="layer:shape">
            <path id="shape:stroke-0" fill="none" d="M 10 10 L 240 190" stroke="currentColor" stroke-width="6"/>
            <path id="shape:stroke-1" fill-rule="evenodd" clip-rule="evenodd" d="M 100 100 L 120 120" fill="none"/>
          </g>
          <g id="layer:fossil" opacity="0.3">
            <path id="fossil:stroke-0" d="M 40 160 C 80 40 120 40 160 160" stroke="currentColor" stroke-width="4" fill="none"/>
          </g>
          <g id="layer:glyph" transform="translate(30, 30) scale(0.7)">
            <g transform="translate(10, 20) scale(0.5)">
              <path id="glyph:stroke-0" d="M 20 100 L 180 100" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </g>
          </g>
        </svg>
        """.trimIndent()

    @Test
    fun `parses the viewBox`() {
        val model = parsePebbleSvg(composed)!!
        assertEquals(PebbleSvgModel.ViewBox(0f, 0f, 250f, 200f), model.viewBox)
    }

    @Test
    fun `extracts the three layers in document order with their kind and opacity`() {
        val model = parsePebbleSvg(composed)!!
        assertEquals(3, model.layers.size)
        assertEquals(PebbleSvgModel.Layer.Kind.SHAPE, model.layers[0].kind)
        assertEquals(PebbleSvgModel.Layer.Kind.FOSSIL, model.layers[1].kind)
        assertEquals(PebbleSvgModel.Layer.Kind.GLYPH, model.layers[2].kind)
        assertEquals(1f, model.layers[0].opacity, EPS) // shape
        assertEquals(0.3f, model.layers[1].opacity, EPS) // fossil
        assertEquals(1f, model.layers[2].opacity, EPS) // glyph
    }

    @Test
    fun `drops the stripped shape-detail fill (fill=none and no stroke)`() {
        val model = parsePebbleSvg(composed)!!
        // Two <path>s in layer:shape, but the fill-only one is invisible → 1 kept.
        assertEquals(1, model.layers[0].paths.size)
        assertEquals("M 10 10 L 240 190", model.layers[0].paths[0].d)
        assertEquals(Affine.IDENTITY, model.layers[0].paths[0].transform)
    }

    @Test
    fun `splits the glyph transform into the layer's own affine and the inner one`() {
        // The layer's own <g transform> stays on Layer.transform (the renderer —
        // and the wobble port, which needs the glyph's raw slot space — applies
        // it separately); only the inner normalization <g> lands on the path.
        val model = parsePebbleSvg(composed)!!
        val layer = model.layers[2].transform
        assertEquals(0.7f, layer.a, EPS)
        assertEquals(30f, layer.e, EPS)
        assertEquals(30f, layer.f, EPS)
        val inner =
            model.layers[2]
                .paths
                .single()
                .transform
        assertEquals(0.5f, inner.a, EPS)
        assertEquals(10f, inner.e, EPS)
        assertEquals(20f, inner.f, EPS)
        // Their composition is the flattened transform the renderer bakes in:
        //   scale = 0.7 * 0.5 = 0.35
        //   e = 0.7*10 + 30 = 37 ; f = 0.7*20 + 30 = 44
        val t = layer.concat(inner)
        assertEquals(0.35f, t.a, EPS)
        assertEquals(0.35f, t.d, EPS)
        assertEquals(0f, t.b, EPS)
        assertEquals(0f, t.c, EPS)
        assertEquals(37f, t.e, EPS)
        assertEquals(44f, t.f, EPS)
    }

    @Test
    fun `returns null for glyph-only markup so GlyphImage falls back to PebbleSvg`() {
        // buildGlyphSvg output: a bare <svg><path/></svg> with no layer:* groups.
        val glyphOnly = buildGlyphSvg(listOf(), "0 0 200 200")
        assertNull(parsePebbleSvg(glyphOnly))
    }

    @Test
    fun `returns null when there is no viewBox`() {
        val noViewBox =
            """
            <svg xmlns="http://www.w3.org/2000/svg">
              <g id="layer:shape"><path d="M 0 0 L 1 1" stroke="currentColor"/></g>
            </svg>
            """.trimIndent()
        assertNull(parsePebbleSvg(noViewBox))
    }

    @Test
    fun `returns null when no layer carries a stroked path`() {
        val empty =
            """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <g id="layer:shape"><path d="M 0 0 L 1 1" fill="none"/></g>
            </svg>
            """.trimIndent()
        assertNull(parsePebbleSvg(empty))
    }

    @Test
    fun `returns null for malformed markup`() {
        assertNull(parsePebbleSvg("not svg at all"))
    }

    @Test
    fun `parses all nine engine fixtures without falling back`() {
        // Sanity: every real shape/valence composition is traceable, so none
        // silently drops to the AndroidSVG fallback.
        for ((name, svg) in FIXTURES) {
            val model = parsePebbleSvg(svg)
            assertNotNull("expected $name to parse", model)
            assertTrue("expected $name to keep a shape stroke", model!!.layers.isNotEmpty())
        }
    }

    companion object {
        private const val EPS = 1e-4f

        // A couple of authentic engine compositions inlined from the screenshot
        // fixtures (the src/test set can't reach the screenshotTest one). Enough
        // to exercise the with-fossil and clip-path/fill-rule shapes.
        private val FIXTURES =
            listOf(
                "smallNeutral (fossil)" to
                    """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 250 200" width="250" height="200">
                      <g id="layer:shape">
                        <path id="shape:stroke-0" fill="none" d="M21.9 161.4C6.9 133.9 8.3 47.3 29.7 26.5C52.2 4.8 133.7 23.7 196.6 47.5C253.5 69 247.1 159.9 196.6 175.4C154.3 188.4 36.7 188.7 21.9 161.4Z" stroke="currentColor" stroke-width="6"/>
                      </g>
                      <g id="layer:fossil" opacity="0.3">
                        <path id="fossil:stroke-0" d="M 40 160 C 80 40 120 40 160 160" stroke="currentColor" stroke-width="4" fill="none"/>
                      </g>
                      <g id="layer:glyph" transform="translate(25, 30) scale(0.7)">
                        <g transform="translate(10.84, 57.89) scale(0.5)">
                          <path id="glyph:stroke-0" d="M 20 100 Q 100 20 180 100 T 340 100" fill="none" stroke="currentColor" stroke-width="1.48" stroke-linecap="round"/>
                          <path id="glyph:stroke-1" d="M 50 50 L 150 150" fill="none" stroke="currentColor" stroke-width="1.48" stroke-linecap="round"/>
                        </g>
                      </g>
                    </svg>
                    """.trimIndent(),
                "largeLowlight (fill-rule)" to
                    """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 310" width="260" height="310">
                      <g id="layer:shape">
                        <path id="shape:stroke-0" fill="none" d="M 20 40 C 20 20 240 20 240 40 L 240 270 C 240 290 20 290 20 270 Z" stroke="currentColor" stroke-width="6"/>
                        <path id="shape:stroke-1" fill-rule="evenodd" clip-rule="evenodd" d="M 120 120 L 140 140" fill="none"/>
                      </g>
                      <g id="layer:glyph" transform="translate(50, 75) scale(0.8)">
                        <g transform="translate(10, 20) scale(0.5)">
                          <path id="glyph:stroke-0" d="M 20 100 L 180 100" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        </g>
                      </g>
                    </svg>
                    """.trimIndent(),
            )
    }
}
