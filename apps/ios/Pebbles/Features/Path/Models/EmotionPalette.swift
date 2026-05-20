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

/// Render-ready colors handed to the pebble render stack.
///
/// All values are sanitized for direct SVG injection: `strokeHex` and
/// `fillHex` are 6-digit `#RRGGBB` (SVGView misparses 8-digit hex), and
/// the fill color's alpha is carried separately in `fillOpacity` because
/// it cannot ride along in a 6-digit hex.
struct PebbleFrameColors: Equatable {
    /// 6-digit `#RRGGBB`. Consumed by `PebbleRenderView` (replaces
    /// `currentColor` in the composed pebble SVG).
    let strokeHex: String
    /// 6-digit `#RRGGBB`. Consumed by `PebbleOutlineBackdropView`
    /// (replaces the `#FF00FF` sentinel in the outline SVG).
    let fillHex: String
    /// The fill color's alpha (0...1), applied as view opacity by
    /// `PebbleOutlineBackdropView`. Large pebbles fill with opaque
    /// `primary` (1.0); small/medium fill with `surface`, which the
    /// palette seeds at ~10% alpha for a faint silhouette wash.
    let fillOpacity: Double
}

extension EmotionPalette {

    /// Single source of truth for the intensity → role mapping.
    /// - intensity 3 (large): `light` stroke + opaque `primary` fill.
    /// - intensity 1 / 2:     `secondary` stroke + `surface` fill (the
    ///   surface color carries a low alpha, so the silhouette reads as
    ///   a faint wash rather than a solid block).
    func pebbleFrameColors(forIntensity intensity: Int) -> PebbleFrameColors {
        switch intensity {
        case 3:
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(lightHex),
                fillHex:     EmotionPalette.rgbHex(primaryHex),
                fillOpacity: EmotionPalette.alphaComponent(primaryHex)
            )
        default:
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(secondaryHex),
                fillHex:     EmotionPalette.rgbHex(surfaceHex),
                fillOpacity: EmotionPalette.alphaComponent(surfaceHex)
            )
        }
    }

    /// `#RRGGBBAA` → `#RRGGBB`. 6-digit and unrecognized input pass
    /// through unchanged. SVGView only renders 6-digit hex reliably, so
    /// every hex injected into SVG markup must go through this first.
    private static func rgbHex(_ hex: String) -> String {
        hex.count == 9 ? String(hex.prefix(7)) : hex
    }

    /// Alpha byte of an `#RRGGBBAA` string as a 0...1 Double. Returns 1
    /// for 6-digit hex or any input that fails to parse.
    private static func alphaComponent(_ hex: String) -> Double {
        guard hex.count == 9, let byte = UInt8(hex.suffix(2), radix: 16) else {
            return 1
        }
        return Double(byte) / 255
    }
}
