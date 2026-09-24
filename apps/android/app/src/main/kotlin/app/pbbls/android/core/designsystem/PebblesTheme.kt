package app.pbbls.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalSpacing = staticCompositionLocalOf { Spacing }

val LocalHandTypography = staticCompositionLocalOf { PebblesHandTypography }

/**
 * Pebbles' own tokens beside Material's (#853). Colour, type and shape are
 * `MaterialTheme.*`; this object keeps only what M3 has no slot for —
 * [spacing] and the handwritten faces ([hand]).
 */
object PebblesTheme {
    val spacing: Spacing
        @Composable get() = LocalSpacing.current

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
    CompositionLocalProvider(
        LocalSpacing provides Spacing,
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
