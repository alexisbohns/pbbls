import SwiftUI

/// What the print reads its geometry from, in one layout pass (anchors, not
/// measured state, so it also holds under `ImageRenderer`): the bare page's
/// place and dashed stone, and the verso's stone.
enum PrintAnchor: Hashable {
    case place, ghost, versoStone
}

struct PrintAnchors: PreferenceKey {
    static let defaultValue: [PrintAnchor: Anchor<CGRect>] = [:]

    static func reduce(value: inout [PrintAnchor: Anchor<CGRect>], nextValue: () -> [PrintAnchor: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

/// The completion (#1146 §The print, D6): over the bare page, the card appears
/// verso up at the place, fed out of it top first (0–0.35); the dashed stone
/// flies to the verso's clearing and the lit stone presses in (0.25–0.6, the
/// press at 0.45); the lockup, the postmarks, the band and the footer print in
/// turn; then the page fades and steps back to 0.97 while the card rises to its
/// rest at the page's top (0.6–1). A pure function of `t`: the caller's
/// `TimelineView` drives it, and the frame that reaches 1 ends it. The place,
/// its dashed stone and the verso's stone are read as anchors in the same pass.
/// Under a crossfade the page fades out and the card in at rest.
///
/// The place is as wide as the card (both `CardLayout.cardWidth`), so the card
/// prints at its own width.
struct CardPrint<Card: View>: View {
    let t: Double
    let crossfade: Bool
    let page: BareMomentPage
    let card: Card

    var body: some View {
        let rise = CompletionMotion.rise(t)
        page
            .opacity(crossfade ? 1 - t : CompletionMotion.pageAlpha(rise))
            .scaleEffect(crossfade ? 1 : 1 - (1 - CompletionMotion.pageScaleEnd) * CompletionMotion.ease(rise),
                         anchor: .top)
            // Nothing on the page is pressed or read under the card.
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .overlayPreferenceValue(PrintAnchors.self) { anchors in
                GeometryReader { proxy in
                    let place = anchors[.place].map { proxy[$0] }
                    let ghost = anchors[.ghost].map { proxy[$0] }
                    printed(place: place, ghost: ghost, width: proxy.size.width)
                }
            }
    }

    @ViewBuilder
    private func printed(place: CGRect?, ghost: CGRect?, width: CGFloat) -> some View {
        let print = crossfade ? 1 : CompletionMotion.print(t)
        let rise = crossfade ? 1 : CompletionMotion.rise(t)
        // From the place's top to the card's rest at the page's top.
        let top = (place?.minY ?? 0) * (1 - CompletionMotion.ease(rise))
        card
            .overlayPreferenceValue(PrintAnchors.self) { anchors in
                GeometryReader { proxy in
                    if !crossfade, let ghost, let stone = anchors[.versoStone].map({ proxy[$0] }) {
                        // Into the card's own space: its leading edge is the place's (the card
                        // is as wide as the place, both centred), its top `top` down the page.
                        flyingGhost(from: ghost.offsetBy(dx: -(place?.minX ?? 0), dy: -top), to: stone)
                    }
                }
            }
            .mask(alignment: .top) {
                GeometryReader { proxy in
                    let placeHeight = place?.height ?? 0
                    let fed = placeHeight + (proxy.size.height - placeHeight) * CompletionMotion.ease(print)
                    let revealed = print >= 1 ? proxy.size.height : max(placeHeight, fed)
                    // 32 pt round the card for its shadow and painted edge.
                    Rectangle()
                        .frame(width: proxy.size.width + 64, height: revealed + 32 + 32 * CompletionMotion.ease(print))
                        .offset(x: -32, y: -32)
                }
            }
            .opacity(crossfade ? t : PrintPart.card.alpha(print))
            .frame(maxWidth: .infinity)
            .offset(y: top)
    }

    /// The dashed stone in flight from the page's ghost to the verso's clearing,
    /// both in the card's own space.
    @ViewBuilder
    private func flyingGhost(from: CGRect, to: CGRect) -> some View {
        let stone = CompletionMotion.stone(t)
        if stone > 0, stone < 1, CompletionMotion.ghostAlpha(stone) > 0 {
            let f = CompletionMotion.ease(CompletionMotion.flight(stone))
            let width = from.width + (to.width - from.width) * f
            let centre = CGPoint(x: from.midX + (to.midX - from.midX) * f, y: from.midY + (to.midY - from.midY) * f)
            GhostStone(width: width)
                .opacity(CompletionMotion.ghostAlpha(stone))
                .position(centre)
        }
    }
}
