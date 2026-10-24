import Foundation

/// The strip as drawn and the pages it focuses (#1226). The server's strip is
/// the truth; the feed only fills what the strip cannot know yet: a week the
/// feed holds and the strip does not (the strip failed to load, or lags a write
/// by one fetch) joins as a week with its moments counted and its cairn left
/// blank. Nothing here aggregates a day. Android: `PathPages`.
enum PathPages {
    static func items(strip: Strip?, entries: [WeekRollEntry], today: Date, calendar: Calendar) -> [StripItem] {
        let zone = calendar.timeZone
        let current = WeekRollBuilder.weekStart(for: today, calendar: calendar)
        let fromServer = strip?.items ?? []
        let focusableWeeks = Set(fromServer.compactMap { item -> CalendarDay? in
            guard case let .week(week) = item, week.isFocusable else { return nil }
            return week.weekStart
        })
        var missing: [CalendarDay: StripItem] = [:]
        for entry in entries {
            let day = CalendarDay(entry.weekStart, in: zone)
            guard !focusableWeeks.contains(day) else { continue }
            missing[day] = .week(StripWeek(
                weekStart: day,
                isoWeek: WeekOpening.isoWeekNumber(entry.weekStart, timeZone: zone),
                isoYear: isoCalendar(zone).component(.yearForWeekOfYear, from: entry.weekStart),
                moments: entry.moments.count,
                isFocusable: true,
                isLive: entry.weekStart == current,
                days: []
            ))
        }
        // A spacer the feed now fills is replaced in place; the rest join in date order.
        // A strip kept across a Monday still calls last week live: only the current week is.
        let currentDay = CalendarDay(current, in: zone)
        let kept = fromServer.map { item -> StripItem in
            guard case let .week(week) = item else { return item }
            if let filled = missing[week.weekStart] { return filled }
            guard week.isLive, week.weekStart != currentDay else { return item }
            return .week(StripWeek(weekStart: week.weekStart, isoWeek: week.isoWeek, isoYear: week.isoYear,
                                   moments: week.moments, isFocusable: week.isFocusable, isLive: false,
                                   days: week.days))
        }
        let placed = Set(kept.compactMap { item -> CalendarDay? in
            guard case let .week(week) = item else { return nil }
            return week.weekStart
        })
        let joining = missing.filter { !placed.contains($0.key) }.sorted { $0.key < $1.key }.map(\.value)
        // A month sits after the week that holds its last day; the index keeps the sort stable.
        return (kept + joining).enumerated().sorted { lhs, rhs in
            let left = sortKey(lhs.element, calendar: calendar)
            let right = sortKey(rhs.element, calendar: calendar)
            if left.week != right.week { return left.week < right.week }
            if left.isMonth != right.isMonth { return !left.isMonth }
            return lhs.offset < rhs.offset
        }.map(\.element)
    }

    static func pages(_ items: [StripItem], calendar: Calendar) -> [PathPage] {
        items.filter(\.isFocusable).map { page(of: $0, calendar: calendar) }
    }

    static func page(of item: StripItem, calendar: Calendar) -> PathPage {
        switch item {
        case let .week(week): return .week(week.weekStart.date(in: calendar.timeZone) ?? .distantPast)
        case let .month(month): return .month(month.month)
        }
    }

    /// The Path's refocus rule, over pages: keep the focus if it is still a
    /// page; otherwise the current week; otherwise the page closest in time (a
    /// month by its last day). Nil only without pages.
    static func refocused(_ pages: [PathPage], focused: PathPage?, today: Date, calendar: Calendar) -> PathPage? {
        guard !pages.isEmpty else { return nil }
        if let focused, pages.contains(focused) { return focused }
        let current = PathPage.week(WeekRollBuilder.weekStart(for: today, calendar: calendar))
        if pages.contains(current) { return current }
        let anchor = date(of: focused ?? current, calendar: calendar)
        return pages.min { lhs, rhs in
            abs(date(of: lhs, calendar: calendar).timeIntervalSince(anchor))
                < abs(date(of: rhs, calendar: calendar).timeIntervalSince(anchor))
        }
    }

    private static func sortKey(_ item: StripItem, calendar: Calendar) -> (week: CalendarDay, isMonth: Bool) {
        switch item {
        case let .week(week):
            return (week.weekStart, false)
        case let .month(month):
            let last = month.month.lastDay.date(in: calendar.timeZone) ?? .distantPast
            return (CalendarDay(WeekRollBuilder.weekStart(for: last, calendar: calendar), in: calendar.timeZone), true)
        }
    }

    private static func date(of page: PathPage, calendar: Calendar) -> Date {
        switch page {
        case let .week(start): return start
        case let .month(month): return month.lastDay.date(in: calendar.timeZone) ?? .distantPast
        }
    }

    private static func isoCalendar(_ zone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = zone
        return calendar
    }
}
