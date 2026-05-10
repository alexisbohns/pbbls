import SwiftUI

/// The "MAY 4 · MAY 10" pill above the path body. Shows the focused
/// week's date range, with a year suffix when the year differs from
/// today's. Chevrons step `focusedWeekStart` through the surrounding
/// `entries` array.
///
/// This file currently exposes only `formatRange` (used in tests).
/// The full view body lives in a later task.
struct WeekHeaderView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar
    let today: Date

    var body: some View {
        // Placeholder body; replaced in Task 13.
        Text(Self.formatRange(
            weekStart: focusedWeekStart, today: today,
            calendar: calendar, locale: .current
        ))
    }

    /// Pure helper, exposed for testing. Locale is taken from the
    /// environment in production but injected here so the test suite is
    /// hermetic against the simulator's locale.
    static func formatRange(
        weekStart: Date,
        today: Date,
        calendar: Calendar,
        locale: Locale
    ) -> String {
        guard let weekEnd = calendar.date(byAdding: .day, value: 6, to: weekStart) else {
            return ""
        }
        let monthDay = Date.FormatStyle.dateTime
            .month(.wide).day()
            .locale(locale)
        let startLabel = weekStart.formatted(monthDay)
        let endLabel   = weekEnd.formatted(monthDay)

        let weekYear  = calendar.component(.yearForWeekOfYear, from: weekStart)
        let todayYear = calendar.component(.yearForWeekOfYear, from: today)

        if weekYear != todayYear {
            return "\(startLabel) · \(endLabel) · \(weekYear)"
        } else {
            return "\(startLabel) · \(endLabel)"
        }
    }
}
