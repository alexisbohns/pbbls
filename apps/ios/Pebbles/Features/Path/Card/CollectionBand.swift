import SwiftUI

/// The pebble's collections as a paper band wrapped across the card's foot
/// (#1146 §Band): full bleed to the shell's edges, two hairlines in the spot
/// inside its long edges and a darker fold at each end where it turns under;
/// the names in italic, each led by its collection's glyph (#1145) or the stack
/// mark, a small diamond between two names on the same line only. No
/// collections, no band (the caller does not add it). One VoiceOver element.
struct CollectionBand: View {
    let collections: [MomentDetail.CollectionRef]
    let glyphs: [UUID: Glyph]
    let spot: Color

    @Environment(\.colorScheme) private var scheme

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        BandFlow(gap: 10, lineGap: 4) {
            ForEach(collections) { collection in
                HStack(spacing: 6) {
                    CollectionMark(glyph: glyphs[collection.id], ink: Color.pebbles.onSurface)
                    Text(verbatim: collection.name)
                        .spineType(.cardBandName)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .multilineTextAlignment(.center)
                }
            }
            ForEach(collections.dropFirst()) { _ in
                // Ideal, not fixed: the flow proposes zero to a knot it does not draw.
                Rectangle()
                    .fill(spot)
                    .frame(idealWidth: 5, maxWidth: 5, idealHeight: 5, maxHeight: 5)
                    .rotationEffect(.degrees(45))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, minHeight: CardLayout.bandMinHeight)
        .background { paper }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(CardStrings.collectionsA11y)
            + Text(verbatim: ": " + collections.map(\.name).formatted(.list(type: .and).locale(locale))))
    }

    private var paper: some View {
        ZStack {
            Color.pebbles.stock
            spot.opacity(CardColors.bandPaperK(scheme: scheme))
        }
        .overlay(alignment: .top) { hairline.padding(.top, 3) }
        .overlay(alignment: .bottom) { hairline.padding(.bottom, 3) }
        .overlay(alignment: .leading) { fold }
        .overlay(alignment: .trailing) { fold }
        .accessibilityHidden(true)
    }

    private var hairline: some View {
        Rectangle().fill(spot.opacity(CardColors.bandHairlineAlpha)).frame(height: 1)
    }

    private var fold: some View {
        Rectangle().fill(spot.opacity(CardColors.bandFoldAlpha)).frame(width: 3)
    }
}

/// A centred flow of the band's names (the first half of the subviews) with a
/// knot (the second half) between two names on the same line only
/// (`BandLines`). A name wider than the band wraps inside its own line.
struct BandFlow: Layout {
    var gap: CGFloat
    var lineGap: CGFloat

    private struct Arrangement {
        var lines: [[Int]]
        var names: [CGSize]
        var knot: CGSize
        var widths: [CGFloat]
        var heights: [CGFloat]
        var knotRun: CGFloat
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let arrangement = arrange(maxWidth: maxWidth, subviews: subviews)
        let widest = arrangement.widths.max() ?? 0
        let height = arrangement.heights.reduce(0, +) + lineGap * CGFloat(max(0, arrangement.heights.count - 1))
        return CGSize(width: proposal.width ?? widest, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let arrangement = arrange(maxWidth: bounds.width, subviews: subviews)
        let count = arrangement.names.count
        var y = bounds.minY
        for (lineIndex, line) in arrangement.lines.enumerated() {
            let lineHeight = arrangement.heights[lineIndex]
            var x = bounds.minX + (bounds.width - arrangement.widths[lineIndex]) / 2
            for (position, index) in line.enumerated() {
                if position > 0 {
                    subviews[count + index - 1].place(
                        at: CGPoint(x: x + gap, y: y + (lineHeight - arrangement.knot.height) / 2),
                        anchor: .topLeading, proposal: ProposedViewSize(arrangement.knot))
                    x += arrangement.knotRun
                } else if index > 0 {
                    // The knot of a name that starts a line is placed at a zero proposal
                    // (a subview a layout leaves unplaced lands at its centre), so it draws nothing.
                    subviews[count + index - 1].place(at: bounds.origin, anchor: .topLeading, proposal: .zero)
                }
                let size = arrangement.names[index]
                subviews[index].place(at: CGPoint(x: x, y: y + (lineHeight - size.height) / 2),
                                      anchor: .topLeading, proposal: ProposedViewSize(size))
                x += size.width
            }
            y += lineHeight + lineGap
        }
    }

    private func arrange(maxWidth: CGFloat, subviews: Subviews) -> Arrangement {
        let count = (subviews.count + 1) / 2
        let nameProposal = ProposedViewSize(width: maxWidth.isFinite ? maxWidth : nil, height: nil)
        let names = (0..<count).map { subviews[$0].sizeThatFits(nameProposal) }
        let knot = subviews.count > count ? subviews[count].sizeThatFits(.unspecified) : .zero
        let knotRun = subviews.count > count ? gap * 2 + knot.width : 0
        let lines = BandLines.lines(widths: names.map(\.width), knotRun: knotRun, maxWidth: maxWidth)
        let widths = lines.map { BandLines.width(of: $0, widths: names.map(\.width), knotRun: knotRun) }
        let heights = lines.map { line in line.map { names[$0].height }.max() ?? 0 }
        return Arrangement(lines: lines, names: names, knot: knot, widths: widths, heights: heights, knotRun: knotRun)
    }
}
