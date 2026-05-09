import Foundation

/// Groups pebbles by their ISO 8601 week, returning `(weekStart, pebbles)`
/// pairs ordered descending by week. The `weekStart` is the first instant
/// of that week's Monday in the provided calendar.
///
/// - Caller passes `Calendar(identifier: .iso8601)` — only that calendar
///   gives Mon-start, week-1-contains-first-Thursday semantics
///   consistently. `Calendar.current` would vary by user locale.
/// - Within a group, input order is preserved — callers typically pass
///   pebbles already sorted descending by `happenedAt`.
/// - The bucket key is reconstructed from `[.yearForWeekOfYear,
///   .weekOfYear, .weekday]` rather than `[.year, .month]`, so dates that
///   span a calendar-year boundary while sharing an ISO week (e.g.
///   2025-12-29 and 2026-01-02 both land in ISO week 1 of 2026) bucket
///   together.
func groupPebblesByISOWeek(
    _ pebbles: [Pebble],
    calendar: Calendar
) -> [(key: Date, value: [Pebble])] {
    let buckets = Dictionary(grouping: pebbles) { pebble -> Date in
        let comps = calendar.dateComponents(
            [.yearForWeekOfYear, .weekOfYear],
            from: pebble.happenedAt
        )
        var weekStart = DateComponents()
        weekStart.yearForWeekOfYear = comps.yearForWeekOfYear
        weekStart.weekOfYear = comps.weekOfYear
        weekStart.weekday = 2 // Monday in ISO 8601
        return calendar.date(from: weekStart) ?? pebble.happenedAt
    }
    return buckets
        .map { (key: $0.key, value: $0.value) }
        .sorted { $0.key > $1.key }
}
