import SwiftUI

/// Flow layout that places its children left-to-right and wraps to a new
/// line whenever the next child wouldn't fit in the proposed width.
/// Both axes use the same gap. iOS 17+ — uses the `Layout` protocol.
///
/// Children are measured against the proposed width so a child with
/// `.lineLimit(1)` (or a similar truncation modifier) can fit when its
/// natural size would overflow.
struct PebblePillFlow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        let rows = computeRows(subviews: subviews, maxWidth: maxWidth)
        let height = rows.reduce(0) { $0 + $1.height } + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let rows = computeRows(subviews: subviews, maxWidth: bounds.width)
        var y = bounds.minY
        for row in rows {
            var x = bounds.minX
            for item in row.items {
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y),
                    proposal: ProposedViewSize(width: item.size.width, height: item.size.height)
                )
                x += item.size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        struct Item {
            let index: Int
            let size: CGSize
        }
        var items: [Item] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func computeRows(subviews: Subviews, maxWidth: CGFloat) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let childProposal = ProposedViewSize(
                width: maxWidth.isFinite ? maxWidth : nil,
                height: nil
            )
            let size = subviews[index].sizeThatFits(childProposal)
            var current = rows[rows.count - 1]
            let candidateWidth = current.width
                + (current.items.isEmpty ? 0 : spacing)
                + size.width
            if current.items.isEmpty || candidateWidth <= maxWidth {
                if !current.items.isEmpty { current.width += spacing }
                current.items.append(Row.Item(index: index, size: size))
                current.width += size.width
                current.height = max(current.height, size.height)
                rows[rows.count - 1] = current
            } else {
                var fresh = Row()
                fresh.items.append(Row.Item(index: index, size: size))
                fresh.width = size.width
                fresh.height = size.height
                rows.append(fresh)
            }
        }
        return rows
    }
}

#Preview {
    PebblePillFlow {
        PebbleMetaPill(
            icon: .system("heart.fill"),
            label: "Anxiety",
            style: .emotion(color: Color(red: 0.5, green: 0.4, blue: 0.95))
        )
        PebbleMetaPill(icon: .system("square.grid.2x2"), label: "Family", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Writing, Books, Photography", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Travel", style: .neutral)
        PebbleMetaPill(icon: .system("folder.fill"), label: "Long collection name that wraps", style: .neutral)
    }
    .frame(width: 320)
    .padding()
    .background(Color.pebblesBackground)
}
