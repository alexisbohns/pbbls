import SwiftUI

/// The success step's column (#1691): its pieces stacked top to bottom at their rest, centred,
/// each `gap` under the one before. At the pause the name block (`.text`) is dropped under the
/// hero by `textDrop`, and the hero's centre slot (`.centre`, taking no room) sits at
/// `StoneCeremonyLayout.pauseCentreY`. The name block is measured in the same pass, so a tall one
/// raises the hero instead of running under Continue, and a reference renders it without a
/// state round-trip.
///
/// `regionHeight` is the height the page shows in, and `regionTop` how far below its top the
/// column starts. The page does not scroll during the ceremony, so the two stay aligned.
struct StoneCeremonyColumn: Layout {
    enum Role {
        case stacked, text, centre
    }

    struct RoleKey: LayoutValueKey {
        static let defaultValue = Role.stacked
    }

    struct GapKey: LayoutValueKey {
        static let defaultValue: CGFloat = 0
    }

    let regionHeight: CGFloat
    let regionTop: CGFloat
    let hero: CGSize
    let textDrop: Double

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? subviews.map { $0.sizeThatFits(.unspecified).width }.max() ?? 0
        return CGSize(width: width, height: rest(subviews, width: width).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rest = rest(subviews, width: bounds.width)
        let centreY = StoneCeremonyLayout.pauseCentreY(region: regionHeight, hero: hero, textHeight: rest.textHeight)
            - regionTop
        let pauseTextTop = centreY + hero.height / 2 + StoneCeremonyLayout.nameGap
        for (subview, slot) in zip(subviews, rest.slots) {
            guard let slot else {
                subview.place(at: CGPoint(x: bounds.midX, y: bounds.minY + centreY), anchor: .center,
                              proposal: ProposedViewSize(hero))
                continue
            }
            let y = subview[RoleKey.self] == .text ? slot.y + CGFloat(textDrop) * (pauseTextTop - slot.y) : slot.y
            subview.place(at: CGPoint(x: bounds.midX, y: bounds.minY + y), anchor: .top,
                          proposal: ProposedViewSize(width: bounds.width, height: slot.height))
        }
    }

    private struct Slot {
        let y: CGFloat
        let height: CGFloat
    }

    private struct Rest {
        /// One per subview, in order; nil for the centre slot, which takes no room.
        var slots: [Slot?] = []
        var height: CGFloat = 0
        var textHeight: CGFloat = 0
    }

    private func rest(_ subviews: Subviews, width: CGFloat) -> Rest {
        var rest = Rest()
        var y: CGFloat = 0
        var placed = false
        for subview in subviews {
            let role = subview[RoleKey.self]
            guard role != .centre else {
                rest.slots.append(nil)
                continue
            }
            if placed { y += subview[GapKey.self] }
            let height = subview.sizeThatFits(ProposedViewSize(width: width, height: nil)).height
            rest.slots.append(Slot(y: y, height: height))
            if role == .text { rest.textHeight = height }
            y += height
            placed = true
        }
        rest.height = y
        return rest
    }
}

extension View {
    /// This piece's role in `StoneCeremonyColumn`, and its gap under the piece before it.
    func ceremonyColumn(_ role: StoneCeremonyColumn.Role = .stacked, gap: CGFloat = 0) -> some View {
        layoutValue(key: StoneCeremonyColumn.RoleKey.self, value: role)
            .layoutValue(key: StoneCeremonyColumn.GapKey.self, value: gap)
    }
}
