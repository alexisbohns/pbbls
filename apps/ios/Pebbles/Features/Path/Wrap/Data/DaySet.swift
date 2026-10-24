import Foundation

/// The days of a span (a week of 7, or a month) that something touches, as 0-based indices.
struct DaySet: Hashable, Sendable {
    let length: Int
    let days: Set<Int>

    /// Indices outside `0..<length` are dropped.
    init(length: Int, days: some Sequence<Int>) {
        self.length = length
        self.days = Set(days.filter { $0 >= 0 && $0 < length })
    }

    func contains(_ day: Int) -> Bool { days.contains(day) }

    var count: Int { days.count }
}

/// One day of a cairn: how many moments it holds and its strongest pebble's
/// intensity (0 without a pebble).
struct DayWeight: Hashable, Sendable {
    let moments: Int
    let topIntensity: Int

    /// A `[moments, strongest intensity]` pair; a missing value is 0.
    init(pair: [Int]) {
        moments = pair.first ?? 0
        topIntensity = pair.count > 1 ? pair[1] : 0
    }

    init(moments: Int, topIntensity: Int) {
        self.moments = moments
        self.topIntensity = topIntensity
    }
}
