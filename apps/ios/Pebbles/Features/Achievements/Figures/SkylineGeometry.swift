import SwiftUI

/// The Pebbles card's skyline (#1306, the board's One skyline, g/92): eight
/// stages, one per step of a ladder, standing on a ground line at y 148, in the
/// board's units. Android: `SkylineGeometry`.
enum SkylineGeometry {
    static let ground: CGFloat = 148

    static let stages = [
        "M23 148 C23 143.3 29 143.3 29 148 Z",
        "M32.5 148 C32.1 136.9 34.9 133.2 40.7 133.2 C46.1 133.2 49.9 138.8 49.5 148 Z",
        "M53 148 C52.6 133.5 56.8 128.7 63.3 128.7 C68.8 128.7 72.4 136 72 148 Z",
        "M75.5 148 L76.7 135.5 L83.1 125.2 L89.9 127.5 L95.5 138.4 L95.5 148 Z",
        "M99 148 C98.6 128.4 105 121.8 112.8 121.8 C118.8 121.8 122.4 131.8 122 148 Z",
        "M125.5 148 L125.9 123.5 C126.3 116.7 133.4 116.1 134 122.8 L134 148 Z",
        "M141 148 L141 120.7 L137.5 120.7 C137.2 114.5 145.9 113.9 151.5 113.9 C157.1 113.9 165.8 114.5 165.5 120.7 "
            + "L162 120.7 L162 148 L158 148 L158 120.7 L145 120.7 L145 148 Z",
        "M169 148 C180.5 147.2 182 110.5 194.9 110.5 C208.4 110.5 207.4 145.8 217 148 Z"
    ]

    static let spans: [ClosedRange<CGFloat>] = [23...29, 32.5...49.5, 53...72, 75.5...95.5, 99...122, 125.5...134,
                                                 137.5...165.5, 169...217]
    static let tops: [CGFloat] = [144.5, 133.2, 128.7, 125.2, 121.8, 117.4, 113.9, 110.5]

    /// The card's and the sheet's view box, and the glyph rows' smaller one.
    static let wide = CGRect(x: 12, y: 104, width: 216, height: 48)
    static let narrow = CGRect(x: 18, y: 118, width: 110, height: 32)

    /// The stages parsed once.
    private static let stagePaths: [Path] = stages.map { SVGPathParser.parse($0).map(Path.init) ?? Path() }

    /// The stage drawn for `index`: itself, the last one for an index past the
    /// eighth (a level past the drawn ladder, as Android and web draw it), or
    /// none for a negative index.
    static func drawn(_ index: Int) -> Int? {
        index < 0 ? nil : min(index, stages.count - 1)
    }

    static func stage(_ index: Int) -> Path {
        drawn(index).map { stagePaths[$0] } ?? Path()
    }

    /// The part of stage `index` a fill of `fraction` covers, from the ground up (the board's clip).
    static func fill(_ index: Int, fraction: Double) -> CGRect {
        guard let index = drawn(index) else { return .null }
        let span = spans[index]
        let height = (ground - tops[index]) * CGFloat(min(max(fraction, 0), 1))
        return CGRect(x: span.lowerBound - 2, y: ground - height,
                      width: span.upperBound - span.lowerBound + 4, height: height + 1)
    }

    /// The contour over the first `count` stages, from x 16 to `end` on the ground:
    /// each parsed stage joined by a line, its close left out.
    static func contour(stages count: Int, end: CGFloat) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 16, y: ground))
        for stage in stagePaths.prefix(max(count, 0)) {
            stage.forEach { element in
                switch element {
                case .move(let to), .line(let to): path.addLine(to: to)
                case .quadCurve(let to, let control): path.addQuadCurve(to: to, control: control)
                case .curve(let to, let control1, let control2):
                    path.addCurve(to: to, control1: control1, control2: control2)
                case .closeSubpath: break
                }
            }
        }
        path.addLine(to: CGPoint(x: end, y: ground))
        return path
    }

    /// The stage a step stands on: its place on the ladder, or the last stage for
    /// a level past the drawn ladder (the pebbles' every-500 levels).
    static func stageIndex(of threshold: Int, in ladder: [Int]) -> Int {
        let last = min(ladder.count, stages.count) - 1
        return min(ladder.firstIndex(of: threshold) ?? last, last)
    }
}
