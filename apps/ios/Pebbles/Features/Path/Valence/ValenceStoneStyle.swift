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

    static func style(for polarity: ValencePolarity, scheme: ColorScheme) -> ValenceStoneStyle {
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

    /// The mesh only exists here. There is no rainbow token in the design
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
                    colors: [
                        .purple, .indigo, .purple, .yellow,
                        .pink, .purple, .pink, .yellow,
                        .orange, .pink, .yellow, .orange,
                        .yellow, .orange, .pink, .purple
                    ]
                )
            )
        } else {
            // iOS 17 has no MeshGradient. The same hues in the same corner
            // order, so a 17 device gets a coherent stone rather than a
            // different-looking one. Used for fill *and* stroke, never mixed
            // with the mesh.
            return AnyShapeStyle(
                LinearGradient(
                    colors: [.purple, .pink, .orange, .yellow],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
        }
    }
}
