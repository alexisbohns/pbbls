import SwiftUI

/// The card's shadow (#1382 D7), as constants a test can read: two layers in
/// `SpineColors.shadowInk` on Paper, one at Night (where a `rule` hairline
/// draws the edge). `dy` drops a layer, `blur` is the CSS blur (σ = blur / 2).
enum PebblesCardShadow {
    struct Layer: Equatable {
        let dy: CGFloat
        let blur: CGFloat
        let alpha: Double
    }

    static let paper = [Layer(dy: 1, blur: 2, alpha: 0.05), Layer(dy: 8, blur: 22, alpha: 0.07)]
    static let night = [Layer(dy: 8, blur: 22, alpha: 0.35)]
    static let hairline: CGFloat = 1

    static func layers(_ scheme: ColorScheme) -> [Layer] {
        scheme == .dark ? night : paper
    }
}

extension View {
    /// A card (#1382 D7): content you browse (a month wrap, a badge, the Lab's
    /// announcement) on `stock`, an 18 pt continuous radius, with a soft shadow on
    /// Paper and a hairline at Night. The content is clipped to the card; the
    /// shadow, drawn under the ground only, is not, so leave room below a row of cards.
    func pebblesCard(radius: CGFloat = 18) -> some View {
        modifier(PebblesCardModifier(radius: radius))
    }

    /// The box (#1382 D7): one list on `surfaceContainer`, a 26 pt continuous
    /// radius, padded 24 / 18 / 10 / 16. Home's first five is its one user.
    func pebblesBox() -> some View {
        padding(EdgeInsets(top: 24, leading: 18, bottom: 10, trailing: 16))
            .background(Color.pebbles.surfaceContainer, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    }
}

private struct PebblesCardModifier: ViewModifier {
    let radius: CGFloat
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        content
            .clipShape(shape)
            .background { ground(shape) }
    }

    /// Built as `SpineCardShell` builds its pool: the shape in `shadowInk` at each
    /// layer's alpha, dropped and blurred; the Night hairline; then the stock.
    private func ground(_ shape: RoundedRectangle) -> some View {
        let ink = SpineColors.shadowInk(scheme: scheme)
        return ZStack {
            ForEach(Array(PebblesCardShadow.layers(scheme).enumerated()), id: \.offset) { _, layer in
                shape.fill(ink.opacity(layer.alpha))
                    .offset(y: layer.dy)
                    .blur(radius: layer.blur / 2)
            }
            if scheme == .dark {
                let half = PebblesCardShadow.hairline / 2
                RoundedRectangle(cornerRadius: radius + half, style: .continuous)
                    .stroke(Color.pebbles.rule, lineWidth: PebblesCardShadow.hairline)
                    .padding(-half)
            }
            shape.fill(Color.pebbles.stock)
        }
        .accessibilityHidden(true)
    }
}
