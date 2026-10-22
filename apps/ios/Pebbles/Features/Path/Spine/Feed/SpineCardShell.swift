import SwiftUI

/// The card's body without its print (#1146 §Component tree, moved out of
/// `SpineCard`): the stock on its 11 pt radius, the painted edge (2 / 3.5 /
/// 5.5 pt in the edge colour) and the rule set's shadow, on the ground only,
/// so no text casts one. The Spine wraps one `SpineCardFace`; the held card
/// wraps a turning face and its still footer.
struct SpineCardShell<Content: View>: View {
    /// The emotion's palette; nil for a bare moment or an unknown emotion, which
    /// paint no edge (Android's `SpineCardShell`).
    let emotion: EmotionPalette?
    let polarity: ValencePolarity
    let intensity: Int
    @ViewBuilder let content: () -> Content

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        content().background { ground }
    }

    /// Android's `SpineCardShell` order, bottom up: the shadow, the Night ring, the
    /// painted edge (2 / 3.5 / 5.5 pt, none without an emotion), the stock. On the
    /// ground only, so no text casts a shadow. Each shadow layer is the card's shape
    /// in `shadowInk` at its own alpha, inset by its negative spread (SwiftUI's
    /// `.shadow` has no spread), dropped by `dy` and blurred at the CSS σ = blur / 2,
    /// so the pool sits under the card instead of haloing its sides.
    private var ground: some View {
        let shape = RoundedRectangle(cornerRadius: 11, style: .continuous)
        let ink = SpineColors.shadowInk(scheme: scheme)
        let edge = SpineColors.edge(emotion, polarity: polarity, scheme: scheme)
        return ZStack {
            ForEach(Array(SpineColors.cardShadow(scheme: scheme).enumerated()), id: \.offset) { _, layer in
                shape.fill(ink.opacity(layer.alpha))
                    .padding(-layer.spread)
                    .offset(y: layer.dy)
                    .blur(radius: layer.blur / 2)
            }
            if let ring = SpineColors.nightRing(scheme: scheme) {
                let half = SpineColors.nightRingWidth / 2
                RoundedRectangle(cornerRadius: 11 + half, style: .continuous)
                    .stroke(ring, lineWidth: SpineColors.nightRingWidth)
                    .padding(-half)
            }
            if let edge {
                shape.fill(edge).offset(y: CardSizes.edge(intensity))
            }
            shape.fill(Color.pebbles.stock)
        }
        .accessibilityHidden(true)
    }
}
