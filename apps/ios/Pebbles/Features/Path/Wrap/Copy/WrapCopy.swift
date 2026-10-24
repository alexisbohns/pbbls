import Foundation

/// The wraps' copy (#1226; Android: `WrapCopy`): the one place the payloads'
/// numbers, slugs and days become sentences, over `Localizable.xcstrings`'
/// `path.strip.*`, `path.wrap.*` and `path.month.*` keys, which are Android's
/// strings word for word. The language is passed in (views pass
/// `SpineStrings.appLocale`), so the tests pin `en` and `fr`; dates take the
/// Spine's calendar and its date locale.
enum WrapCopy {
    // MARK: - Strip

    static func stripNow(locale: Locale) -> String {
        SpineStrings.string("path.strip.now", locale: locale)
    }

    /// "Week of October 5, 4 moments"; the live week says "This week, from …, so far".
    static func stripWeek(_ week: StripWeek, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        let start = week.weekStart.date(in: calendar.timeZone) ?? .distantPast
        let day = SpineDates.dayMonth(start, calendar: calendar, locale: dateLocale)
        if !week.isFocusable {
            return SpineStrings.format("path.strip.spacerA11y", locale: locale, day)
        }
        let key = week.isLive ? "path.strip.liveWeekA11y" : "path.strip.weekA11y"
        return SpineStrings.format(key, locale: locale, day, week.moments)
    }

    /// "September, the month".
    static func stripMonth(_ month: CalendarMonth, locale: Locale) -> String {
        SpineStrings.format("path.strip.monthA11y", locale: locale, monthName(month, locale: locale))
    }

    // MARK: - Months

    /// "October", "Octobre": the month standing alone, capitalised.
    static func monthName(_ month: CalendarMonth, locale: Locale) -> String {
        let name = calendar(locale).standaloneMonthSymbols[month.month - 1]
        return name.prefix(1).uppercased(with: locale) + name.dropFirst()
    }

    /// "Oct", "oct.": the strip's label for a month.
    static func monthShort(_ month: CalendarMonth, locale: Locale) -> String {
        calendar(locale).shortStandaloneMonthSymbols[month.month - 1]
    }

    private static func calendar(_ locale: Locale) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return calendar
    }
}
