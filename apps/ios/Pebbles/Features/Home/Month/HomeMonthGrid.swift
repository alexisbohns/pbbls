import SwiftUI

/// The month's weeks at the top of Home (#1382 D8, D9): the wrap's columns at
/// Home's size, in a fixed 292 pt frame so nothing jumps while the month loads.
/// A figure, not text: it stops growing at `.large`, and VoiceOver reads one
/// container (the month and its line) holding one element per week. A week
/// that has begun opens on the Path; a later one draws and reads as text.
struct HomeMonthGrid: View {
    let month: MonthWrap?
    let lookups: WrapLookups
    /// Today in the Spine's zone: a week starting after it is not a button.
    let today: CalendarDay
    let onWeek: (CalendarDay) -> Void

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        Group {
            if let month {
                WeeksColumns(wrap: month, lookups: lookups, room: WeeksColumns.Metrics.home.height,
                             metrics: .home, opens: { $0.weekStart <= today },
                             weekHint: Text("home.month.weekHint"), onWeek: onWeek)
                    .accessibilityElement(children: .contain)
                    .accessibilityLabel(Text(verbatim: label(month)))
            } else {
                Color.clear
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: WeeksColumns.Metrics.home.height)
        .dynamicTypeSize(...DynamicTypeSize.large)
    }

    private func label(_ month: MonthWrap) -> String {
        let line = WrapCopy.line(pebbles: month.pebbles, people: month.people, live: false,
                                 hasMoments: month.pebbles > 0, locale: locale)
        return "\(WrapCopy.monthName(month.month, locale: locale)), \(line)"
    }
}
