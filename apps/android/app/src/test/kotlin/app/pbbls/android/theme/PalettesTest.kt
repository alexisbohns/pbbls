package app.pbbls.android.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression guard for the hand-transcribed hex values (plan: exact iOS asset-catalog colors). */
class PalettesTest {
    @Test
    fun systemLightMatchesSpec() {
        assertEquals(Color(0xFF4A3639), SystemPaletteLight.foreground)
        assertEquals(Color(0xFF7A5E64), SystemPaletteLight.secondary)
        assertEquals(Color(0xFFE9E2E4), SystemPaletteLight.muted)
        assertEquals(Color(0xFFFFFFFF), SystemPaletteLight.background)
    }

    @Test
    fun systemDarkMatchesSpec() {
        assertEquals(Color(0xFFE9E2E4), SystemPaletteDark.foreground)
        assertEquals(Color(0xFFAF979D), SystemPaletteDark.secondary)
        assertEquals(Color(0xFF2E2024), SystemPaletteDark.muted)
        assertEquals(Color(0xFF171012), SystemPaletteDark.background)
    }

    @Test
    fun accentPaletteHasNoDarkVariantAndExposesPrimaryHex() {
        assertEquals(Color(0xFFC07A7A), AccentPaletteShared.primary)
        assertEquals(Color(0x1AC07A7A), AccentPaletteShared.surface)
        assertEquals("#C07A7A", AccentPaletteShared.primaryHex)
    }

    @Test
    fun spacingMatchesSpec() {
        assertEquals(3.0, Spacing.xs.value.toDouble(), 0.0)
        assertEquals(10.0, Spacing.sm.value.toDouble(), 0.0)
        assertEquals(13.0, Spacing.md.value.toDouble(), 0.0)
        assertEquals(17.0, Spacing.lg.value.toDouble(), 0.0)
        assertEquals(22.0, Spacing.xl.value.toDouble(), 0.0)
        assertEquals(34.0, Spacing.xxl.value.toDouble(), 0.0)
    }
}
