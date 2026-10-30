import CoreGraphics
import Foundation

/// The Explorer wheel's places (#1306, spec D6; the board's `wheel`): a 300-unit
/// square on the origin, angles clockwise from twelve, one radius per ring from
/// the centre out. Seat index → angle and ring → radius; nothing here reads
/// whether a seat is done. Android: `ExplorerWheelGeometry`.
enum ExplorerWheelGeometry {
    static let side: CGFloat = 300
    static let firstFive: CGFloat = 44
    static let sixFirsts: CGFloat = 68
    static let twentyFive: CGFloat = 90
    static let domains: CGFloat = 112
    static let emotions: CGFloat = 134
    /// The ring of 25 seats: the Explorer's "25 pebbles" milestone (D6).
    static let twentyFiveSeats = 25

    /// The board's `polar`: `radius` at `degrees` clockwise from twelve o'clock.
    static func point(_ radius: CGFloat, _ degrees: Double) -> CGPoint {
        let radians = degrees * .pi / 180
        return CGPoint(x: radius * CGFloat(sin(radians)), y: -radius * CGFloat(cos(radians)))
    }

    /// The first five round their ring, the first at twelve (the Home checklist's
    /// order), spaced by how many the server sent: every 72° for five.
    static func firstFiveAngle(_ index: Int, of count: Int) -> Double {
        Double(index) * 360 / Double(max(count, 1))
    }

    /// The six firsts round their ring, half a step past twelve, spaced by how
    /// many the server sent: from 30° every 60° for six.
    static func sixFirstAngle(_ index: Int, of count: Int) -> Double {
        seatAngle(index, of: count)
    }

    /// `count` seats round a ring, the first half a step past twelve.
    static func seatAngle(_ index: Int, of count: Int) -> Double {
        (Double(index) + 0.5) * 360 / Double(max(count, 1))
    }

    /// The emotions' seats, as the rosace seats them: seat 2.5 at twelve.
    static func emotionAngle(_ seat: Double, of count: Int) -> Double {
        (seat - 2.5) * 360 / Double(max(count, 1))
    }

    /// A family's arc on the emotions ring: half a step outside its first and
    /// last seats, 1.2° in from each end.
    static func familyArc(start: Int, length: Int, of count: Int) -> (from: Double, to: Double) {
        let step = 360 / Double(max(count, 1))
        return (emotionAngle(Double(start), of: count) - step / 2 + 1.2,
                emotionAngle(Double(start + length - 1), of: count) + step / 2 - 1.2)
    }

    /// The points of an arc of `radius`, one per degree, ending exactly on `to`.
    static func arc(_ radius: CGFloat, from: Double, to: Double) -> [CGPoint] {
        guard to > from else { return [point(radius, from)] }
        return stride(from: from, to: to, by: 1).map { point(radius, $0) } + [point(radius, to)]
    }

    /// A family's boundary tick: half a step before its first seat, from 6 to 11 beyond the ring.
    static func familyTick(start: Int, of count: Int) -> (CGPoint, CGPoint) {
        let angle = emotionAngle(Double(start), of: count) - 180 / Double(max(count, 1))
        return (point(emotions + 6, angle), point(emotions + 11, angle))
    }
}
