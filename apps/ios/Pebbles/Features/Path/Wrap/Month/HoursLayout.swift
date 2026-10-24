import CoreGraphics
import Foundation

/// The dial's arithmetic (#1226, D12): display only, over the server's local
/// decimal hours. Android: `HoursLayout`.
enum HoursLayout {
    private static let clearance: CGFloat = 1.4

    /// Hours from `from` to `to`, clockwise on a day: 22.5 → 1.0 is 2.5.
    static func forward(_ from: Double, _ to: Double) -> Double {
        ((to - from).truncatingRemainder(dividingBy: 24) + 24).truncatingRemainder(dividingBy: 24)
    }

    /// Lanes for marks on a ring so none touch (the board's swarm): earliest
    /// first, each into the innermost lane where it clears every mark already
    /// there. `hourLength` is one hour's length on a lane's circle, `sizes` each
    /// mark's width, in points. With a `limit`, marks past the last lane share
    /// it: the dial has room for that many lanes, and no more (Android has no
    /// limit, and a month collected in one sitting runs off its ring).
    static func lanes(_ hours: [Double], sizes: [CGFloat], limit: Int? = nil,
                      hourLength: (Int) -> CGFloat) -> [Int] {
        // A stable sort, as Kotlin's sortedBy.
        let order = hours.indices.sorted { hours[$0] < hours[$1] || (hours[$0] == hours[$1] && $0 < $1) }
        var lane = Array(repeating: 0, count: hours.count)
        var placed: [Int] = []
        for i in order {
            var candidate = 0
            while placed.contains(where: { j in
                lane[j] == candidate
                    && CGFloat(circular(hours[i], hours[j])) * hourLength(candidate)
                    < (sizes[i] + sizes[j]) / 2 + clearance
            }) {
                candidate += 1
            }
            lane[i] = candidate
            placed.append(i)
        }
        guard let limit else { return lane }
        return lane.map { min($0, max(limit, 1) - 1) }
    }

    private static func circular(_ a: Double, _ b: Double) -> Double {
        min(abs(a - b), 24 - abs(a - b))
    }
}
