import SwiftUI

/// The colors of an emotion category palette, plus role-named accessors
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
/// Initialized from the 8-digit hex strings stored on
/// `public.emotion_categories`. Returns `nil` if any hex fails to parse —
/// callers treat the palette as unavailable and fall back to
/// `Color.accent.primary` / `Color.accent.primaryHex`.
///
/// `dark` is the #599 addition: the small/medium read-view Petroglyph backfill
/// in dark mode (the "palette.dark" role, from the `dark_color` column). The
/// sibling `shaded_color` column also exists on the view but has no iOS
/// consumer yet, so it is intentionally not modelled here.
struct EmotionPalette: Equatable {
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color
    let dark: Color

    let primaryHex: String
    let secondaryHex: String
    let lightHex: String
    let surfaceHex: String
    let darkHex: String

    init?(
        primaryHex: String,
        secondaryHex: String,
        lightHex: String,
        surfaceHex: String,
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
        let darkHex = darkHex.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            let primary = Color(hex: primaryHex),
            let secondary = Color(hex: secondaryHex),
            let light = Color(hex: lightHex),
            let surface = Color(hex: surfaceHex),
            let dark = Color(hex: darkHex)
        else {
            return nil
        }
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.dark = dark
        self.primaryHex = primaryHex
        self.secondaryHex = secondaryHex
        self.lightHex = lightHex
        self.surfaceHex = surfaceHex
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
    /// `PebbleOutlineBackdropView`. Large pebbles fill with opaque
    /// `primary` (1.0); small/medium fill with `surface`, which the
    /// palette seeds at ~10% alpha for a faint silhouette wash.
    let fillOpacity: Double
}

/// Render-ready colors for the read-view Petroglyph (issue #599) — the framed
/// pebble (backfill + outline + glyph) shown as the page heading, or
/// overlapping the snap's top-right. Same shape as `PebbleFrameColors` (all
/// sanitized for SVG injection), but resolved per size group AND color scheme,
/// per the issue table:
///
/// | layer                 | light             | dark                |
/// | --------------------- | ----------------- | ------------------- |
/// | small/medium strokes  | palette.primary   | palette.secondary   |
/// | small/medium backfill | palette.light     | palette.dark        |
/// | large strokes         | palette.light     | palette.light       |
/// | large backfill        | palette.primary   | palette.primary     |
///
/// "palette.dark" is the dedicated `dark_color` column (added for #599), not
/// the faint `surface_color` wash — so the dark-mode backfill is a solid dark
/// tint, mirroring the solid `light` backfill in light mode.
///
/// Deliberately distinct from the theme-neutral `pebbleFrameColors` used by the
/// Path rows (which still washes small/medium with `surface`): #599 scopes the
/// new theme-dependent coloring to the read view.
struct PetroglyphColors: Equatable {
    /// 6-digit `#RRGGBB` for the outline + glyph strokes.
    let strokeHex: String
    /// 6-digit `#RRGGBB` for the backfill silhouette.
    let fillHex: String
    /// The backfill fill color's alpha (0...1), applied as backdrop view opacity.
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

    /// Read-view Petroglyph colors (issue #599) for the given size group and
    /// color scheme — see `PetroglyphColors` for the full role table.
    ///
    /// - large: `light` stroke over an opaque `primary` backfill, both schemes
    ///   (the same hero treatment `pebbleFrameColors(forIntensity: 3)` gives).
    /// - small/medium: theme-dependent — light mode uses a `primary` stroke over
    ///   a solid `light` backfill; dark a `secondary` stroke over a solid `dark`.
    func petroglyphColors(forSize size: ValenceSizeGroup, scheme: ColorScheme) -> PetroglyphColors {
        switch size {
        case .large:
            return PetroglyphColors(
                strokeHex:   EmotionPalette.rgbHex(lightHex),
                fillHex:     EmotionPalette.rgbHex(primaryHex),
                fillOpacity: EmotionPalette.alphaComponent(primaryHex)
            )
        case .small, .medium:
            if scheme == .dark {
                return PetroglyphColors(
                    strokeHex:   EmotionPalette.rgbHex(secondaryHex),
                    fillHex:     EmotionPalette.rgbHex(darkHex),
                    fillOpacity: EmotionPalette.alphaComponent(darkHex)
                )
            } else {
                return PetroglyphColors(
                    strokeHex:   EmotionPalette.rgbHex(primaryHex),
                    fillHex:     EmotionPalette.rgbHex(lightHex),
                    fillOpacity: EmotionPalette.alphaComponent(lightHex)
                )
            }
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
