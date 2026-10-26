import SwiftUI

/// A card's paper: the pebble cards' own, inset in its page. Their shell
/// without an emotion, so no painted edge: the stock, its shadow, the Night ring.
struct MonthDeckPaper: View {
    var body: some View {
        SpineCardShell(emotion: nil, polarity: .neutral, intensity: 1) { Color.clear }
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

    /// The content's height as laid out (its own, or the room when it fits), and the card's.
    @State private var ideal: CGFloat = 0
    @State private var room: CGFloat = 0

    var body: some View {
        ZStack(alignment: .top) {
            MonthDeckPaper()
            HStack(spacing: 0) {
                MonthDeckMargin(month: month, step: step)
                Rectangle()
                    .fill(Color.pebbles.cardRule)
                    .frame(width: SpineLayout.rule)
                VStack(spacing: 0) {
                    if let head { MonthDeckTitle(head: head) }
                    // The room is what the title leaves, whatever the content's height:
                    // an overlay never grows its base.
                    Color.clear
                        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { room = $0 }
                        .overlay(alignment: .top) {
                            MonthCardFill(room: room) {
                                content.environment(\.monthCardRoom, room > 0 ? room : nil)
                            }
                            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { ideal = $0 }
                            .scaleEffect(fit, anchor: .top)
                        }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: MonthDeckLayout.corner, style: .continuous))
            .padding(MonthDeckMotion.inset)
        }
    }

    private var fit: CGFloat {
        ideal > room && room > 0 ? room / ideal : 1
    }
}

/// Lays a card's content out in the room when it fits, so its spacers open
/// and a section's *more* stands at the card's foot; past the room, at its own
/// height, for the card to scale it down. Its height is the larger of the two.
struct MonthCardFill: Layout {
    let room: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let own = subviews.first?.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil)) ?? .zero
        return CGSize(width: proposal.width ?? own.width, height: max(own.height, room))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: bounds.origin, anchor: .topLeading,
                              proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
    }
}

/// A card's chrome (#1241 D11), in points: the exploration's galley.
enum MonthDeckLayout {
    /// The paper's corner: the pebble cards' (`SpineCardShell`).
    static let corner: CGFloat = 11
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
    /// Between the end card's month and its replay.
    static let endGap: CGFloat = 20
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
                .fill(Color.pebbles.cardRule)
                .frame(height: SpineLayout.rule)
        }
        .padding(.horizontal, MonthLayout.sectionPadX)
        .padding(.top, MonthDeckLayout.titleTop)
        .padding(.bottom, MonthDeckLayout.titleBottom)
    }
}

/// The deck's last card (#1241 D14), the back of the deck: the month's name and
/// a button that replays the month from the cover. Centred in the card's room.
struct MonthDeckEnd: View {
    let month: CalendarMonth
    let onReplay: () -> Void

    @Environment(\.monthCardRoom) private var room

    var body: some View {
        let locale = SpineStrings.appLocale
        VStack(spacing: MonthDeckLayout.endGap) {
            Text(verbatim: WrapCopy.monthName(month, locale: locale))
                .spineType(.monthName)
                .foregroundStyle(Color.pebbles.onSurface)
                .dynamicTypeSize(...DynamicTypeSize.large)
            Button(action: onReplay) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.counterclockwise")
                        .imageScale(.small)
                        .accessibilityHidden(true)
                    Text(verbatim: WrapCopy.replayMonth(locale: locale))
                }
                .spineType(.personsFold)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .padding(.horizontal, 18)
                .frame(minHeight: SpineLayout.minTarget)
                .overlay { Capsule().strokeBorder(Color.pebbles.cardRule, lineWidth: SpineLayout.rule) }
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .introReveal(.replay)
        }
        .frame(maxWidth: .infinity, minHeight: room ?? 0)
        .introSequence(.end) { IntroScore.end() }
    }
}
