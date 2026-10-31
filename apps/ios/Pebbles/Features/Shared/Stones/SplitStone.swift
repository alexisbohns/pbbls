import SwiftUI

/// One stone cut into `count` equal cells (#1382 D7): the quick actions, You's
/// counters, the first-five stepper and the Lab's drawn band. The cells lay out
/// as equal columns `options.gutter` apart, as `split` cuts them; the pieces
/// are drawn behind, filled from `fills` (cycled), and hidden from VoiceOver,
/// so each cell's content is the element. Height comes from the content, at
/// least `minHeight`.
struct SplitStone<Cell: View>: View {
    let count: Int
    let seed: String
    var options = StoneOptions()
    let fills: [Color]
    var minHeight: CGFloat = 0
    @ViewBuilder let cell: (Int) -> Cell

    var body: some View {
        HStack(spacing: options.gutter) {
            ForEach(0..<count, id: \.self) { index in
                cell(index)
                    .frame(maxWidth: .infinity, minHeight: minHeight, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .background {
            GeometryReader { proxy in
                let pieces = StoneCache.split(size: proxy.size, count: count, seed: seed, options: options)
                ForEach(Array(pieces.enumerated()), id: \.offset) { index, outline in
                    StoneGeometry.path(outline).fill(fill(index))
                }
            }
            .accessibilityHidden(true)
        }
    }

    private func fill(_ index: Int) -> Color {
        fills.isEmpty ? Color.pebbles.surfaceContainer : fills[index % fills.count]
    }
}
