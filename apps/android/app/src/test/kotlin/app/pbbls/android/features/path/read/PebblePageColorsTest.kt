package app.pbbls.android.features.path.read

import androidx.compose.ui.graphics.Color
import app.pbbls.android.features.path.models.EmotionPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [pebblePageColors] — the #605 whole-page emotion-palette colour table. Fixture
 * hex matches the shared `EmotionPaletteTest` palette (opaque primary/secondary/
 * light/dark/shaded, 10%-alpha surface). Each element must map to the exact
 * palette role per theme.
 */
class PebblePageColorsTest {
    private val palette: EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
                shadedHex = "#4A3A5CFF",
            ),
        )

    private val primary = Color(0xFF7B5E99)
    private val secondary = Color(0xFFAE91CC)
    private val light = Color(0xFFF2EFF5)
    private val surface = Color(0x1A7B5E99)
    private val dark = Color(0xFF2A2138)
    private val shaded = Color(0xFF4A3A5C)

    @Test
    fun `light mode maps each element to its role`() {
        val colors = pebblePageColors(palette, isDark = false)
        assertEquals(light, colors.background)
        assertEquals(shaded, colors.title)
        assertEquals(secondary, colors.date)
        assertEquals(surface, colors.tileBackground)
        assertEquals(secondary, colors.tileIcon)
        assertEquals(shaded, colors.tileLabel)
        assertEquals(shaded, colors.description)
        assertEquals(secondary, colors.soulGlyph)
        assertEquals(primary, colors.soulName)
    }

    @Test
    fun `dark mode maps each element to its role`() {
        val colors = pebblePageColors(palette, isDark = true)
        assertEquals(dark, colors.background)
        assertEquals(light, colors.title)
        assertEquals(primary, colors.date)
        assertEquals(surface, colors.tileBackground)
        assertEquals(primary, colors.tileIcon)
        assertEquals(light, colors.tileLabel)
        assertEquals(light, colors.description)
        assertEquals(primary, colors.soulGlyph)
        assertEquals(light, colors.soulName)
    }

    @Test
    fun `tile background is the surface wash in both themes`() {
        val lightBg = pebblePageColors(palette, isDark = false).tileBackground
        val darkBg = pebblePageColors(palette, isDark = true).tileBackground
        assertEquals(surface, lightBg)
        assertEquals(surface, darkBg)
    }
}
