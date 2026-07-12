package app.pbbls.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
 *
 * The Pebbles system palette is mapped onto Material's color roles so the
 * Material components the app can't avoid — `ModalBottomSheet`, `DropdownMenu`,
 * `AlertDialog`, the date/time pickers, `TextField` — render on the themed
 * surface instead of Material's default light scheme (the root of the dark-mode
 * "white sheet / unreadable label" bugs). Every surface/container tier maps to
 * `background` (flat, no tonal tint): light mode keeps its white surfaces, dark
 * mode goes dark, and elevation reads from the scrim + shadow — the app's flat
 * aesthetic. `onSurface`/`onSurfaceVariant` carry the readable foreground.
 */
@Composable
fun PebblesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val systemPalette = if (dark) SystemPaletteDark else SystemPaletteLight
    val accent = AccentPaletteShared
    val colorScheme =
        if (dark) {
            darkColorScheme(
                primary = accent.primary,
                onPrimary = accent.light,
                background = systemPalette.background,
                onBackground = systemPalette.foreground,
                surface = systemPalette.background,
                onSurface = systemPalette.foreground,
                surfaceVariant = systemPalette.muted,
                onSurfaceVariant = systemPalette.secondary,
                surfaceContainerLowest = systemPalette.background,
                surfaceContainerLow = systemPalette.background,
                surfaceContainer = systemPalette.background,
                surfaceContainerHigh = systemPalette.background,
                surfaceContainerHighest = systemPalette.background,
                outline = systemPalette.secondary,
                outlineVariant = systemPalette.muted,
            )
        } else {
            lightColorScheme(
                primary = accent.primary,
                onPrimary = accent.light,
                background = systemPalette.background,
                onBackground = systemPalette.foreground,
                surface = systemPalette.background,
                onSurface = systemPalette.foreground,
                surfaceVariant = systemPalette.muted,
                onSurfaceVariant = systemPalette.secondary,
                surfaceContainerLowest = systemPalette.background,
                surfaceContainerLow = systemPalette.background,
                surfaceContainer = systemPalette.background,
                surfaceContainerHigh = systemPalette.background,
                surfaceContainerHighest = systemPalette.background,
                outline = systemPalette.secondary,
                outlineVariant = systemPalette.muted,
            )
        }
    CompositionLocalProvider(
        LocalSystemPalette provides systemPalette,
        LocalAccentPalette provides accent,
        LocalSpacing provides Spacing,
        LocalPebblesTypography provides PebblesTypography,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
