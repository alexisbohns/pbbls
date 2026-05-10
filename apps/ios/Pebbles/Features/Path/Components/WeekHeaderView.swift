import SwiftUI

/// The "MAY 4 · MAY 10" pill above the path body. Shows the focused
/// week's date range, with a year suffix when the year differs from
/// today's. Chevrons step `focusedWeekStart` through the surrounding
/// `entries` array.
struct WeekHeaderView: View {
    let entries: [WeekRollEntry]
    @Binding var focusedWeekStart: Date
    let calendar: Calendar
    let today: Date

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: 12) {
            chevronButton(isPrevious: true)
            Spacer(minLength: 0)
            Text(Self.formatRange(
                weekStart: focusedWeekStart, today: today,
                calendar: calendar, locale: .current
            ))
            .font(.custom("Ysabeau-SemiBold", size: 17))
            .tracking(0.34)              // 2% of 17pt
            .textCase(.uppercase)
            .foregroundStyle(Color.pebblesMutedForeground)
            Spacer(minLength: 0)
            chevronButton(isPrevious: false)
        }
        .padding(.horizontal, 16)
        .frame(height: 40)
        .overlay(
            Capsule().stroke(strokeColor, lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }

    private var strokeColor: Color {
        colorScheme == .dark ? Color.pebblesForeground : Color.pebblesMutedForeground
    }

    @ViewBuilder
    private func chevronButton(isPrevious: Bool) -> some View {
        let target = isPrevious
            ? WeekRollBuilder.previous(of: focusedWeekStart, in: entries)
            : WeekRollBuilder.next(of: focusedWeekStart, in: entries)

        Button {
            guard let target else { return }
            withAnimation { focusedWeekStart = target.weekStart }
        } label: {
            Image(systemName: isPrevious ? "chevron.compact.left" : "chevron.compact.right")
                .font(.title3)
                .foregroundStyle(Color.pebblesAccent)
                .opacity(target == nil ? 0.3 : 1.0)
        }
        .disabled(target == nil)
        .accessibilityLabel(isPrevious ? "Previous week" : "Next week")
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
