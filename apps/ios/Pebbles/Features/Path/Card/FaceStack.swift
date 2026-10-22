import SwiftUI

/// Both faces at one width, the area as tall as the taller (#1146 D5): each
/// face measured at the width with a free height, then both placed at the
/// taller height, so turning never changes the card's size and the verso
/// (flexible) fills the height its stone centres in. Neither face is ever
/// removed: the hidden one is laid out at opacity 0 (Android's lesson: an
/// unplaced face loses its display list).
struct FaceStack: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        let height = subviews.map { $0.sizeThatFits(ProposedViewSize(width: width, height: nil)).height }.max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for subview in subviews {
            subview.place(at: bounds.origin, anchor: .topLeading,
                          proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
        }
    }
}
