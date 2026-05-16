import SwiftUI

/// Four-tier system palette for interface chrome. Mirrors the structural
/// shape of `EmotionPalette` so token-aware UI code reads uniformly.
struct SystemPalette {
    let foreground: Color
    let secondary: Color
    let muted: Color
    let background: Color
}

/// Six-tier brand-accent palette. Designed on the same model as
/// per-emotion palettes (see `EmotionPalette`), extended with `dark` and
/// `shaded` tiers above `primary`.
///
/// `primaryHex` is exposed for SVG-text injection in `PebbleRenderView`
/// (which replaces `currentColor` literally inside SVG markup).
struct AccentPalette {
    let dark: Color
    let shaded: Color
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    let primaryHex: String
}

extension Color {
    static let system = SystemPalette(
        foreground: Color("SystemForeground"),
        secondary:  Color("SystemSecondary"),
        muted:      Color("SystemMuted"),
        background: Color("SystemBackground")
    )

    static let accent = AccentPalette(
        dark:       Color("AccentDark"),
        shaded:     Color("AccentShaded"),
        primary:    Color("AccentPrimary"),
        secondary:  Color("AccentSecondary"),
        light:      Color("AccentLight"),
        surface:    Color("AccentSurface"),
        primaryHex: "#C07A7A"
    )
}
