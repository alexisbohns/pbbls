import SwiftUI

/// One share of a `ProportionBar`: a non-negative weight and its colour.
struct BarSegment: Hashable {
    let weight: Double
    let color: Color
}

/// Shares as one bar (#1226): the emotions tile, a domain row. Segments keep
/// their order, take the width in proportion to their weights, are rounded at
/// their ends and separated by a 2 pt gap; zero-weight segments take no room.
/// Decorative: the caller says the shares in words.
struct ProportionBar: View {
    let segments: [BarSegment]
    var height: CGFloat = SpineLayout.barHeight

    var body: some View {
        Canvas { context, size in
            for (rect, color) in Self.layout(segments, width: size.width, height: size.height) {
                context.fill(Path(roundedRect: rect, cornerRadius: size.height / 2), with: .color(color))
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    /// Each shown segment's rect, left to right.
    static func layout(_ segments: [BarSegment], width: CGFloat, height: CGFloat) -> [(CGRect, Color)] {
        let shown = segments.filter { $0.weight > 0 }
        let total = shown.reduce(0) { $0 + $1.weight }
        guard total > 0 else { return [] }
        let room = width - SpineLayout.barGap * CGFloat(shown.count - 1)
        var x: CGFloat = 0
        return shown.map { segment in
            let w = room * CGFloat(segment.weight / total)
            defer { x += w + SpineLayout.barGap }
            return (CGRect(x: x, y: 0, width: w, height: height), segment.color)
        }
    }
}
