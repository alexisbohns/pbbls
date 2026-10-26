import SwiftUI

/// A card's paper: the week tiles' radius and rule on the page's paper, inset in its page.
struct MonthDeckPaper: View {
    var body: some View {
        let shape = RoundedRectangle(cornerRadius: SpineLayout.tileCorner, style: .continuous)
        shape
            .fill(Color.pebbles.surface)
            .overlay {
                shape
                    .inset(by: SpineLayout.rule / 2)
                    .stroke(Color.pebbles.rule, lineWidth: SpineLayout.rule)
            }
            .padding(MonthDeckMotion.inset)
    }
}

/// One card on its paper: the margin ruled off on the left, the title band,
/// and the content top-aligned under it, scaled down to fit when it is taller
/// than the room left (the larger text sizes, small phones).
struct MonthDeckCard<Content: View>: View {
    let month: String
    let step: MonthDeckStep?
    let head: MonthHead?
    @ViewBuilder let content: Content

    /// The content's own height, and the card's.
    @State private var ideal: CGFloat = 0
    @State private var room: CGFloat = 0

    var body: some View {
        ZStack(alignment: .top) {
            MonthDeckPaper()
            HStack(spacing: 0) {
                MonthDeckMargin(month: month, step: step)
                Rectangle()
                    .fill(Color.pebbles.rule)
                    .frame(width: SpineLayout.rule)
                VStack(spacing: 0) {
                    if let head { MonthDeckTitle(head: head) }
                    // The room is what the title leaves, whatever the content's height:
                    // an overlay never grows its base.
                    Color.clear
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { room = $0 }
                        .overlay(alignment: .top) {
                            content
                                .fixedSize(horizontal: false, vertical: true)
                                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { ideal = $0 }
                                .scaleEffect(fit, anchor: .top)
                        }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: SpineLayout.tileCorner, style: .continuous))
            .padding(MonthDeckMotion.inset)
        }
    }

    private var fit: CGFloat {
        ideal > room && room > 0 ? room / ideal : 1
    }
}

/// A card's chrome (#1241 D11), in points: the exploration's galley.
enum MonthDeckLayout {
    /// The margin, left of its rule.
    static let margin: CGFloat = 28
    /// Above the title band, and above the margin's month.
    static let titleTop: CGFloat = 22
    /// Between the title and its rule.
    static let titleRuleGap: CGFloat = 10
    /// Between the rule and the content.
    static let titleBottom: CGFloat = 12
    /// Under the stepper.
    static let marginFoot: CGFloat = 18
    /// A stepper segment: its length down the margin, its thickness, and the gap to the next.
    static let segment: CGFloat = 10
    static let segmentThickness: CGFloat = 3
    static let segmentGap: CGFloat = 3
}

/// A card's margin (#1241 D11): the month set upright at the top, reading
/// upward, and the stepper at the foot. Decorative: the deck never deals under
/// VoiceOver, and the title band names the card.
private struct MonthDeckMargin: View {
    let month: String
    let step: MonthDeckStep?

    var body: some View {
        VStack(spacing: 0) {
            MonthDeckUpright {
                Text(verbatim: month)
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.outline)
                    .lineLimit(1)
                    // The margin keeps its width.
                    .dynamicTypeSize(...DynamicTypeSize.large)
                    .rotationEffect(.degrees(-90))
            }
            Spacer(minLength: MonthDeckLayout.titleBottom)
            if let step { MonthDeckStepper(step: step) }
        }
        .padding(.top, MonthDeckLayout.titleTop)
        .padding(.bottom, MonthDeckLayout.marginFoot)
        .frame(width: MonthDeckLayout.margin)
        .frame(maxHeight: .infinity)
        .accessibilityHidden(true)
    }
}

/// Its one subview, turned a quarter, laid out as turned: its width the
/// subview's height, its height the subview's width.
private struct MonthDeckUpright: Layout {
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let size = subviews.first?.sizeThatFits(.unspecified) else { return .zero }
        return CGSize(width: size.height, height: size.width)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center, proposal: .unspecified)
    }
}

/// The deck's stepper (#1241 D11): a segment per card down the margin's foot,
/// filled to this one, the cover's at the top.
private struct MonthDeckStepper: View {
    let step: MonthDeckStep

    var body: some View {
        VStack(spacing: MonthDeckLayout.segmentGap) {
            ForEach(0..<step.count, id: \.self) { segment in
                Capsule()
                    .fill(step.isFilled(segment) ? Color.pebbles.onSurfaceVariant : Color.pebbles.outlineVariant)
                    .frame(width: MonthDeckLayout.segmentThickness, height: MonthDeckLayout.segment)
            }
        }
    }
}

/// A card's title band (#1241 D11): its head's label, the hint at the far end
/// in italics, and a rule under them, at the sections' measure.
private struct MonthDeckTitle: View {
    let head: MonthHead

    var body: some View {
        VStack(alignment: .leading, spacing: MonthDeckLayout.titleRuleGap) {
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text(verbatim: head.label)
                    .spineType(.sectionLabel)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .layoutPriority(1)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 12)
                if let hint = head.hint {
                    Text(verbatim: hint)
                        .spineType(.sectionHint)
                        .foregroundStyle(Color.pebbles.outline)
                        .multilineTextAlignment(.trailing)
                }
            }
            Rectangle()
                .fill(Color.pebbles.rule)
                .frame(height: SpineLayout.rule)
        }
        .padding(.horizontal, MonthLayout.sectionPadX)
        .padding(.top, MonthDeckLayout.titleTop)
        .padding(.bottom, MonthDeckLayout.titleBottom)
    }
}
