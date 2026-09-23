package app.pbbls.android.testing

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x relative luminance. Test-only: the app ships no contrast utility. */
private fun luminance(color: Color): Double {
    fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG contrast ratio between [ink] and [ground], 1.0–21.0. Alpha is ignored. */
fun contrastRatio(
    ink: Color,
    ground: Color,
): Double {
    val a = luminance(ink)
    val b = luminance(ground)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

/** WCAG AA for body text. */
const val AA_TEXT = 4.5

/** WCAG AA for non-text UI (borders, icons that carry meaning). */
const val AA_NON_TEXT = 3.0
