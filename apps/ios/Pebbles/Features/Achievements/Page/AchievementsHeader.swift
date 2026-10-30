import SwiftUI

/// The page's header (#1306, spec D1–D2): since the account began, and the days
/// kept, which is calendar age (the server's `days_kept`), never a streak. It
/// hides when the server sent no count.
struct AchievementsHeader: View {
    let since: CalendarDay?
    let daysKept: Int?

    var body: some View {
        if let daysKept {
            let locale = SpineStrings.appLocale
            let calendar = SpineCalendar.current
            VStack(alignment: .leading, spacing: 4) {
                if let since {
                    Text(verbatim: AchievementsCopy.since(since, calendar: calendar, locale: locale,
                                                          dateLocale: SpineStrings.dateLocale))
                        .spineType(.achievementsSince)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                HStack(alignment: .lastTextBaseline, spacing: 14) {
                    Text(verbatim: AchievementsCopy.number(daysKept, locale: locale))
                        .spineType(.achievementsDays)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: AchievementsCopy.daysUnit(daysKept, locale: locale))
                        .spineType(.achievementsDaysUnit)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 22)
            .padding(.top, 6)
            .padding(.bottom, 22)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: AchievementsA11y.header(
                daysKept: daysKept, since: since, calendar: calendar, locale: locale,
                dateLocale: SpineStrings.dateLocale)))
            .accessibilityAddTraits(.isHeader)
        }
    }
}
