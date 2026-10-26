import Foundation

/// Tone's and hours' beats (#1241 §Choreography): figures drawn progressively.
extension IntroScore {
    static let sweepAt: TimeInterval = 0.32
    static let sweepSpan: TimeInterval = 1.2
    static let dialAt: TimeInterval = 0.32
    static let stonesAt: TimeInterval = 0.52
    static let stonesSpan: TimeInterval = 1.2

    /// The insights head alone, over a figure that draws nothing: the arrival's tick (D8).
    static func insightsHead() -> [IntroBeat] {
        [IntroBeat(key: .insights, at: 0, duration: 0, tick: .arrive)]
    }

    /// The tone: its head (after the insights head when it leads them) and its
    /// sentence, then the sweep left to right, a beat on each day with pebbles
    /// as the sweep reaches it.
    static func tone(days: [ToneDay], insights: Bool) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: insights)
        let start = shift(insights) + sweepAt
        beats.append(IntroBeat(key: .sweep, at: start, duration: sweepSpan))
        for (index, day) in days.enumerated() where day.up + day.even + day.down > 0 {
            let place = days.count > 1 ? Double(index) / Double(days.count - 1) : 0
            beats.append(IntroBeat(key: .day(index), at: start + sweepSpan * IntroMotion.easeInOutInverse(place),
                                   duration: IntroMotion.pop, tick: .light))
        }
        return beats
    }

    /// The hours: its head and sentence, the dial's face, then `stones` stones
    /// in the order of the time of day they happened (`ranks`).
    static func hours(stones: Int, insights: Bool) -> [IntroBeat] {
        var beats = head(hasLead: true, insights: insights)
        let shift = shift(insights)
        beats.append(IntroBeat(key: .dial, at: shift + dialAt, duration: IntroMotion.rise))
        for (rank, time) in IntroCurve.times(count: stones, duration: stonesSpan).enumerated() {
            beats.append(IntroBeat(key: .stone(rank), at: shift + stonesAt + time, duration: IntroMotion.pop,
                                   tick: .light))
        }
        return beats
    }

    /// Each stone's rank by the time of day it happened, from 00:00; ties keep their order.
    static func ranks(_ hours: [Double]) -> [Int] {
        let order = hours.indices.sorted { hours[$0] != hours[$1] ? hours[$0] < hours[$1] : $0 < $1 }
        var ranks = Array(repeating: 0, count: hours.count)
        for (rank, index) in order.enumerated() {
            ranks[index] = rank
        }
        return ranks
    }
}
