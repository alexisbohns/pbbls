import SwiftUI

/// A rock cut rows × columns (#1510 D7): one `SplitStone` per row, seeded `<seed>#<row>` exactly as
/// Android's `StoneGrid.rowSeeds`, stacked `options.gutter` apart, so the pieces read as one stone cut
/// both ways. `index` is row-major.
enum StoneGridLayout {
    static func rowSeeds(seed: String, rows: Int) -> [String] { (0..<rows).map { "\(seed)#\($0)" } }
    static func cellIndex(row: Int, column: Int, columns: Int) -> Int { row * columns + column }
}

/// The `StoneGridLayout` drawn: `rows` split stones of `columns` pieces. `cell` receives the row-major
/// index and the column, and fills its piece; `fills` holds one colour per cell.
struct StoneGrid<Cell: View>: View {
    let rows: Int
    let columns: Int
    let seed: String
    let fills: [Color]
    var options = StoneOptions()
    var minCellHeight: CGFloat = 88
    @ViewBuilder let cell: (_ index: Int, _ column: Int) -> Cell

    var body: some View {
        let seeds = StoneGridLayout.rowSeeds(seed: seed, rows: rows)
        VStack(spacing: options.gutter) {
            ForEach(0..<rows, id: \.self) { row in
                SplitStone(
                    count: columns,
                    seed: seeds[row],
                    options: options,
                    fills: rowFills(row),
                    minHeight: minCellHeight
                ) { column in
                    cell(StoneGridLayout.cellIndex(row: row, column: column, columns: columns), column)
                }
            }
        }
    }

    private func rowFills(_ row: Int) -> [Color] {
        let start = row * columns
        guard start < fills.count else { return [] }
        return Array(fills[start..<min(start + columns, fills.count)])
    }
}
