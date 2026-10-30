import CoreGraphics
import Foundation

/// The docked year disc (#1306, spec D12; the board's g/112 and
/// `yearDiscDocked`): a stone 220 units across on (120, 120), a spoke a month at
/// 30° × (month − 1) from twelve, notches on the inner disc (50) and the rim
/// (80). A month's counter docks at the radius of the level the server gives
/// (centre 30, inner disc 50, rim 80) and its progress runs along the spoke.
/// Android: `YearDiscGeometry`.
enum YearDiscGeometry {
    static let box = CGRect(x: 10, y: 10, width: 220, height: 220)
    static let centre = CGPoint(x: 120, y: 120)
    /// Level → radius.
    static let radii: [CGFloat] = [30, 50, 80]
    static let spoke: ClosedRange<CGFloat> = 14...88
    static let numeralRadius: CGFloat = 96
    static let numerals = ["I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"]

    /// The stone's edge (the board's g/112 outline).
    static let outline: [CGPoint] = [
        CGPoint(x: 225.1, y: 120.0), CGPoint(x: 223.0, y: 136.3), CGPoint(x: 217.4, y: 151.6), CGPoint(x: 211.9, y: 166.8),
        CGPoint(x: 205.0, y: 181.7), CGPoint(x: 192.4, y: 192.4), CGPoint(x: 181.0, y: 203.9), CGPoint(x: 167.6, y: 213.5),
        CGPoint(x: 151.9, y: 218.2), CGPoint(x: 136.1, y: 221.4), CGPoint(x: 120.0, y: 225.2), CGPoint(x: 103.6, y: 223.5),
        CGPoint(x: 87.6, y: 219.6), CGPoint(x: 72.9, y: 212.5), CGPoint(x: 58.7, y: 204.4), CGPoint(x: 46.3, y: 193.7),
        CGPoint(x: 34.7, y: 182.0), CGPoint(x: 27.8, y: 167.0), CGPoint(x: 20.4, y: 152.4), CGPoint(x: 17.4, y: 136.3),
        CGPoint(x: 15.6, y: 120.0), CGPoint(x: 16.7, y: 103.6), CGPoint(x: 20.4, y: 87.6), CGPoint(x: 27.0, y: 72.6),
        CGPoint(x: 36.8, y: 59.6), CGPoint(x: 45.4, y: 45.4), CGPoint(x: 58.2, y: 35.0), CGPoint(x: 73.4, y: 28.6),
        CGPoint(x: 88.0, y: 21.5), CGPoint(x: 103.9, y: 18.3), CGPoint(x: 120.0, y: 14.9), CGPoint(x: 136.0, y: 18.8),
        CGPoint(x: 152.0, y: 21.7), CGPoint(x: 167.3, y: 27.1), CGPoint(x: 180.7, y: 36.5), CGPoint(x: 192.8, y: 47.2),
        CGPoint(x: 203.3, y: 59.5), CGPoint(x: 213.5, y: 72.4), CGPoint(x: 220.4, y: 87.4), CGPoint(x: 222.5, y: 103.8),
    ]

    /// One grain of the stone's surface, in the disc's units.
    struct Grain: Equatable {
        let x: CGFloat
        let y: CGFloat
        let r: CGFloat
    }

    /// The stone's grain, the board's forty dots verbatim (its `YEARBASE`), drawn over the stone.
    static let grain: [Grain] = [
        Grain(x: 217.6, y: 135.5, r: 0.7), Grain(x: 25.8, y: 134.9, r: 0.8), Grain(x: 105.0, y: 25.2, r: 0.6),
        Grain(x: 37.7, y: 179.8, r: 0.9), Grain(x: 25.9, y: 134.9, r: 0.8), Grain(x: 189.4, y: 50.6, r: 0.8),
        Grain(x: 209.8, y: 74.2, r: 0.7), Grain(x: 89.4, y: 25.9, r: 0.9), Grain(x: 52.0, y: 52.0, r: 0.8),
        Grain(x: 25.7, y: 105.1, r: 0.8), Grain(x: 217.5, y: 135.4, r: 0.8), Grain(x: 197.8, y: 63.5, r: 0.8),
        Grain(x: 75.7, y: 207.0, r: 0.9), Grain(x: 150.1, y: 27.3, r: 0.7), Grain(x: 25.1, y: 135.0, r: 0.6),
        Grain(x: 135.0, y: 214.5, r: 0.5), Grain(x: 89.0, y: 24.5, r: 0.9), Grain(x: 75.3, y: 32.2, r: 0.5),
        Grain(x: 25.7, y: 134.9, r: 0.6), Grain(x: 63.8, y: 42.6, r: 0.7), Grain(x: 176.8, y: 41.8, r: 0.5),
        Grain(x: 120.0, y: 18.6, r: 0.8), Grain(x: 105.1, y: 26.1, r: 0.6), Grain(x: 197.9, y: 176.6, r: 0.5),
        Grain(x: 120.0, y: 23.9, r: 0.9), Grain(x: 164.2, y: 206.7, r: 0.5), Grain(x: 42.2, y: 176.5, r: 0.9),
        Grain(x: 178.3, y: 200.2, r: 0.7), Grain(x: 89.6, y: 213.6, r: 0.7), Grain(x: 204.2, y: 162.9, r: 0.5),
        Grain(x: 207.2, y: 75.6, r: 0.8), Grain(x: 176.1, y: 42.8, r: 0.8), Grain(x: 120.0, y: 219.1, r: 0.5),
        Grain(x: 219.4, y: 104.3, r: 0.7), Grain(x: 90.6, y: 29.7, r: 0.6), Grain(x: 90.5, y: 210.8, r: 0.9),
        Grain(x: 216.3, y: 88.7, r: 0.6), Grain(x: 120.0, y: 220.0, r: 0.5), Grain(x: 135.4, y: 22.6, r: 0.7),
        Grain(x: 20.7, y: 135.7, r: 0.5),
    ]

    /// A month's level as the disc can draw it: 0 at the centre, 1 the inner disc, 2 the rim.
    static func level(_ month: AchievementsPage.Month) -> Int {
        min(max(month.level, 0), 2)
    }

    static func angle(_ month: Int) -> Double {
        Double(month - 1) * 30
    }

    /// `radius` on month `month`'s spoke, turned `offset` degrees.
    static func point(_ radius: CGFloat, month: Int, offset: Double = 0) -> CGPoint {
        let p = ExplorerWheelGeometry.point(radius, angle(month) + offset)
        return CGPoint(x: p.x + centre.x, y: p.y + centre.y)
    }

    /// Where a month's counter docks: its level's radius.
    static func counter(_ month: AchievementsPage.Month) -> CGPoint {
        point(radii[level(month)], month: month.month)
    }

    /// The way travelled toward the next level: from 6 past the docked radius,
    /// `progress` of the gap to the next radius less 12. Nil on the rim or before any.
    static func climb(_ month: AchievementsPage.Month) -> (CGPoint, CGPoint)? {
        let level = level(month)
        guard level < 2, month.progress > 0 else { return nil }
        let from = radii[level]
        let gap = radii[level + 1] - from - 12
        return (point(from + 6, month: month.month), point(from + 6 + CGFloat(month.progress) * gap, month: month.month))
    }

    /// A rim month's whole climb, from the inner disc to the rim.
    static func rimClimb(_ month: Int) -> (CGPoint, CGPoint) {
        (point(radii[1] + 6, month: month), point(radii[2] - 7, month: month))
    }

    /// The notch across month `month`'s spoke at `radius`, 6 long.
    static func notch(_ radius: CGFloat, month: Int) -> (CGPoint, CGPoint) {
        let radians = angle(month) * .pi / 180
        let middle = point(radius, month: month)
        let dx = 3 * CGFloat(cos(radians))
        let dy = 3 * CGFloat(sin(radians))
        return (CGPoint(x: middle.x - dx, y: middle.y - dy), CGPoint(x: middle.x + dx, y: middle.y + dy))
    }

    /// The goal on the inner disc of a month that has begun: ±4.6° about its spoke.
    static func goal(month: Int) -> (CGPoint, CGPoint) {
        (point(radii[1], month: month, offset: 4.6), point(radii[1], month: month, offset: -4.6))
    }

    /// A numeral's size in the disc's units: 7, or 6 for the three- and four-letter ones.
    static func numeralSize(_ month: Int) -> CGFloat {
        numerals[month - 1].count >= 3 ? 6 : 7
    }
}
