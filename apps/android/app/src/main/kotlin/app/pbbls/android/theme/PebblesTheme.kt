package app.pbbls.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalSystemPalette = staticCompositionLocalOf { SystemPaletteLight }
val LocalAccentPalette = staticCompositionLocalOf { AccentPaletteShared }
val LocalSpacing = staticCompositionLocalOf { Spacing }
val LocalPebblesTypography = staticCompositionLocalOf { PebblesTypography }

/** Bundles the two palettes behind one `.colors.system.*` / `.colors.accent.*` accessor. */
data class PebblesColors(
    val system: SystemPalette,
    val accent: AccentPalette,
)

/**
 * Token accessor — `PebblesTheme.colors.system.*`, `.colors.accent.*`,
 * `.spacing.*`, `.type.*`. Paired with the [PebblesTheme] composable below,
 * which resolves and provides the CompositionLocals (same dual object+function
 * pattern Compose's own `MaterialTheme` uses).
 */
object PebblesTheme {
    val colors: PebblesColors
        @Composable get() = PebblesColors(LocalSystemPalette.current, LocalAccentPalette.current)

    val spacing: Spacing
        @Composable get() = LocalSpacing.current

    val type: PebblesTypography
        @Composable get() = LocalPebblesTypography.current
}

/**
 * Root theme wrapper. Resolves light/dark system palette from
 * `isSystemInDarkTheme()`, provides all four design-system CompositionLocals,
 * and wraps Material 3 as the rendering engine only — no dynamic color, no
 * Material color roles in app code (D6).
 */
@Composable
fun PebblesTheme(content: @Composable () -> Unit) {
    val systemPalette = if (isSystemInDarkTheme()) SystemPaletteDark else SystemPaletteLight
    CompositionLocalProvider(
        LocalSystemPalette provides systemPalette,
        LocalAccentPalette provides AccentPaletteShared,
        LocalSpacing provides Spacing,
        LocalPebblesTypography provides PebblesTypography,
    ) {
        MaterialTheme(content = content)
    }
}
