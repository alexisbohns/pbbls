package app.pbbls.android.features.path.read

import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.ValenceSizeGroup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [petroglyphColors] — the #599 per-size, per-theme colour table. Fixture hex
 * matches the shared `EmotionPaletteTest` palette (opaque primary/secondary/
 * light/dark, 10%-alpha surface).
 */
class PetroglyphColorsTest {
    private val palette: EmotionPalette =
        requireNotNull(
            EmotionPalette.fromHex(
                primaryHex = "#7B5E99FF",
                secondaryHex = "#AE91CCFF",
                lightHex = "#F2EFF5FF",
                surfaceHex = "#7B5E991A",
                darkHex = "#2A2138FF",
            ),
        )

    @Test
    fun `large is light stroke over opaque primary backfill in both themes`() {
        for (isDark in listOf(false, true)) {
            val colors = petroglyphColors(palette, ValenceSizeGroup.LARGE, isDark)
            assertEquals("#F2EFF5", colors.strokeHex)
            assertEquals("#7B5E99", colors.fillHex)
            assertEquals(1f, colors.fillOpacity, 0.001f)
        }
    }

    @Test
    fun `small and medium in light mode use primary stroke over solid light backfill`() {
        for (size in listOf(ValenceSizeGroup.SMALL, ValenceSizeGroup.MEDIUM)) {
            val colors = petroglyphColors(palette, size, isDark = false)
            assertEquals("#7B5E99", colors.strokeHex)
            assertEquals("#F2EFF5", colors.fillHex)
            assertEquals(1f, colors.fillOpacity, 0.001f)
        }
    }

    @Test
    fun `small and medium in dark mode use secondary stroke over the solid dark backfill`() {
        for (size in listOf(ValenceSizeGroup.SMALL, ValenceSizeGroup.MEDIUM)) {
            val colors = petroglyphColors(palette, size, isDark = true)
            assertEquals("#AE91CC", colors.strokeHex)
            // "palette.dark" == the dedicated opaque dark_color slot.
            assertEquals("#2A2138", colors.fillHex)
            assertEquals(1f, colors.fillOpacity, 0.001f)
        }
    }
}
