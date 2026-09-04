package app.pbbls.android.components

import androidx.compose.ui.graphics.Color
import app.pbbls.android.theme.SystemPaletteDark
import app.pbbls.android.theme.SystemPaletteLight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The Google capsule is a pinned light surface, so its label must not follow the
 * theme. A dark-mode preview does not gate this (screenshot references are
 * git-ignored), so the pairing is asserted numerically instead.
 */
class GoogleSignInButtonContrastTest {
    /** WCAG 2.x relative luminance. Test-local: the app ships no contrast utility. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(
        ink: Color,
        ground: Color,
    ): Double {
        val inkLuminance = luminance(ink)
        val groundLuminance = luminance(ground)
        val lighter = max(inkLuminance, groundLuminance)
        val darker = min(inkLuminance, groundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun labelIsLegibleOnTheCapsuleInBothThemes() {
        val palettes = listOf("light" to SystemPaletteLight, "dark" to SystemPaletteDark)
        for ((name, palette) in palettes) {
            val ratio = contrastRatio(googleButtonLabelColor(palette), GoogleButtonSurface)
            assertTrue("$name theme: $ratio:1 fails WCAG AA (4.5:1)", ratio >= 4.5)
        }
    }

    @Test
    fun onLightDoesNotFollowTheTheme() {
        assertEquals(SystemPaletteLight.onLight, SystemPaletteDark.onLight)
    }
}
