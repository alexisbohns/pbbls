import Foundation

/// One slot in the iOS Path's horizontal weeks roll.
///
/// `weekStart` is the ISO Monday 00:00 of the week, in the calendar passed
/// to `WeekRollBuilder.build(...)`. `pebbles` is already sorted per the
/// past-vs-current rule (past = oldest first, current/future = newest first).
///
/// Identity is `weekStart` so SwiftUI's `ForEach` keys correctly across
/// rebuilds (e.g. after a pebble create that does not change which weeks
/// are populated).
struct WeekRollEntry: Identifiable, Hashable {
    let weekStart: Date
    let pebbles: [Pebble]
    var id: Date { weekStart }
}
