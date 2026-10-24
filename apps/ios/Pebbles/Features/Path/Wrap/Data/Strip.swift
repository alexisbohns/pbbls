import Foundation

/// The Path's strip (#1226), oldest first: weeks, and the months placed after
/// the week holding their last day.
struct Strip: Hashable, Sendable {
    let items: [StripItem]
}

enum StripItem: Hashable, Sendable {
    /// An ISO week; an empty past week is a non-focusable spacer.
    case week(StripWeek)
    /// A completed month with at least one pebble.
    case month(StripMonth)

    var isFocusable: Bool {
        switch self {
        case let .week(week): return week.isFocusable
        case .month: return true
        }
    }
}

struct StripWeek: Hashable, Sendable {
    let weekStart: CalendarDay
    let isoWeek: Int
    let isoYear: Int
    let moments: Int
    let isFocusable: Bool
    /// The current week.
    let isLive: Bool
    let days: [DayWeight]
}

struct StripMonth: Hashable, Sendable {
    let month: CalendarMonth
    let pebbles: Int
}

extension StripPayload {
    /// Unknown kinds are dropped, so a server that adds an item kind does not break this client.
    var model: Strip {
        Strip(items: items.compactMap { item in
            switch item.kind {
            case "week":
                return item.weekStart.map { start in
                    .week(StripWeek(weekStart: start, isoWeek: item.isoWeek, isoYear: item.isoYear, moments: item.count,
                                    isFocusable: item.focusable, isLive: item.live,
                                    days: item.days.map(DayWeight.init(pair:))))
                }
            case "month":
                return item.month.map { .month(StripMonth(month: CalendarMonth($0), pebbles: item.count)) }
            default:
                return nil
            }
        })
    }
}
