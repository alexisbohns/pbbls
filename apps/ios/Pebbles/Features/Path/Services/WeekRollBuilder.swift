import Foundation

/// Pure builder for the iOS Path screen's weeks roll.
///
/// Returns `[WeekRollEntry]` containing every ISO week with at least one
/// pebble, plus the current week and the next week (so the user can always
/// see "today" and "tomorrow's week" in the roll). Entries are sorted
/// ascending by `weekStart`.
///
/// Within each entry, pebbles are sorted asymmetrically:
///   - Past weeks (whose Monday is strictly before the current week's
///     Monday) sort **ascending** by `happenedAt` — reading time forward.
///   - Current and future weeks sort **descending** — most recent first,
///     matching the way the old `PathView` rendered.
enum WeekRollBuilder {

    static func build(
        pebbles: [Pebble],
        calendar: Calendar,
        today: Date
    ) -> [WeekRollEntry] {
        let currentStart = weekStart(for: today, calendar: calendar)
        guard let nextStart = calendar.date(byAdding: .weekOfYear, value: 1, to: currentStart) else {
            return []
        }

        // Bucket pebbles by their week's Monday.
        let grouped: [Date: [Pebble]] = Dictionary(grouping: pebbles) { p in
            weekStart(for: p.happenedAt, calendar: calendar)
        }

        // Union: every week with pebbles, plus current and next.
        let weekStarts = Set(grouped.keys).union([currentStart, nextStart])

        return weekStarts
            .sorted()
            .map { ws in
                let raw = grouped[ws] ?? []
                let sorted = ws < currentStart
                    ? raw.sorted { $0.happenedAt < $1.happenedAt }
                    : raw.sorted { $0.happenedAt > $1.happenedAt }
                return WeekRollEntry(weekStart: ws, pebbles: sorted)
            }
    }

    /// ISO Monday 00:00:00 of the week containing `date`.
    private static func weekStart(for date: Date, calendar: Calendar) -> Date {
        let comps = calendar.dateComponents([.yearForWeekOfYear, .weekOfYear], from: date)
        var monday = DateComponents()
        monday.yearForWeekOfYear = comps.yearForWeekOfYear
        monday.weekOfYear = comps.weekOfYear
        monday.weekday = 2 // Monday in ISO 8601
        return calendar.date(from: monday) ?? date
    }
}

extension WeekRollBuilder {

    static func previous(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry? {
        guard let idx = entries.firstIndex(where: { $0.weekStart == weekStart }), idx > 0 else {
            return nil
        }
        return entries[idx - 1]
    }

    static func next(of weekStart: Date, in entries: [WeekRollEntry]) -> WeekRollEntry? {
        guard let idx = entries.firstIndex(where: { $0.weekStart == weekStart }),
              idx + 1 < entries.count else {
            return nil
        }
        return entries[idx + 1]
    }
}
