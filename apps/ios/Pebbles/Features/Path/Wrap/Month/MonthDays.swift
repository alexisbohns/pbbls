import SwiftUI

/// How the month's days read (#1226, D9): a ring from the 1st, the default, or a
/// calendar. One setting for every month, kept on the device with
/// `@AppStorage`; the month sheet's switch writes it. Android: `DayView`.
enum DayView: String, CaseIterable {
    case circle, calendar

    static let storageKey = "path.dayView"
}

extension MonthWrap {
    /// The month's days as the reader chose to see them.
    func dayMode(_ view: DayView) -> DayMode {
        view == .circle ? .circle : .calendar(leading: firstWeekday)
    }

    /// The days a month's figures number (0-based): the 1st, each Monday and the last day.
    var numberedDays: [Int] {
        (0..<daysInMonth).filter { day in day == 0 || day == daysInMonth - 1 || (firstWeekday + day) % 7 == 0 }
    }
}

/// A month row's ring (the board's `ring(…, 40)`).
enum MonthLayout {
    static let rowRing: CGFloat = 40
    /// The quiet above and below a calendar, taller than its row.
    static let calendarInset: CGFloat = 8
    static let sectionPadX: CGFloat = 20
    static let sectionTop: CGFloat = 32
    static let headGap: CGFloat = 8
    static let pageBottom: CGFloat = 96
}

extension View {
    /// A calendar's rows grow past the row's own height: it keeps clear of the rules.
    func dayMarksInset(_ view: DayView) -> some View {
        padding(.vertical, view == .calendar ? MonthLayout.calendarInset : 0)
    }
}

/// A month section's head, with the board's spacing; on the deck, the stepper at its far end (#1241 D11).
struct MonthSectionHead: View {
    let label: String
    var hint: String?

    var body: some View {
        SpineSectionHeader(label, hint: hint) { MonthDeckStepper() }
            .padding(.horizontal, MonthLayout.sectionPadX)
            .padding(.top, MonthLayout.sectionTop)
            .padding(.bottom, MonthLayout.headGap)
    }
}

/// Above a calendar: the weekday letters over its columns. Decorative; each row says its days.
struct CalendarHead: View {
    var body: some View {
        // Any Monday: the letters only need the week's order.
        let monday = CalendarDay(year: 2026, month: 10, day: 5).date(in: SpineCalendar.current.timeZone) ?? .now
        let letters = WeekOpening.weekdayLetters(weekStart: monday, calendar: SpineCalendar.current,
                                                 locale: SpineStrings.appLocale)
        HStack(spacing: SpineLayout.dotGap) {
            ForEach(letters.indices, id: \.self) { index in
                Text(verbatim: letters[index])
                    .spineType(.dayLetter)
                    .foregroundStyle(Color.pebbles.outline)
                    .fixedSize()
                    .frame(width: SpineLayout.dot)
            }
        }
        .accessibilityHidden(true)
    }
}

/// The month sheet's day view (D9): a segmented control, calendar · circle. Android: `ViewModeSwitch`.
struct ViewModeSwitch: View {
    @Binding var view: DayView

    var body: some View {
        let locale = SpineStrings.appLocale
        Picker(selection: $view) {
            Text(verbatim: WrapCopy.viewCalendar(locale: locale)).tag(DayView.calendar)
            Text(verbatim: WrapCopy.viewCircle(locale: locale)).tag(DayView.circle)
        } label: {
            EmptyView()
        }
        .pickerStyle(.segmented)
    }
}
