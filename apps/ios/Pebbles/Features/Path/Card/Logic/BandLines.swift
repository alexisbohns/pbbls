import CoreGraphics

/// The collection band's lines (#1146 §Band): greedy, a knot (a small diamond)
/// between two names on the same line only, so a wrapped line never starts or
/// ends on one, which would read as a stray mark. Android's `BandFlow` measure.
enum BandLines {
    /// - Parameters:
    ///   - widths: each name's width (its mark, gap and italic name).
    ///   - knotRun: a knot and the gap each side of it.
    /// - Returns: the names' indices, line by line.
    static func lines(widths: [CGFloat], knotRun: CGFloat, maxWidth: CGFloat) -> [[Int]] {
        guard !widths.isEmpty else { return [] }
        var lines: [[Int]] = [[]]
        var lineWidth: CGFloat = 0
        for (index, width) in widths.enumerated() {
            let isLineEmpty = lines[lines.count - 1].isEmpty
            if !isLineEmpty, lineWidth + knotRun + width > maxWidth {
                lines.append([index])
                lineWidth = width
            } else {
                lineWidth += (isLineEmpty ? 0 : knotRun) + width
                lines[lines.count - 1].append(index)
            }
        }
        return lines
    }

    static func width(of line: [Int], widths: [CGFloat], knotRun: CGFloat) -> CGFloat {
        line.map { widths[$0] }.reduce(0, +) + knotRun * CGFloat(max(0, line.count - 1))
    }
}
