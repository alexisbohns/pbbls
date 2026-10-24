import Foundation

/// A page of the Path's pager (#1226, D2): one per focusable strip item, in
/// strip order. A week is named by its Monday at midnight in the Spine's
/// calendar, the key every Spine page already uses.
enum PathPage: Hashable {
    case week(Date)
    case month(CalendarMonth)

    var weekStart: Date? {
        if case let .week(start) = self { return start }
        return nil
    }

    /// The RPC a page's wrap comes from, for the logs.
    var rpcName: String {
        switch self {
        case .week: return "path_week_wrap"
        case .month: return "path_month_wrap"
        }
    }
}

extension StripItem: Identifiable {
    var id: String {
        switch self {
        case let .week(week): return "w\(week.weekStart.iso)"
        case let .month(month): return "m\(month.month.firstDay.iso)"
        }
    }
}
