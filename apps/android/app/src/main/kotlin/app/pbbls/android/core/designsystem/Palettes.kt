package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

// The #853 bridge. The old iOS-derived token vocabulary, kept only so Part 1
// can change the whole app's look without touching ~650 call sites. Every
// value is a role of the active ColorScheme. Parts 3–5 move call sites to
// MaterialTheme.colorScheme; Part 7 deletes this file.

private const val BRIDGE = "Bridge for #853: use MaterialTheme.colorScheme roles"

@Deprecated(BRIDGE)
data class SystemPalette(
    val foreground: Color,
    val secondary: Color,
    val muted: Color,
    val background: Color,
    val onLight: Color,
)

@Deprecated(BRIDGE)
data class AccentPalette(
    val dark: Color,
    val shaded: Color,
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val surface: Color,
    /** `primary` as `#RRGGBB`, for `currentColor` injection into SVG markup (6-digit only). */
    val primaryHex: String,
)

@Suppress("DEPRECATION")
internal fun systemPaletteFrom(scheme: ColorScheme): SystemPalette =
    SystemPalette(
        foreground = scheme.onSurface,
        secondary = scheme.onSurfaceVariant,
        muted = scheme.outlineVariant,
        background = scheme.surface,
        onLight = GoogleCapsuleInk,
    )

@Suppress("DEPRECATION")
internal fun accentPaletteFrom(scheme: ColorScheme): AccentPalette =
    AccentPalette(
        dark = scheme.onPrimaryContainer,
        shaded = scheme.onPrimaryContainer,
        primary = scheme.primary,
        secondary = scheme.primaryContainer,
        light = scheme.onPrimary,
        surface = scheme.primary.copy(alpha = 0.10f),
        primaryHex = scheme.primary.toRgbHex(),
    )

/** `#RRGGBB`, alpha dropped — the SVG pipeline misparses 8-digit hex. */
internal fun Color.toRgbHex(): String = String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

@Deprecated("Bridge for #853: use MaterialTheme.colorScheme.error")
internal val PebblesDestructive: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.error

@Deprecated("Bridge for #853: use MaterialTheme.colorScheme.tertiary")
internal val PebblesSuccess: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
