import Foundation

/// Calendar-day arithmetic in the caller's calendar and zone (#1130 D10), a port
/// of `apps/web/lib/utils/spine/local-day.ts`. Never `.current`: tests pin
/// Europe/Paris as Vitest does.
enum LocalDays {
    /// Whole calendar days from `weekStart`'s day to `date`'s (0 = Monday for an
    /// ISO Monday). Counted by the calendar, so a 23- or 25-hour day is one day.
    static func dayIndexInWeek(weekStart: Date, _ date: Date, calendar: Calendar) -> Int {
        let from = calendar.startOfDay(for: weekStart)
        let to = calendar.startOfDay(for: date)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// Midnight of the calendar day `days` after `date`'s. DST-safe.
    static func adding(_ days: Int, to date: Date, calendar: Calendar) -> Date {
        let start = calendar.startOfDay(for: date)
        return calendar.date(byAdding: .day, value: days, to: start) ?? start
    }
}
