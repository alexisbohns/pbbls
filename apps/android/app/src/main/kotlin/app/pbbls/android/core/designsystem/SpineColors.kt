package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
    /** The rake's groove shadow (`SpineGround`): the one Spine tone not riding on `onSurface` at Night. */
    val rakeShadow: Color,
    /** The rake's lit line: white on Paper, a cool `#DCE2EA` at Night — the one hex the Spine writes. */
    val rakeLight: Color,
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
                rakeShadow = if (night) Color.Black.copy(alpha = 0.34f) else ink.copy(alpha = 0.20f),
                rakeLight = if (night) Color(0xFFDCE2EA).copy(alpha = 0.075f) else Color.White.copy(alpha = 0.55f),
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

/** For previews: the same object the theme provides (the default one outside `PebblesTheme`). */
@Composable
fun rememberSpineColors(): SpineColors = LocalSpineTokens.current.colors
