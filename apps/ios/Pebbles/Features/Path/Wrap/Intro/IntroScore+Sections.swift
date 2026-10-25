import Foundation

/// The sections' beats (#1241 §Choreography): a head, then rows; a person's
/// row in three parts. The gem, tone and hours build on `head`.
extension IntroScore {
    static let leadAt: TimeInterval = 0.16
    static let rowsAt: TimeInterval = 0.32
    static let rowGap: TimeInterval = 0.07
    /// When the insights head leads tone or hours, the rest comes this much later.
    static let insightsShift: TimeInterval = 0.16
    static let partGap: TimeInterval = 0.09
    static let ringSpan: TimeInterval = 0.45
    static let nextRow: TimeInterval = 0.06

    /// A list section (weeks, milestones, domains): its head, its lead line if
    /// any, each row in turn, and *more* with the last when it has one.
    static func rows(count: Int, hasLead: Bool, more: Bool) -> [IntroBeat] {
        var beats = head(hasLead: hasLead, insights: false)
        let times = (0..<count).map { rowsAt + rowGap * Double($0) }
        for (row, at) in times.enumerated() {
            beats.append(IntroBeat(key: .row(row), at: at, duration: IntroMotion.rise, tick: .light))
        }
        if more, let last = times.last {
            beats.append(IntroBeat(key: .more, at: last, duration: IntroMotion.rise))
        }
        return beats
    }

    /// The persons: per row its glyph, its name, then its ring's line drawn
    /// from the 1st, a beat on each day present as the line reaches it; the
    /// next row once the ring has closed; *more* as the last ring closes. With
    /// nobody named, the head and the invitation (`hasLead`).
    static func persons(rings: [DaySet], hasLead: Bool) -> [IntroBeat] {
        var beats = head(hasLead: hasLead, insights: false)
        var at = rowsAt
        for (row, days) in rings.enumerated() {
            beats.append(IntroBeat(key: .glyph(row), at: at, duration: IntroMotion.pop, tick: .light))
            beats.append(IntroBeat(key: .name(row), at: at + partGap, duration: IntroMotion.rise, tick: .light))
            let ring = at + 2 * partGap
            beats.append(IntroBeat(key: .ring(row), at: ring, duration: ringSpan))
            for day in days.days.sorted() {
                let reached = ringSpan * IntroMotion.easeInOutInverse(dotPlace(day, length: days.length))
                beats.append(IntroBeat(key: .dot(row, day), at: ring + reached, duration: IntroMotion.pop,
                                       tick: .light))
            }
            if row == rings.count - 1 {
                beats.append(IntroBeat(key: .more, at: ring + ringSpan, duration: IntroMotion.rise))
            }
            at = ring + ringSpan + nextRow
        }
        return beats
    }

    /// How far round its ring a day sits, 0…1: the middle of its slot, the ring opening before the 1st.
    static func dotPlace(_ day: Int, length: Int) -> Double {
        length > 0 ? (Double(day) + 0.5) / Double(length) : 0
    }

    /// A section's head: the insights head first when it leads tone or hours,
    /// then the head, then the lead line if any.
    static func head(hasLead: Bool, insights: Bool) -> [IntroBeat] {
        var beats: [IntroBeat] = []
        if insights {
            beats.append(IntroBeat(key: .insights, at: 0, duration: IntroMotion.rise, tick: .light))
        }
        let shift = shift(insights)
        beats.append(IntroBeat(key: .header, at: shift, duration: IntroMotion.rise, tick: .light))
        if hasLead {
            beats.append(IntroBeat(key: .lead, at: shift + leadAt, duration: IntroMotion.rise))
        }
        return beats
    }

    static func shift(_ insights: Bool) -> TimeInterval {
        insights ? insightsShift : 0
    }
}
