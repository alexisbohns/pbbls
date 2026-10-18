package app.pbbls.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The Spine's paper, from the scheme (spec 2026-10-18-android-spine-brand-scheme-design.md,
 * §The rule set). Alphas ride on `onSurface` as the web's tokens ride on its ink. Emotion
 * palettes are never scheme roles (#853): the spot, deep and edge tones take a palette.
 */
@Immutable
data class SpineColors(
    val isNight: Boolean,
    val ground: Color,
    val stock: Color,
    val halo: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val cardRule: Color,
    val line: Color,
    val seam: Color,
    val teeth: Color,
    val ring: Color,
    val ripple: Color,
    val shadow: Color,
    val shadowAlpha: Float,
    val tintK: Float,
    val patK: Float,
    val error: Color,
) {
    companion object {
        fun from(
            scheme: ColorScheme,
            night: Boolean,
        ): SpineColors {
            val ink = scheme.onSurface
            return SpineColors(
                isNight = night,
                ground = scheme.surface,
                stock = if (night) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
                halo = scheme.surfaceContainerLow,
                ink = ink,
                ink2 = scheme.onSurfaceVariant,
                ink3 = scheme.outline,
                rule = ink.copy(alpha = if (night) 0.12f else 0.14f),
                cardRule = ink.copy(alpha = if (night) 0.18f else 0.22f),
                line = ink.copy(alpha = if (night) 0.30f else 0.34f),
                seam = ink.copy(alpha = if (night) 0.24f else 0.30f),
                teeth = ink.copy(alpha = if (night) 0.14f else 0.20f),
                ring = if (night) ink.copy(alpha = 0.17f) else scheme.onSurfaceVariant.copy(alpha = 0.30f),
                ripple = ink.copy(alpha = if (night) 0.50f else 0.55f),
                shadow = if (night) Color.Black else ink,
                shadowAlpha = if (night) 0.62f else 0.30f,
                tintK = if (night) 0.13f else 0.09f,
                patK = if (night) 0.40f else 0.34f,
                error = scheme.error,
            )
        }
    }
}

/** Both Spine token sets, read together through `PebblesTheme.spine`. */
@Immutable
data class SpineTokens(
    val type: SpineTypography,
    val colors: SpineColors,
)

/** For previews that bypass `PebblesTheme`; agrees with the value it provides. */
@Composable
fun rememberSpineColors(): SpineColors {
    val scheme = MaterialTheme.colorScheme
    val night = isSystemInDarkTheme()
    return remember(scheme, night) { SpineColors.from(scheme, night) }
}
