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
                backdrop: AnyShapeStyle(highlightGradient.opacity(highlightFillOpacity(scheme))),
                ink: AnyShapeStyle(highlightGradient)
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
            return ValenceStoneStyle(
                backdrop: AnyShapeStyle(highlightGradient),
                ink: AnyShapeStyle(Color.accent.light)
            )
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
        case .highlight: return AnyShapeStyle(highlightGradient)
        }
    }

    /// Soft enough that the page background reads through the stone. The wash
    /// composites against the background, so the same alpha that reads as a
    /// pastel over the light background reads as mud over the dark one — dark
    /// gets more of the gradient, not less.
    private static func highlightFillOpacity(_ scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.5 : 0.25
    }

    /// The three hues the highlight mesh is built from: the `secondary_color`
    /// of the Joy, Pride and Peaceful emotion categories.
    ///
    /// Copied rather than read from `EmotionPaletteService`, which needs the
    /// network and is not loaded when this view first draws — and the gradient
    /// is decoration, not a reading of anyone's palette. The cost is that a
    /// re-design of those categories will not reach here: whoever changes them
    /// should re-check this mesh.
    private static let joy = Color(hex: "#CF8C39") ?? .orange
    private static let pride = Color(hex: "#EA91CE") ?? .pink
    private static let peace = Color(hex: "#80BF96") ?? .green

    /// The mesh only exists here. There is no gradient token in the design
    /// system, and this change deliberately does not create one — promote it
    /// when a second surface needs the same colours.
    private static var highlightGradient: AnyShapeStyle {
        if #available(iOS 18, *) {
            return AnyShapeStyle(
                MeshGradient(
                    width: 4,
                    height: 4,
                    points: [
                        [0.0, 0.0], [0.3, 0.0], [0.7, 0.0], [1.0, 0.0],
                        [0.0, 0.3], [0.2, 0.4], [0.7, 0.2], [1.0, 0.3],
                        [0.0, 0.7], [0.3, 0.8], [0.7, 0.6], [1.0, 0.7],
                        [0.0, 1.0], [0.3, 1.0], [0.7, 1.0], [1.0, 1.0]
                    ],
                    // Peace at the top left, Joy down the right, Pride
                    // rising from the bottom — a stone catching light rather
                    // than a filter over it.
                    colors: [
                        peace, peace, joy, joy,
                        peace, peace, joy, joy,
                        pride, pride, joy, joy,
                        pride, pride, pride, joy
                    ]
                )
            )
        } else {
            // iOS 17 has no MeshGradient. The same three hues in the same
            // corner order, so a 17 device gets a coherent stone rather than a
            // different-looking one.
            return AnyShapeStyle(
                LinearGradient(
                    colors: [peace, joy, pride],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        }
    }
}
