import SwiftUI

/// Splits a flat array into rows of `columns` width.
/// Pads the final row with `false` if the input length isn't divisible.
/// Returns an empty array for empty input. Used by AssiduityGrid and
/// (later) the stats view's denser variant.
func chunkAssiduity(_ data: [Bool], columns: Int) -> [[Bool]] {
    guard !data.isEmpty, columns > 0 else { return [] }
    var rows: [[Bool]] = []
    var i = 0
    while i < data.count {
        let end = min(i + columns, data.count)
        var row = Array(data[i..<end])
        if row.count < columns {
            row.append(contentsOf: Array(repeating: false, count: columns - row.count))
        }
        rows.append(row)
        i += columns
    }
    return rows
}

struct AssiduityGrid: View {
    let data: [Bool]
    var columns: Int = 7
    var cellSize: CGFloat = 7
    var cellSpacing: CGFloat = 4

    var body: some View {
        let rows = chunkAssiduity(data, columns: columns)
        VStack(spacing: cellSpacing) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: cellSpacing) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, active in
                        // 7pt cells: no icon draws this small (spec D4); a lit
                        // stone is a filled dot, a day without one an outline.
                        Circle()
                            .fill(active ? Color.pebbles.primary : Color.clear)
                            .overlay(Circle().strokeBorder(Color.pebbles.surfaceContainerHighest,
                                                           lineWidth: active ? 0 : 1))
                            .frame(width: cellSize, height: cellSize)
                    }
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Assiduity grid, last 28 days"))
    }
}

#Preview {
    let sample = (0..<28).map { $0 % 3 != 0 }
    return AssiduityGrid(data: sample)
        .padding()
        .background(Color.pebbles.surface)
}
