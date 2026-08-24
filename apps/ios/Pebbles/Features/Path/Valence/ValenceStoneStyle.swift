import SwiftUI

/// Backdrop wash and ink for one polarity of valence stone.
///
/// Mirrors the roles `EmotionPalette.pebbleFrameColors` hands a real pebble:
/// the backdrop is a soft silhouette *behind* the artwork, and the ink is what
/// the artwork's lines are drawn in. Highlight is the interesting one: backdrop
/// and ink are the *same* gradient at two intensities, so the lines read as the
/// vivid edge of a soft wash rather than as a separate colour.
struct ValenceStoneStyle {
    /// Fills the silhouette behind the artwork. Never stroked — the outline a
    /// stone reads as belongs to the ink, not to the backdrop.
    let backdrop: AnyShapeStyle
    /// Tints the pebble artwork drawn inside the backdrop.
    let ink: AnyShapeStyle

    /// Selection inverts the two roles: the wash becomes the solid and the ink
    /// goes pale, so the chosen stone reads as filled in rather than merely
    /// less faded than its neighbours. It is the same treatment
    /// `EmotionPalette.pebbleFrameColors(forIntensity: 3)` gives a hero pebble
    /// on the Path — a `light` stroke over an opaque `primary` fill.
    static func style(
        for polarity: ValencePolarity,
        scheme: ColorScheme,
        isSelected: Bool = false
    ) -> ValenceStoneStyle {
        if isSelected { return selected(for: polarity) }
        switch polarity {
        case .lowlight:
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(Color.system.muted),
                ink: AnyShapeStyle(Color.system.secondary)
            )
        case .neutral:
            // `AccentSurface` already carries a low alpha, so it lands as a
            // wash behind the opaque `AccentPrimary` artwork.
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(Color.accent.surface),
                ink: AnyShapeStyle(Color.accent.primary)
            )
        case .highlight:
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(highlightWash.opacity(highlightFillOpacity(scheme))),
                ink: highlightInk
            )
        }
    }

    private static func selected(for polarity: ValencePolarity) -> ValenceStoneStyle {
        switch polarity {
        case .lowlight:
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(Color.system.secondary),
                ink: AnyShapeStyle(Color.system.background)
            )
        case .neutral:
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(Color.accent.primary),
                ink: AnyShapeStyle(Color.accent.light)
            )
        case .highlight:
            // The one polarity that does not swap its roles: a pastel fill
            // cannot carry pale ink. It reads as chosen by going fully opaque
            // while its neighbours sit at 0.45.
            return ValenceStoneStyle(backdrop: highlightWash, ink: highlightInk)
        }
    }

    /// Fill for the headline word naming the picked valence. Highlight carries
    /// the same mesh its stone does, so the word and the stone read as one
    /// thing. Lowlight goes darker than its stone ink: at headline size a grey
    /// word looks disabled rather than quiet.
    static func headlineInk(for polarity: ValencePolarity) -> AnyShapeStyle {
        switch polarity {
        case .lowlight:  return AnyShapeStyle(Color.system.foreground)
        case .neutral:   return AnyShapeStyle(Color.accent.primary)
        case .highlight: return highlightInk
        }
    }

    /// Soft enough that the page background reads through the stone. The wash
    /// composites against the background, so the same alpha that reads as a
    /// pastel over the light background reads as mud over the dark one — dark
    /// gets more of the gradient, not less.
    private static func highlightFillOpacity(_ scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.7 : 0.35
    }

    // MARK: - The highlight gradient

    /// The highlight material, sampled from the reference gradient at each of
    /// the mesh's own control points (patch-averaged, so no single noisy pixel
    /// decides a corner). Rose at the top left, blush across the top right,
    /// gold rising from the bottom left.
    ///
    /// Every sample lands between hue 3° and 41° — the whole thing is warm.
    /// That is what makes the ink below possible: a gradient this narrow can be
    /// saturated without becoming a rainbow, which a full-hue-wheel one cannot.
    private static let washHexes = [
        "#E7928B", "#FBA78F", "#FED6C9", "#FDC0B5",
        "#EAA68F", "#F5B592", "#FDC9B6", "#FCAA9F",
        "#EFC094", "#F8CF97", "#FBB493", "#F3968C",
        "#F1CC95", "#FADB9A", "#F1B192", "#E6908B"
    ]

    /// The same gradient as ink: each sample keeps its hue and takes a fixed
    /// saturation and lightness (HSL 0.90 / 0.52), which runs gold through
    /// orange to coral. The wash is far too light to draw the artwork with — a
    /// stone inked in it disappears against the page — so the wash fills and
    /// this twin draws.
    ///
    /// Saturated on purpose, and only affordable because the hue range is
    /// narrow. Earlier versions of this gradient spanned the wheel, where the
    /// same saturation read as a clown's palette and the alternative (darkening
    /// instead) read as mud.
    private static let inkHexes = [
        "#F32716", "#F34716", "#F34C16", "#F33816",
        "#F34E16", "#F36416", "#F35116", "#F33016",
        "#F38116", "#F39616", "#F35C16", "#F32C16",
        "#F39A16", "#F3AC16", "#F35E16", "#F32316"
    ]

    private static let meshPoints: [SIMD2<Float>] = [
        [0.0, 0.0], [0.3, 0.0], [0.7, 0.0], [1.0, 0.0],
        [0.0, 0.3], [0.2, 0.4], [0.7, 0.2], [1.0, 0.3],
        [0.0, 0.7], [0.3, 0.8], [0.7, 0.6], [1.0, 0.7],
        [0.0, 1.0], [0.3, 1.0], [0.7, 1.0], [1.0, 1.0]
    ]

    private static let highlightWash = gradient(from: washHexes)
    private static let highlightInk = gradient(from: inkHexes)

    /// A mesh on iOS 18, and below that a linear run through the same four
    /// diagonal samples — the same colours in the same corner order, so a 17
    /// device gets a coherent stone rather than a different-looking one.
    ///
    /// There is no gradient token in the design system, and this deliberately
    /// does not create one: promote it when a second surface needs it.
    private static func gradient(from hexes: [String]) -> AnyShapeStyle {
        let colors = hexes.map { Color(hex: $0) ?? .clear }
        if #available(iOS 18, *) {
            return AnyShapeStyle(
                MeshGradient(width: 4, height: 4, points: meshPoints, colors: colors)
            )
        }
        return AnyShapeStyle(
            LinearGradient(
                colors: [colors[0], colors[5], colors[10], colors[15]],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
    }
}
