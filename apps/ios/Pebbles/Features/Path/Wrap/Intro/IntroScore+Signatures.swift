import Foundation

/// A week in the intro: its cairn's stones (0, an empty week) and the souls it shows.
struct IntroWeekShape: Hashable, Sendable {
    let stones: Int
    let souls: Int
}

/// A milestone in the intro: its pebble's intensity, 1…3, and the souls it shows.
struct IntroMilestoneShape: Hashable, Sendable {
    let intensity: Int
    let souls: Int
}

/// The cards' signatures (#1241 D10): one row at a time, each in its parts, a
/// tick for each part.
extension IntroScore {
    static let stoneGap: TimeInterval = 0.05
    static let wordsAfter: TimeInterval = 0.1
    static let markGap: TimeInterval = 0.05
    static let nextWeek: TimeInterval = 0.12
    static let stampSpan: TimeInterval = 0.24
    static let nextMilestone: TimeInterval = 0.14
    static let swellFrom: Float = 0.15
    static let swellSharpness: Float = 0.3

    /// The weeks: each week's cairn stone by stone from the bottom, then its
    /// words, then its souls one by one, then the next week. An empty week
    /// lands whole on a faint tick.
    static func weeks(_ weeks: [IntroWeekShape]) -> [IntroBeat] {
        var beats = head(hasLead: false, insights: false)
        var at = rowsAt
        for (row, week) in weeks.enumerated() {
            guard week.stones > 0 else {
                beats.append(IntroBeat(key: .row(row), at: at, duration: IntroMotion.rise, tick: .faint))
                at += nextWeek
                continue
            }
            var part = at
            for stone in 0..<week.stones {
                if stone > 0 { part += stoneGap }
                beats.append(IntroBeat(key: .cairn(row, stone), at: part, duration: IntroMotion.pop,
                                       tick: .cairn(stone)))
            }
            part += wordsAfter
            beats.append(IntroBeat(key: .words(row), at: part, duration: IntroMotion.rise, tick: .light))
            part = souls(row: row, count: week.souls, after: part, into: &beats)
            at = part + nextWeek
        }
        return beats
    }

    /// The milestones: each stone stamps, ticking at its impact as firmly as
    /// its pebble is intense, then its date, its name and its souls one by
    /// one, then the next.
    static func milestones(_ rows: [IntroMilestoneShape]) -> [IntroBeat] {
        var beats = head(hasLead: false, insights: false)
        var at = rowsAt
        for (row, milestone) in rows.enumerated() {
            beats.append(IntroBeat(key: .stamp(row), at: at, duration: stampSpan))
            beats.append(IntroBeat(key: .impact(row), at: at + IntroMotion.stampImpact * stampSpan, duration: 0,
                                   tick: .seal(milestone.intensity)))
            var part = at + stampSpan
            beats.append(IntroBeat(key: .date(row), at: part, duration: IntroMotion.rise, tick: .light))
            part += partGap
            beats.append(IntroBeat(key: .name(row), at: part, duration: IntroMotion.rise, tick: .light))
            part = souls(row: row, count: milestone.souls, after: part, into: &beats)
            at = part + nextMilestone
        }
        return beats
    }

    /// The domains: each name and count, then its gauge filling for longer
    /// the fuller it is, swelling as it fills and ticking as it stops, then
    /// the next; *more* with the last.
    static func domains(shares: [Double], more: Bool) -> [IntroBeat] {
        var beats = head(hasLead: false, insights: false)
        var at = rowsAt
        var last = rowsAt
        for (row, share) in shares.enumerated() {
            beats.append(IntroBeat(key: .row(row), at: at, duration: IntroMotion.rise, tick: .light))
            let fill = fillSpan(share)
            let gauge = at + partGap
            beats.append(IntroBeat(key: .gauge(row), at: gauge, duration: fill,
                                   swell: IntroSwell(from: swellFrom, to: swellTo(share), sharpness: swellSharpness)))
            last = gauge + fill
            beats.append(IntroBeat(key: .full(row), at: last, duration: 0, tick: .light))
            at = last + nextRow
        }
        if more, !shares.isEmpty {
            beats.append(IntroBeat(key: .more, at: last, duration: IntroMotion.rise))
        }
        return beats
    }

    /// How long a gauge fills: a fuller one takes longer.
    static func fillSpan(_ share: Double) -> TimeInterval {
        0.25 + 0.45 * IntroMotion.clamp(share)
    }

    /// How strong a gauge's swell ends: a fuller one ends stronger.
    static func swellTo(_ share: Double) -> Float {
        0.25 + 0.5 * Float(IntroMotion.clamp(share))
    }

    /// A row's souls one by one, a `partGap` after `after`; returns the last part's time.
    private static func souls(row: Int, count: Int, after: TimeInterval,
                              into beats: inout [IntroBeat]) -> TimeInterval {
        guard count > 0 else { return after }
        var part = after + partGap
        for soul in 0..<count {
            if soul > 0 { part += markGap }
            beats.append(IntroBeat(key: .mark(row, soul), at: part, duration: IntroMotion.pop, tick: .light))
        }
        return part
    }
}
