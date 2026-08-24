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

    /// The highlight material, sampled from the reference pastel gradient at
    /// each of the mesh's own control points (patch-averaged, so no single
    /// noisy pixel decides a corner). Cyan and gold across the top, lavender
    /// and rose through the middle, mint along the bottom.
    private static let washHexes = [
        "#CCF3F9", "#EEF597", "#FEE9B4", "#FFE1E0",
        "#E3E5FE", "#F1DEFF", "#FFE5C2", "#FFDFEE",
        "#E3E4FF", "#DFE9FF", "#FFDFE0", "#F9E0F5",
        "#D9EAFE", "#D2EFFF", "#C4FFDC", "#CAFFD4"
    ]

    /// The same gradient as ink: each sample keeps its hue, loses most of its
    /// saturation and drops to a readable luminance. The pastels are far too
    /// light to draw the artwork with — a stone inked in them disappears
    /// against the page — so the wash fills and this twin draws.
    ///
    /// Muted hard on purpose. Carrying the wash's saturation down into the ink
    /// turns the artwork and the headline word into a rainbow, which is the
    /// look this gradient replaced.
    private static let inkHexes = [
        "#5B888F", "#8C905A", "#958355", "#965754",
        "#575B94", "#7A5496", "#967A54", "#965473",
        "#545796", "#546996", "#965457", "#8B5F84",
        "#567394", "#547F96", "#54966F", "#549661"
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
