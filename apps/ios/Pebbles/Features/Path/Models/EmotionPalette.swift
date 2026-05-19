import SwiftUI

/// The four colors of an emotion category palette, plus role-named accessors
/// that map to the design-token contract:
///
/// - **Accent context** (used by the emotion meta pill on the read view):
///   `accentBackground` = primary, `accentForeground` = light. Both
///   scheme-independent.
/// - **Pebble stroke**: primary in light mode, secondary in dark mode.
///   Exposed both as a SwiftUI `Color` (for SwiftUI shape strokes) and as a
///   raw hex `String` (for SVG-text injection in `PebbleRenderView`, which
///   replaces `currentColor` literally inside the SVG markup).
///
/// Initialized from the four 8-digit hex strings stored on
/// `public.emotion_categories`. Returns `nil` if any hex fails to parse —
/// callers treat the palette as unavailable and fall back to
/// `Color.accent.primary` / `Color.accent.primaryHex`.
struct EmotionPalette: Equatable {
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    let primaryHex: String
    let secondaryHex: String
    let lightHex: String
    let surfaceHex: String

    init?(
        primaryHex: String,
        secondaryHex: String,
        lightHex: String,
        surfaceHex: String
    ) {
        guard
            let primary = Color(hex: primaryHex),
            let secondary = Color(hex: secondaryHex),
            let light = Color(hex: lightHex),
            let surface = Color(hex: surfaceHex)
        else {
            return nil
        }
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.primaryHex = primaryHex
        self.secondaryHex = secondaryHex
        self.lightHex = lightHex
        self.surfaceHex = surfaceHex
    }

    var accentBackground: Color { primary }
    var accentForeground: Color { light }

    func stroke(for scheme: ColorScheme) -> Color {
        scheme == .dark ? secondary : primary
    }

    /// 6-digit `#RRGGBB` for SVG-text injection. The 8-digit form stored in
    /// the DB doesn't render correctly when SVGView parses it inline; stroke
    /// colors are always opaque (alpha = FF), so dropping the alpha bytes is
    /// lossless for this use case.
    func strokeHex(for scheme: ColorScheme) -> String {
        let hex = scheme == .dark ? secondaryHex : primaryHex
        // "#RRGGBBAA" → "#RRGGBB". Defensive: only trim when the input is
        // exactly 9 chars including the leading '#'.
        return hex.count == 9 ? String(hex.prefix(7)) : hex
    }
}

/// Pair of hex strings handed to the pebble render stack: `strokeHex`
/// is consumed by `PebbleRenderView` (replaces `currentColor` in the
/// composed SVG); `fillHex` is consumed by `PebbleOutlineBackdropView`
/// (replaces the `#FF00FF` sentinel in the outline SVG).
struct PebbleFrameColors: Equatable {
    let strokeHex: String
    let fillHex: String
}

extension EmotionPalette {

    /// Single source of truth for the intensity → role mapping.
    /// - intensity 3 (large): `light` stroke + `primary` fill (opaque body).
    /// - intensity 1 / 2:     `secondary` stroke + `surface` fill (alpha body).
    func pebbleFrameColors(forIntensity intensity: Int) -> PebbleFrameColors {
        switch intensity {
        case 3:
            return PebbleFrameColors(strokeHex: lightHex,     fillHex: primaryHex)
        default:
            return PebbleFrameColors(strokeHex: secondaryHex, fillHex: surfaceHex)
        }
    }
}
