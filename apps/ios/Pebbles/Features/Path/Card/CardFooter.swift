import SwiftUI

/// The card's still footer (#1146 §Footer), below the border inside the shell,
/// the same on both faces. Left: the privacy glyph, the grade in small caps and
/// the grade's sentence in italic, read as one element ("Secret. Only you can
/// see this one."). Right: the turn, no container, one target of at least
/// 44 pt from the label to the card's edge, never over the privacy sentence.
/// It names the face not in front (`turnTo`), and VoiceOver reaches it last.
struct CardFooter: View {
    let visibility: Visibility
    /// The face the button leads to: the one not in front.
    let turnTo: CardFace
    /// The emotion's spot, for the miniature's stone.
    let stone: Color
    let onTurn: () -> Void

    var body: some View {
        FooterLayout {
            privacy
            turn
        }
    }

    private var privacy: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 8) {
                Image(systemName: visibility.systemImageName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 16, height: 16)
                    .foregroundStyle(Color.pebbles.onSurface)
                Text(visibility.label)
                    .spineType(.cardFooterGrade)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
            // The sentence hangs under the grade, past the glyph.
            Text(visibility.explanation)
                .spineType(.cardFooterNote)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 24)
        }
        .padding(.leading, CardLayout.footerPaddingH)
        .padding(.vertical, CardLayout.footerPaddingV)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(visibility.label) + Text(verbatim: ". ") + Text(visibility.explanation))
    }

    private var turn: some View {
        Button(action: onTurn) {
            HStack(spacing: 8) {
                VStack(alignment: .trailing, spacing: 0) {
                    Text(CardStrings.turn)
                        .spineType(.cardFooterGrade)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(turnTo == .verso ? CardStrings.turnToPebble : CardStrings.turnToMoment)
                        .spineType(.cardTurnTarget)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                FaceMiniature(to: turnTo, stone: stone)
            }
            .padding(.leading, 8)
            .padding(.trailing, CardLayout.footerPaddingH)
            .frame(minHeight: CardLayout.turnTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // The label is the action alone; no hint repeats it (§VoiceOver).
        .accessibilityLabel(Text(turnTo == .verso ? CardStrings.turnActionToPebble : CardStrings.turnActionToMoment))
    }
}

/// The footer's two arrangements (`FooterArrangement`): side by side while the
/// turn takes at most half the width, else the turn under the privacy,
/// trailing-aligned. Both rows are at least the turn target tall.
struct FooterLayout: Layout {
    private struct Sizes {
        var stacked: Bool
        var left: CGSize
        var leftWidth: CGFloat
        var turn: CGSize
        var height: CGFloat
    }

    private func sizes(width: CGFloat, subviews: Subviews) -> Sizes {
        let natural = subviews[1].sizeThatFits(.unspecified)
        let turn = CGSize(width: min(natural.width, width), height: natural.height)
        let stacked = FooterArrangement.stacked(turnWidth: turn.width, available: width)
        let leftWidth = stacked ? width : max(0, width - turn.width - CardLayout.footerGap)
        let left = subviews[0].sizeThatFits(ProposedViewSize(width: leftWidth, height: nil))
        let turnHeight = max(turn.height, CardLayout.turnTarget)
        let height = stacked ? left.height + turnHeight : max(left.height, turnHeight)
        return Sizes(stacked: stacked, left: left, leftWidth: leftWidth, turn: turn, height: height)
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard subviews.count == 2 else { return .zero }
        let width = proposal.width ?? subviews[0].sizeThatFits(.unspecified).width
            + CardLayout.footerGap + subviews[1].sizeThatFits(.unspecified).width
        return CGSize(width: width, height: sizes(width: width, subviews: subviews).height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 2 else { return }
        let sizes = sizes(width: bounds.width, subviews: subviews)
        let leftProposal = ProposedViewSize(width: sizes.leftWidth, height: nil)
        if sizes.stacked {
            subviews[0].place(at: bounds.origin, anchor: .topLeading, proposal: leftProposal)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.minY + sizes.left.height), anchor: .topTrailing,
                              proposal: ProposedViewSize(sizes.turn))
        } else {
            subviews[0].place(at: CGPoint(x: bounds.minX, y: bounds.midY), anchor: .leading, proposal: leftProposal)
            subviews[1].place(at: CGPoint(x: bounds.maxX, y: bounds.midY), anchor: .trailing,
                              proposal: ProposedViewSize(sizes.turn))
        }
    }
}
