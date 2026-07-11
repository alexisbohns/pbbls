package app.pbbls.android.theme

import androidx.compose.ui.graphics.Color

/**
 * Four-tier system palette for interface chrome. Mirrors the structural shape
 * used for per-emotion palettes so token-aware UI code reads uniformly.
 * Ported from `apps/ios/Pebbles/Theme/Palettes.swift`.
 */
data class SystemPalette(
    val foreground: Color,
    val secondary: Color,
    val muted: Color,
    val background: Color,
)

/**
 * Six-tier brand-accent palette. `primaryHex` is exposed for SVG-text
 * injection (D — `currentColor` replacement in path-rendered SVG markup).
 */
data class AccentPalette(
    val dark: Color,
    val shaded: Color,
    val primary: Color,
    val secondary: Color,
    val light: Color,
    val surface: Color,
    val primaryHex: String,
)

internal val SystemPaletteLight =
    SystemPalette(
        foreground = Color(0xFF4A3639),
        secondary = Color(0xFF7A5E64),
        muted = Color(0xFFE9E2E4),
        background = Color(0xFFFFFFFF),
    )

internal val SystemPaletteDark =
    SystemPalette(
        foreground = Color(0xFFE9E2E4),
        secondary = Color(0xFFAF979D),
        muted = Color(0xFF2E2024),
        background = Color(0xFF171012),
    )

// No dark variant — identical in both themes (mirrors iOS `AccentPalette`).
internal val AccentPaletteShared =
    AccentPalette(
        dark = Color(0xFF341B1B),
        shaded = Color(0xFF8C4949),
        primary = Color(0xFFC07A7A),
        secondary = Color(0xFFEAD3D3),
        light = Color(0xFFFAF4F4),
        surface = Color(0x1AC07A7A),
        primaryHex = "#C07A7A",
    )
