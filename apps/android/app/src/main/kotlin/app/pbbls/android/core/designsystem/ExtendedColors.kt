package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * A custom colour's four roles, shaped like Material Theme Builder's Jetpack
 * export. Values come from the generated `ColorSchemes.kt`.
 */
@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

/**
 * The theme's custom colours (#990), which M3's `ColorScheme` has no slot for.
 * Read them through [PebblesTheme.colors]. Sand is the warm amber the error
 * role used to wear, kept verbatim.
 */
@Immutable
data class ExtendedColors(
    val sand: ColorFamily,
)

internal fun pebblesExtendedColors(
    dark: Boolean,
    contrast: ContrastLevel,
): ExtendedColors = ExtendedColors(sand = pebblesSand(dark, contrast))

val LocalExtendedColors = staticCompositionLocalOf { pebblesExtendedColors(dark = false, contrast = ContrastLevel.STANDARD) }

/**
 * [this] (a wallpaper scheme) with the brand's error roles pinned over it
 * (#990). The OS derives a wallpaper scheme's error from Material's red, and
 * red is ruled out for Pebbles: every other role still follows the wallpaper.
 */
internal fun ColorScheme.withErrorFrom(brand: ColorScheme): ColorScheme =
    copy(
        error = brand.error,
        onError = brand.onError,
        errorContainer = brand.errorContainer,
        onErrorContainer = brand.onErrorContainer,
    )
