import SwiftUI

/// The six colors of an emotion category palette, plus role-named accessors
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
/// Initialized from the six 8-digit hex strings stored on
/// `public.emotion_categories`. Returns `nil` if any hex fails to parse —
/// callers treat the palette as unavailable and fall back to
/// `Color.accent.primary` / `Color.accent.primaryHex`.
struct EmotionPalette: Equatable {
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color
    let shaded: Color
    let dark: Color

    let primaryHex: String
    let secondaryHex: String
    let lightHex: String
    let surfaceHex: String
    let shadedHex: String
    let darkHex: String

    init?(
        primaryHex: String,
        secondaryHex: String,
        lightHex: String,
        surfaceHex: String,
        shadedHex: String,
        darkHex: String
    ) {
        // The palette hex columns on `emotion_categories` are populated by
        // hand in Supabase Studio and can carry stray surrounding whitespace.
        // Trim at the model boundary so the stored `*Hex` strings are clean
        // `#RRGGBBAA`: the SVG-injection helpers (`rgbHex` / `alphaComponent`
        // / `strokeHex`) gate on `count == 9`, which silently no-ops on a
        // padded string and would leak 8-digit hex into the render stack.
        let primaryHex = primaryHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let secondaryHex = secondaryHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let lightHex = lightHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let surfaceHex = surfaceHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let shadedHex = shadedHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let darkHex = darkHex.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            let primary = Color(hex: primaryHex),
            let secondary = Color(hex: secondaryHex),
            let light = Color(hex: lightHex),
            let surface = Color(hex: surfaceHex),
            let shaded = Color(hex: shadedHex),
            let dark = Color(hex: darkHex)
        else {
            return nil
        }
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.shaded = shaded
        self.dark = dark
        self.primaryHex = primaryHex
        self.secondaryHex = secondaryHex
        self.lightHex = lightHex
        self.surfaceHex = surfaceHex
        self.shadedHex = shadedHex
        self.darkHex = darkHex
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
    /// `PebbleOutlineBackdropView`. Value depends on the producer:
    /// - `pebbleFrameColors` (timeline row): large → opaque `primary` (1.0);
    ///   small/medium → `surface`, which the palette seeds at ~10% alpha for a
    ///   faint silhouette wash.
    /// - `petroglyphColors` (pebble page, #599): large → opaque `primary`;
    ///   small/medium → opaque `light`/`dark`, so the backfill reads as a solid
    ///   tint rather than a wash.
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

    /// Theme-aware petroglyph colors for the pebble **page** (issue #599).
    /// "Stroke" = outline + glyph paths (one color); "backfill" = the
    /// silhouette fill behind them. Distinct from `pebbleFrameColors`, which
    /// keeps the older theme-neutral mapping for the timeline row.
    ///
    /// | intensity      | stroke (light / dark) | backfill (light / dark) |
    /// |----------------|-----------------------|-------------------------|
    /// | small, medium  | primary / secondary   | light / dark            |
    /// | large (3)      | light / light         | primary / primary       |
    func petroglyphColors(forIntensity intensity: Int, scheme: ColorScheme) -> PebbleFrameColors {
        let isDark = scheme == .dark
        switch intensity {
        case 3:
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(lightHex),
                fillHex:     EmotionPalette.rgbHex(primaryHex),
                fillOpacity: EmotionPalette.alphaComponent(primaryHex)
            )
        default:
            let strokeSource = isDark ? secondaryHex : primaryHex
            let fillSource   = isDark ? darkHex : lightHex
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(strokeSource),
                fillHex:     EmotionPalette.rgbHex(fillSource),
                fillOpacity: EmotionPalette.alphaComponent(fillSource)
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
