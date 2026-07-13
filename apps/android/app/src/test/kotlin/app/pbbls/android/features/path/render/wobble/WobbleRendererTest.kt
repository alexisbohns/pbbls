// Inlined SVG markup lines are machine-shaped and cannot wrap.
@file:Suppress("ktlint:standard:max-line-length")

package app.pbbls.android.features.path.render.wobble

import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleSvgModel
import app.pbbls.android.features.path.render.parsePebbleSvg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mirrors iOS `WobbleRendererTests.swift`. The backdrop-assets case reads the
 * nine `res/raw` files straight from disk (the Gradle test task's working
 * directory is the module root, same trick as `LocalizationParityTest` — JVM
 * tests can't reach Android resources), which doubles as an asset regression
 * test.
 */
class WobbleRendererTest {
    private val composedSvg =
        """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
          <g id="layer:shape">
            <path d="M 20 130 C 20 70 70 20 130 20 C 190 20 240 70 240 130 C 240 190 190 240 130 240 C 70 240 20 190 20 130 Z" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:glyph" transform="translate(55, 55) scale(0.75)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none" stroke="currentColor"/>
          </g>
        </svg>
        """.trimIndent()

    @Test
    fun `pebble art is cached by svg content and index-aligned with layers`() {
        val model = requireNotNull(parsePebbleSvg(composedSvg))
        val first = WobbleRenderer.pebbleArt(composedSvg, model)
        val second = WobbleRenderer.pebbleArt(composedSvg, model)
        assertSame(first, second)
        assertEquals(model.layers.size, first.layers.size)
        for (layer in first.layers) {
            assertTrue(layer.ink.isNotEmpty())
            assertTrue(layer.centerline.isNotEmpty())
        }
    }

    @Test
    fun `glyph layers wobble in slot space with the pre-divided half-width`() {
        val model = requireNotNull(parsePebbleSvg(composedSvg))
        assertEquals(PebbleSvgModel.Layer.Kind.GLYPH, model.layers[1].kind)
        val art = WobbleRenderer.pebbleArt(composedSvg, model)
        // The glyph's ink is in the raw 200-box (pre-transform): its extent
        // must be ~200 units, not the 150 the 0.75 layer scale would give.
        val xs =
            art.layers[1]
                .ink
                .flatten()
                .map { it.x }
        assertTrue("glyph ink looks canvas-spaced: ${xs.max() - xs.min()}", xs.max() - xs.min() > 170)
    }

    @Test
    fun `space rule scales amplitude, frequency and step`() {
        val params = WobbleParams.scaled(260.0, 310.0)
        val normalization = 200.0 / 310.0
        assertEquals(18 / normalization, params.amplitude, 1e-12)
        assertEquals(0.024 * normalization, params.frequency, 1e-12)
        assertEquals(2 / normalization, params.flattenStep, 1e-12)
        assertEquals(5, params.octaves)
    }

    @Test
    fun `degenerate space falls back to canonical params`() {
        val params = WobbleParams.scaled(0.0, 0.0)
        assertEquals(WobbleParams.CANONICAL.amplitude, params.amplitude, 0.0)
        assertEquals(WobbleParams.CANONICAL.frequency, params.frequency, 0.0)
    }

    @Test
    fun `all nine backdrop assets parse and wobble`() {
        for (size in ValenceSizeGroup.entries) {
            for (polarity in ValencePolarity.entries) {
                val name = "${size.key}-${polarity.key}"
                val art =
                    requireNotNull(WobbleRenderer.backdropArt(name, backdropAsset(size, polarity))) {
                        "backdrop art failed for $name"
                    }
                assertTrue(art.contours.isNotEmpty())
                assertTrue(art.viewBox.width > 0)
                assertTrue(art.viewBox.height > 0)
            }
        }
        // The only evenodd asset is large-lowlight (it carves a hole).
        val lowlight = WobbleRenderer.backdropArt(backdropAsset(ValenceSizeGroup.LARGE, ValencePolarity.LOWLIGHT))
        assertEquals(true, lowlight?.usesEvenOddFill)
        val neutral = WobbleRenderer.backdropArt(backdropAsset(ValenceSizeGroup.MEDIUM, ValencePolarity.NEUTRAL))
        assertEquals(false, neutral?.usesEvenOddFill)
    }

    @Test
    fun `asset parsing survives on synthetic markup and rejects garbage`() {
        val asset =
            """
            <svg width="200" height="100" viewBox="0 0 200 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M10 50 C 10 10, 190 10, 190 50 C 190 90, 10 90, 10 50 Z" fill="#FF00FF"/>
            </svg>
            """.trimIndent()
        val art = WobbleRenderer.backdropArt(asset)
        assertNotNull(art)
        assertEquals(PebbleSvgModel.ViewBox(0f, 0f, 200f, 100f), art?.viewBox)
        assertEquals(false, art?.usesEvenOddFill)
        assertNull(WobbleRenderer.backdropArt("<svg></svg>"))
    }

    @Test
    fun `glyph ink parses carve strokes and caches by content`() {
        val d = "M30,30 Q60,80 100,100 L170,170"
        val first = WobbleRenderer.glyphInk(d, width = 6.0)
        val second = WobbleRenderer.glyphInk(d, width = 6.0)
        assertNotNull(first)
        assertSame(first, second)
        // A different width is different ink.
        val wider = WobbleRenderer.glyphInk(d, width = 10.0)
        assertNotSame(wider, first)
        // Unparseable input falls back to null (caller strokes plainly).
        assertNull(WobbleRenderer.glyphInk("not a path", width = 6.0))
    }

    private fun backdropAsset(
        size: ValenceSizeGroup,
        polarity: ValencePolarity,
    ): String {
        val file = File("src/main/res/raw/outline_${size.key}_${polarity.key}.svg")
        check(file.exists()) { "Missing outline asset: ${file.absolutePath}" }
        return file.readText()
    }
}
