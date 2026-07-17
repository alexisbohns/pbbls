package app.pbbls.android.features.path.models

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Hex values mirror the iOS `EmotionPaletteTests` fixture (a real category
 * palette shape: opaque primary/secondary/light, 10%-alpha surface).
 */
class EmotionPaletteTest {
    private fun makePalette(): EmotionPalette? =
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#2A2138FF",
            shadedHex = "#4A3A5CFF",
        )

    // parseColor

    @Test
    fun `parses 6-digit hex with and without hash`() {
        assertEquals(Color(0xFF7B5E99), EmotionPalette.parseColor("#7B5E99"))
        assertEquals(Color(0xFF7B5E99), EmotionPalette.parseColor("7B5E99"))
    }

    @Test
    fun `parses 8-digit RRGGBBAA into ARGB`() {
        // DB byte order is RGBA; Compose is ARGB — the alpha byte relocates.
        assertEquals(Color(0xFF7B5E99), EmotionPalette.parseColor("#7B5E99FF"))
        assertEquals(Color(0x1A7B5E99), EmotionPalette.parseColor("#7B5E991A"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(Color(0xFF7B5E99), EmotionPalette.parseColor("  #7B5E99  "))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(EmotionPalette.parseColor("#7B5E9"))
        assertNull(EmotionPalette.parseColor("#7B5E991"))
        assertNull(EmotionPalette.parseColor("#ZZZZZZ"))
        assertNull(EmotionPalette.parseColor(""))
    }

    // fromHex

    @Test
    fun `fromHex succeeds with well-formed 8-digit hex`() {
        assertNotNull(makePalette())
    }

    @Test
    fun `fromHex returns null when any hex is malformed`() {
        assertNull(
            EmotionPalette.fromHex(
                primaryHex = "not-hex",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
                shadedHex = "#4A3A5CFF",
            ),
        )
    }

    @Test
    fun `fromHex trims whitespace before storing`() {
        val palette =
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF  ",
                secondaryHex = "  #AE91CCFF",
                lightHex = "#F2EFF5FF\n",
                surfaceHex = "#7B5E991A ",
                darkHex = "  #2A2138FF ",
                shadedHex = " #4A3A5CFF ",
            )
        assertEquals("#7B5E99FF", palette?.primaryHex)
        assertEquals("#7B5E991A", palette?.surfaceHex)
        assertEquals("#2A2138FF", palette?.darkHex)
        assertEquals("#4A3A5CFF", palette?.shadedHex)
        assertEquals("#7B5E99", palette?.strokeHex(isDark = false))
    }

    @Test
    fun `fromHex captures the dark slot and rejects a malformed one`() {
        assertEquals("#2A2138FF", makePalette()?.darkHex)
        assertNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "not-hex",
                shadedHex = "#4A3A5CFF",
            ),
        )
    }

    @Test
    fun `fromHex captures the shaded slot and rejects a malformed one`() {
        assertEquals("#4A3A5CFF", makePalette()?.shadedHex)
        assertEquals(Color(0xFF4A3A5C), makePalette()?.shaded)
        assertNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
                shadedHex = "not-hex",
            ),
        )
    }

    // strokeHex

    @Test
    fun `strokeHex returns 6-digit primary in light and secondary in dark`() {
        val palette = makePalette()
        assertEquals("#7B5E99", palette?.strokeHex(isDark = false))
        assertEquals("#AE91CC", palette?.strokeHex(isDark = true))
    }

    // pebbleFrameColors

    @Test
    fun `large pebbles frame with light stroke and opaque primary fill`() {
        val colors = makePalette()?.pebbleFrameColors(intensity = 3)
        assertEquals("#F2EFF5", colors?.strokeHex)
        assertEquals("#7B5E99", colors?.fillHex)
        assertEquals(1f, colors!!.fillOpacity, 0.001f)
    }

    @Test
    fun `small and medium pebbles frame with secondary stroke and surface wash`() {
        val palette = makePalette()
        for (intensity in listOf(1, 2)) {
            val colors = palette?.pebbleFrameColors(intensity = intensity)
            assertEquals("#AE91CC", colors?.strokeHex)
            assertEquals("#7B5E99", colors?.fillHex)
            // Surface alpha 0x1A = 26/255 ≈ 0.102 — the faint wash.
            assertEquals(0x1A / 255f, colors!!.fillOpacity, 0.001f)
        }
    }

    @Test
    fun `alphaComponent defaults to 1 for 6-digit or unparseable input`() {
        assertEquals(1f, EmotionPalette.alphaComponent("#7B5E99"), 0f)
        assertEquals(1f, EmotionPalette.alphaComponent("#7B5E99ZZ"), 0f)
    }

    @Test
    fun `rgbHex passes 6-digit input through unchanged`() {
        assertEquals("#7B5E99", EmotionPalette.rgbHex("#7B5E99"))
        assertEquals("#7B5E99", EmotionPalette.rgbHex("#7B5E99FF"))
    }
}
