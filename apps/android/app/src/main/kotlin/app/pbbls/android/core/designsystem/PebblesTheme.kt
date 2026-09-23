package app.pbbls.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

@Suppress("DEPRECATION")
val LocalSystemPalette = staticCompositionLocalOf { systemPaletteFrom(LightScheme) }

@Suppress("DEPRECATION")
val LocalAccentPalette = staticCompositionLocalOf { accentPaletteFrom(LightScheme) }

val LocalSpacing = staticCompositionLocalOf { Spacing }

@Suppress("DEPRECATION")
val LocalPebblesTypography = staticCompositionLocalOf { PebblesTypography }

val LocalHandTypography = staticCompositionLocalOf { PebblesHandTypography }

@Suppress("DEPRECATION")
@Deprecated("Bridge for #853: use MaterialTheme.colorScheme roles")
data class PebblesColors(
    val system: SystemPalette,
    val accent: AccentPalette,
)

/**
 * Pebbles' own tokens beside Material's (#853). Colour, type and shape are
 * `MaterialTheme.*`; this object keeps only what M3 has no slot for —
 * [spacing] and the handwritten faces ([hand]). [colors] and [type] are the
 * Part 1 bridge and go in Part 7.
 */
object PebblesTheme {
    @Suppress("DEPRECATION")
    @Deprecated("Bridge for #853: use MaterialTheme.colorScheme roles")
    val colors: PebblesColors
        @Composable get() = PebblesColors(LocalSystemPalette.current, LocalAccentPalette.current)

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    @Suppress("DEPRECATION")
    @Deprecated("Bridge for #853: use MaterialTheme.typography roles")
    val type: PebblesTypography
        @Composable get() = LocalPebblesTypography.current

    val hand: PebblesHandTypography
        @Composable get() = LocalHandTypography.current
}

/**
 * Root theme (#853, supersedes D6). The M3-evo export in
 * `MaterialExpressiveTheme`, with Expressive motion, the export's type on the
 * M3 scale, and Expressive shapes.
 *
 * Scheme precedence: wallpaper colour when [dynamicColor] (minSdk 33 always
 * supports it; the OS applies its own contrast level to those), otherwise the
 * export's scheme for the system contrast level. [dynamicColor] defaults to
 * false so previews and screenshot references render the brand scheme; only
 * `MainActivity` passes the user's setting.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val contrast = rememberContrastLevel()
    val context = LocalContext.current
    val scheme =
        when {
            dynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            else -> pebblesColorScheme(dark, contrast)
        }

    @Suppress("DEPRECATION")
    val system = remember(scheme) { systemPaletteFrom(scheme) }

    @Suppress("DEPRECATION")
    val accent = remember(scheme) { accentPaletteFrom(scheme) }
    @Suppress("DEPRECATION")
    CompositionLocalProvider(
        LocalSystemPalette provides system,
        LocalAccentPalette provides accent,
        LocalSpacing provides Spacing,
        LocalPebblesTypography provides PebblesTypography,
        LocalHandTypography provides PebblesHandTypography,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = PebblesMaterialTypography,
            shapes = PebblesShapes,
            content = content,
        )
    }
}
