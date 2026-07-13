import CoreGraphics
import Foundation

/// A flattened subpath: dense points ready for displacement.
struct WobblePolyline {
    var points: [CGPoint]
    var isClosed: Bool
}

/// Flattens a `CGPath` into polylines whose chords are ≤ ~`step` units, so the
/// noise displacement reads as a smooth wobble rather than a kinked polygon
/// (issue #555 §2.3 step 1). Straight segments are subdivided too — a long
/// straight line needs interior vertices or it cannot bend.
enum WobblePathFlattener {

    private static let duplicateEpsilon: CGFloat = 1e-6

    static func flatten(_ path: CGPath, step: Double) -> [WobblePolyline] {
        let step = CGFloat(step)
        var polylines: [WobblePolyline] = []
        var current: [CGPoint] = []
        var subpathStart = CGPoint.zero
        var cursor = CGPoint.zero

        func flush(closed: Bool) {
            if !current.isEmpty {
                polylines.append(WobblePolyline(points: current, isClosed: closed))
            }
            current = []
        }

        func append(_ point: CGPoint) {
            if let last = current.last,
               abs(last.x - point.x) < Self.duplicateEpsilon,
               abs(last.y - point.y) < Self.duplicateEpsilon {
                return
            }
            current.append(point)
        }

        func appendLine(to end: CGPoint) {
            let distance = hypot(end.x - cursor.x, end.y - cursor.y)
            let count = max(1, Int((distance / step).rounded(.up)))
            for i in 1...count {
                let t = CGFloat(i) / CGFloat(count)
                append(CGPoint(
                    x: cursor.x + (end.x - cursor.x) * t,
                    y: cursor.y + (end.y - cursor.y) * t
                ))
            }
            cursor = end
        }

        func appendQuad(control: CGPoint, end: CGPoint) {
            // Control-polygon length over-estimates arc length, which only
            // makes chords denser than `step` — never sparser.
            let approxLength = hypot(control.x - cursor.x, control.y - cursor.y)
                + hypot(end.x - control.x, end.y - control.y)
            let count = max(1, Int((approxLength / step).rounded(.up)))
            let start = cursor
            for i in 1...count {
                let t = CGFloat(i) / CGFloat(count)
                let mt = 1 - t
                append(CGPoint(
                    x: mt * mt * start.x + 2 * mt * t * control.x + t * t * end.x,
                    y: mt * mt * start.y + 2 * mt * t * control.y + t * t * end.y
                ))
            }
            cursor = end
        }

        func appendCubic(control1: CGPoint, control2: CGPoint, end: CGPoint) {
            let approxLength = hypot(control1.x - cursor.x, control1.y - cursor.y)
                + hypot(control2.x - control1.x, control2.y - control1.y)
                + hypot(end.x - control2.x, end.y - control2.y)
            let count = max(1, Int((approxLength / step).rounded(.up)))
            let start = cursor
            for i in 1...count {
                let t = CGFloat(i) / CGFloat(count)
                let mt = 1 - t
                let c0 = mt * mt * mt
                let c1 = 3 * mt * mt * t
                let c2 = 3 * mt * t * t
                let c3 = t * t * t
                append(CGPoint(
                    x: c0 * start.x + c1 * control1.x + c2 * control2.x + c3 * end.x,
                    y: c0 * start.y + c1 * control1.y + c2 * control2.y + c3 * end.y
                ))
            }
            cursor = end
        }

        path.applyWithBlock { elementPointer in
            let element = elementPointer.pointee
            switch element.type {
            case .moveToPoint:
                flush(closed: false)
                cursor = element.points[0]
                subpathStart = cursor
                current = [cursor]
            case .addLineToPoint:
                appendLine(to: element.points[0])
            case .addQuadCurveToPoint:
                appendQuad(control: element.points[0], end: element.points[1])
            case .addCurveToPoint:
                appendCubic(control1: element.points[0], control2: element.points[1], end: element.points[2])
            case .closeSubpath:
                // Subdivide the implicit closing leg, then drop the duplicated
                // start point: rings are stored without repetition because the
                // outline builder wraps neighbors cyclically.
                appendLine(to: subpathStart)
                if let last = current.last, current.count > 1,
                   abs(last.x - subpathStart.x) < Self.duplicateEpsilon,
                   abs(last.y - subpathStart.y) < Self.duplicateEpsilon {
                    current.removeLast()
                }
                flush(closed: true)
                cursor = subpathStart
            @unknown default:
                break
            }
        }
        flush(closed: false)
        return polylines
    }
}
