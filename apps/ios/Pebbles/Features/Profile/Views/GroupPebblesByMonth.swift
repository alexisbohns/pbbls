import Foundation

/// Groups pebbles by the first day of their calendar month, returning
/// `(monthStart, pebbles)` pairs ordered descending by month.
///
/// - Keys are the first instant of each month in the provided calendar.
/// - Within a group, input order is preserved — callers typically pass
///   pebbles already sorted descending by `happenedAt`.
/// - `calendar` is injectable to keep tests deterministic; production
///   callers should pass `Calendar.current`.
func groupPebblesByMonth(
    _ pebbles: [Pebble],
    calendar: Calendar
) -> [(key: Date, value: [Pebble])] {
    let buckets = Dictionary(grouping: pebbles) { pebble -> Date in
        let comps = calendar.dateComponents([.year, .month], from: pebble.happenedAt)
        return calendar.date(from: comps) ?? pebble.happenedAt
    }
    return buckets
        .map { (key: $0.key, value: $0.value) }
        .sorted { $0.key > $1.key }
}
