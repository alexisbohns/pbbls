// swiftlint:disable identifier_name
import CoreGraphics
import SwiftUI
import os

/// Serialization + parsing between a `[CGPoint]` stroke and an SVG `d` string.
///
/// Serialization mirrors `apps/web/lib/utils/simplify-path.ts` (`pointsToSvgPath`):
/// uses quadratic Bezier curves through midpoints for >=3 points to produce a
/// visually smooth stroke. Parsing supports `M`, `L`, `Q`, `C` — the commands
/// the codebase emits (iOS-carved) or persists (web + system seed glyphs).
enum SVGPath {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "svg-path")

    // MARK: - Serialize

    static func svgPathString(from points: [CGPoint], precision: Int = 2) -> String {
        if points.isEmpty { return "" }

        let fmt = { (value: CGFloat) in round(value: Double(value), precision: precision) }

        if points.count == 1 {
            let p = points[0]
            return "M\(fmt(p.x)),\(fmt(p.y)) L\(fmt(p.x)),\(fmt(p.y))"
        }

        if points.count == 2 {
            return "M\(fmt(points[0].x)),\(fmt(points[0].y)) L\(fmt(points[1].x)),\(fmt(points[1].y))"
        }

        var d = "M\(fmt(points[0].x)),\(fmt(points[0].y))"

        // Smooth via midpoints — mirrors web `pointsToSvgPath` logic.
        for i in 1..<(points.count - 1) {
            let midX = (points[i].x + points[i + 1].x) / 2
            let midY = (points[i].y + points[i + 1].y) / 2
            d += " Q\(fmt(points[i].x)),\(fmt(points[i].y)) \(fmt(midX)),\(fmt(midY))"
        }

        let last = points[points.count - 1]
        d += " L\(fmt(last.x)),\(fmt(last.y))"
        return d
    }

    private static func round(value: Double, precision: Int) -> String {
        let multiplier = pow(10.0, Double(precision))
        let rounded = (value * multiplier).rounded() / multiplier
        // Trim trailing zeros to match web output (`10` not `10.00`).
        if rounded == rounded.rounded() {
            return String(Int(rounded))
        }
        // Use `%g` to strip trailing zeros; cap significant digits via precision + 2
        // so that values like 0.12 (2 sig figs) are not rounded further.
        let sigFigs = precision + 2
        return String(format: "%.\(sigFigs)g", rounded)
    }

    // MARK: - Parse

    /// Parses a subset of SVG path syntax (M/L/Q/C) into a SwiftUI `Path`.
    /// On malformed input, logs a warning and returns an empty `Path`.
    static func path(from d: String) -> Path {
        guard !d.isEmpty else { return Path() }

        var path = Path()
        var index = d.startIndex
        var current = CGPoint.zero

        while index < d.endIndex {
            while index < d.endIndex, d[index].isWhitespace { index = d.index(after: index) }
            guard index < d.endIndex else { break }

            let command = d[index]
            guard "MLQCmlqc".contains(command) else {
                logger.warning("unknown svg command \(command, privacy: .public) — aborting parse")
                return Path()
            }
            index = d.index(after: index)

            guard let (nextIndex, nextCurrent) = apply(
                command: command, to: &path, from: index, in: d, current: current
            ) else {
                return Path()
            }
            index = nextIndex
            current = nextCurrent
        }

        return path
    }

    // Applies a single SVG command to `path`, advancing `index` and returning the new current point.
    // Returns `nil` on parse error (caller should return an empty path).
    private static func apply(
        command: Character,
        to path: inout Path,
        from index: String.Index,
        in d: String,
        current: CGPoint
    ) -> (String.Index, CGPoint)? {
        let relative = command.isLowercase
        switch command {
        case "M", "m":
            guard let (pt, next) = readPoint(d, from: index) else {
                logger.warning("malformed M — aborting parse")
                return nil
            }
            let dest = relative ? CGPoint(x: current.x + pt.x, y: current.y + pt.y) : pt
            path.move(to: dest)
            return (next, dest)
        case "L", "l":
            guard let (pt, next) = readPoint(d, from: index) else {
                logger.warning("malformed L — aborting parse")
                return nil
            }
            let dest = relative ? CGPoint(x: current.x + pt.x, y: current.y + pt.y) : pt
            path.addLine(to: dest)
            return (next, dest)
        case "Q", "q":
            guard
                let (control, next1) = readPoint(d, from: index),
                let (end, next2) = readPoint(d, from: next1)
            else {
                logger.warning("malformed Q — aborting parse")
                return nil
            }
            let absControl = relative ? CGPoint(x: current.x + control.x, y: current.y + control.y) : control
            let absEnd = relative ? CGPoint(x: current.x + end.x, y: current.y + end.y) : end
            path.addQuadCurve(to: absEnd, control: absControl)
            return (next2, absEnd)
        case "C", "c":
            guard
                let (c1, next1) = readPoint(d, from: index),
                let (c2, next2) = readPoint(d, from: next1),
                let (end, next3) = readPoint(d, from: next2)
            else {
                logger.warning("malformed C — aborting parse")
                return nil
            }
            let absC1 = relative ? CGPoint(x: current.x + c1.x, y: current.y + c1.y) : c1
            let absC2 = relative ? CGPoint(x: current.x + c2.x, y: current.y + c2.y) : c2
            let absEnd = relative ? CGPoint(x: current.x + end.x, y: current.y + end.y) : end
            path.addCurve(to: absEnd, control1: absC1, control2: absC2)
            return (next3, absEnd)
        default:
            return nil
        }
    }

    /// Reads `x,y` (or `x y`) starting at `from`, skipping leading whitespace.
    /// Returns the parsed point and the next index on success.
    private static func readPoint(_ s: String, from: String.Index) -> (CGPoint, String.Index)? {
        var index = from
        while index < s.endIndex, s[index].isWhitespace { index = s.index(after: index) }
        guard let (x, afterX) = readNumber(s, from: index) else { return nil }
        index = afterX
        // Skip comma or whitespace separator
        while index < s.endIndex, s[index] == "," || s[index].isWhitespace {
            index = s.index(after: index)
        }
        guard let (y, afterY) = readNumber(s, from: index) else { return nil }
        return (CGPoint(x: x, y: y), afterY)
    }

    private static func readNumber(_ s: String, from: String.Index) -> (Double, String.Index)? {
        var end = from
        while end < s.endIndex, "0123456789.-+eE".contains(s[end]) {
            end = s.index(after: end)
        }
        guard end > from, let value = Double(s[from..<end]) else { return nil }
        return (value, end)
    }
}
// swiftlint:enable identifier_name
