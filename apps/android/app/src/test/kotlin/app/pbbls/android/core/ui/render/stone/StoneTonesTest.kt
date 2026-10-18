package app.pbbls.android.core.ui.render.stone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import app.pbbls.android.core.model.BlankPalette
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.ValencePolarity.HIGHLIGHT
import app.pbbls.android.core.model.ValencePolarity.LOWLIGHT
import app.pbbls.android.core.model.ValencePolarity.NEUTRAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `stone-tones.ts` transcribed over the pride palette
 * (`apps/web/lib/seed/sandbox-palettes.ts`), plus the two colour mixes and
 * the night filter matrices the web leaves to the browser.
 */
class StoneTonesTest {
    private val pride: EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#A9478AFF",
                secondaryHex = "#EA91CEFF",
                lightHex = "#F6EDF3FF",
                surfaceHex = "#A9478A1A",
                darkHex = "#220E1CFF",
                shadedHex = "#763261FF",
            ),
        )
    private val p = pride.toStonePalette()
    private val ground = Color.White

    private fun assertChannels(
        expected: Color,
        actual: Color,
        tolerance: Float,
    ) {
        assertEquals("red", expected.red, actual.red, tolerance)
        assertEquals("green", expected.green, actual.green, tolerance)
        assertEquals("blue", expected.blue, actual.blue, tolerance)
        assertEquals("alpha", 1f, actual.alpha, 0f)
    }

    /** Row-major 4×5: `out = M × [r g b a 1]`, no translation in the matrices under test. */
    private fun ColorMatrix.applyTo(color: Color): Color {
        val input = floatArrayOf(color.red, color.green, color.blue, color.alpha, 1f)
        val out = FloatArray(4) { row -> (0 until 5).sumOf { col -> (this[row, col] * input[col]).toDouble() }.toFloat() }
        return Color(out[0].coerceIn(0f, 1f), out[1].coerceIn(0f, 1f), out[2].coerceIn(0f, 1f), out[3].coerceIn(0f, 1f))
    }

    @Test
    fun `toStonePalette maps the five slots`() {
        assertEquals(pride.primary, p.primary)
        assertEquals(pride.secondary, p.secondary)
        assertEquals(pride.light, p.light)
        assertEquals(pride.shaded, p.shaded)
        assertEquals(pride.dark, p.dark)
    }

    @Test
    fun `the blank palette is the shame greys`() {
        assertEquals(BlankPalette.primary, BLANK_STONE_PALETTE.primary)
        assertEquals(BlankPalette.secondary, BLANK_STONE_PALETTE.secondary)
        assertEquals(BlankPalette.light, BLANK_STONE_PALETTE.light)
        assertEquals(BlankPalette.shaded, BLANK_STONE_PALETTE.shaded)
        assertEquals(BlankPalette.dark, BLANK_STONE_PALETTE.dark)
    }

    @Test
    fun `highlight body ramp`() {
        val ramp = StoneTones.bodyRamp(HIGHLIGHT, p)
        assertEquals(listOf(0f, 0.4f, 1f), ramp.map { it.offset })
        assertEquals(StoneTones.mixSrgb(p.secondary, 0.66f, p.light), ramp[0].color)
        assertEquals(p.primary, ramp[1].color)
        assertEquals(StoneTones.mixSrgb(p.shaded, 0.7f, p.dark), ramp[2].color)
    }

    @Test
    fun `neutral body ramp`() {
        val ramp = StoneTones.bodyRamp(NEUTRAL, p)
        assertEquals(listOf(0f, 0.5f, 1f), ramp.map { it.offset })
        assertEquals(p.secondary, ramp[1].color)
        assertEquals(StoneTones.mixSrgb(p.primary, 0.72f, p.secondary), ramp[2].color)
    }

    @Test
    fun `lowlight body ramp ends on dark`() {
        val ramp = StoneTones.bodyRamp(LOWLIGHT, p)
        assertEquals(listOf(0f, 0.5f, 1f), ramp.map { it.offset })
        assertEquals(StoneTones.mixOklab(p.secondary, 0.31f, p.shaded), ramp[0].color)
        assertEquals(p.dark, ramp[2].color)
    }

    @Test
    fun `glyph tones`() {
        val (lip, ink) = StoneTones.glyphTones(LOWLIGHT, p)
        assertEquals(StonePaint(p.secondary, 0.5f), lip)
        assertEquals(Color.Black, ink.color)
        assertEquals(0.66f, ink.opacity, 0f)
        assertEquals(StonePaint(p.light, 0.6f), StoneTones.glyphTones(HIGHLIGHT, p).first)
        assertEquals(StonePaint(p.shaded, 0.9f), StoneTones.glyphTones(NEUTRAL, p).second)
    }

    @Test
    fun `vein tones`() {
        val neutral = StoneTones.veinTones(NEUTRAL, p)
        assertEquals(2, neutral.size)
        assertEquals(listOf(9f, 2.4f), neutral.map { it.widthPx })
        assertEquals(1.5f to 3.5f, neutral[1].dx to neutral[1].dy)
        assertEquals(0f to 0f, neutral[0].dx to neutral[0].dy)
        val lowlight = StoneTones.veinTones(LOWLIGHT, p)
        assertTrue(lowlight[0].glow)
        assertFalse(lowlight[1].glow)
        assertEquals(StoneTones.mixSrgb(p.primary, 0.5f, p.secondary), lowlight[1].color)
        assertEquals(listOf(StonePaint(p.light, 0.3f, widthPx = 3f)), StoneTones.veinTones(HIGHLIGHT, p))
    }

    @Test
    fun `fossil tones`() {
        assertEquals(listOf(StonePaint(p.light, 0.18f)), StoneTones.fossilTones(HIGHLIGHT, p))
        val neutral = StoneTones.fossilTones(NEUTRAL, p)
        assertEquals(StonePaint(p.light, 0.35f, dx = -1.5f, dy = -2f), neutral[0])
        assertNull(neutral[0].widthPx)
        assertEquals(listOf(StonePaint(p.dark, 0.5f)), StoneTones.fossilTones(LOWLIGHT, p))
    }

    @Test
    fun `edge shade is dark at three widths`() {
        assertEquals(StonePaint(p.dark, 0.5f, widthPx = 5f), StoneTones.edgeShade(HIGHLIGHT, p))
        assertEquals(StonePaint(p.dark, 0.26f, widthPx = 6f), StoneTones.edgeShade(NEUTRAL, p))
        assertEquals(StonePaint(p.dark, 0.8f, widthPx = 6f), StoneTones.edgeShade(LOWLIGHT, p))
    }

    @Test
    fun `glint and grain strengths`() {
        assertEquals(1f, StoneTones.glintStrength(HIGHLIGHT), 0f)
        assertEquals(0.3f, StoneTones.glintStrength(NEUTRAL), 0f)
        assertEquals(0.34f, StoneTones.glintStrength(LOWLIGHT), 0f)
        assertEquals(0f, StoneTones.grainStrength(HIGHLIGHT), 0f)
        assertEquals(0.14f, StoneTones.grainStrength(NEUTRAL), 0f)
        assertEquals(0.32f, StoneTones.grainStrength(LOWLIGHT), 0f)
    }

    @Test
    fun `slot colours`() {
        assertEquals(p.light, StoneTones.slotColor(StoneSlot.LIGHT, p))
        assertEquals(p.shaded, StoneTones.slotColor(StoneSlot.SHADED, p))
        assertEquals(p.dark, StoneTones.slotColor(StoneSlot.DARK, p))
        assertEquals(Color.White, StoneTones.slotColor(StoneSlot.WHITE, p))
    }

    @Test
    fun `spot is primary on paper and secondary at night`() {
        assertEquals(p.primary, StoneTones.spot(p, night = false))
        assertEquals(p.secondary, StoneTones.spot(p, night = true))
    }

    @Test
    fun `deep is shaded on paper and secondary toward the ground at night`() {
        assertEquals(p.shaded, StoneTones.deep(p, ground, night = false))
        assertEquals(StoneTones.mixOklab(p.secondary, 0.8f, ground), StoneTones.deep(p, ground, night = true))
    }

    @Test
    fun `the card edge by valence and edition`() {
        assertEquals(p.primary, StoneTones.edge(p, HIGHLIGHT, night = false))
        assertEquals(p.secondary, StoneTones.edge(p, NEUTRAL, night = false))
        assertEquals(p.dark, StoneTones.edge(p, LOWLIGHT, night = false))
        assertEquals(p.secondary, StoneTones.edge(p, HIGHLIGHT, night = true))
        assertEquals(p.primary, StoneTones.edge(p, NEUTRAL, night = true))
        assertEquals(p.shaded, StoneTones.edge(p, LOWLIGHT, night = true))
    }

    @Test
    fun `mixSrgb is a per-channel lerp of the encoded values`() {
        val mid = StoneTones.mixSrgb(Color.White, 0.5f, Color.Black)
        assertChannels(Color(0x80 / 255f, 0x80 / 255f, 0x80 / 255f), mid, 1f / 255f)
        assertEquals(p.secondary, StoneTones.mixSrgb(p.secondary, 1f, p.light))
        assertEquals(p.light, StoneTones.mixSrgb(p.secondary, 0f, p.light))
    }

    @Test
    fun `mixOklab midpoint of white and black is OKLab L one half`() {
        // L = .5 is linear .125, which encodes to sRGB ≈ .389 (#636363): darker
        // than the sRGB midpoint, as the browser's color-mix(in oklab) draws it.
        val mid = StoneTones.mixOklab(Color.White, 0.5f, Color.Black)
        assertChannels(Color(0.389f, 0.389f, 0.389f), mid, 0.01f)
        assertTrue(mid.red < 0x80 / 255f)
    }

    @Test
    fun `mixOklab endpoints are exact`() {
        assertEquals(p.secondary, StoneTones.mixOklab(p.secondary, 1f, p.shaded))
        assertEquals(p.shaded, StoneTones.mixOklab(p.secondary, 0f, p.shaded))
    }

    @Test
    fun `mixOklab round-trips a colour through OKLab within a step`() {
        // A 50 % mix of a colour with itself exercises both conversions.
        assertChannels(p.primary, StoneTones.mixOklab(p.primary, 0.5f, p.primary), 1f / 255f)
    }

    @Test
    fun `night matrix dims and desaturates the stone`() {
        val red = StoneTones.nightMatrix(NEUTRAL).applyTo(Color.Red)
        assertTrue("red ${red.red}", red.red in 0.85f..0.95f)
        assertTrue("green ${red.green}", red.green > 0f && red.green < 0.05f)
        assertTrue("blue ${red.blue}", red.blue > 0f && red.blue < 0.05f)
        assertEquals(1f, red.alpha, 0f)
        // The gem and the river pebble share the stone filter; only lava differs.
        assertEquals(red, StoneTones.nightMatrix(HIGHLIGHT).applyTo(Color.Red))
    }

    @Test
    fun `night matrix brightens lava`() {
        val grey = StoneTones.nightMatrix(LOWLIGHT).applyTo(Color(0.5f, 0.5f, 0.5f))
        assertEquals(0.6f, grey.red, 0.01f)
        assertEquals(grey.red, grey.green, 1e-4f)
        assertEquals(grey.red, grey.blue, 1e-4f)
    }
}
