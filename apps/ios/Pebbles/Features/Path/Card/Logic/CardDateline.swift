import Foundation

/// The held card's dates (#1146 §Recto), formatted with an explicit calendar
/// and locale so tests pin them; views pass `SpineCalendar.current` and
/// `SpineStrings.dateLocale` (the app language with the device's hour cycle
/// and region, as the Spine's seams and cards do). iOS's `MomentDetail` has no zone of its own, so
/// "the moment's zone" is the device's, as on the Spine.
enum CardDateline {
    /// "Thursday, 8 October 2026 · 21:00": the full date, then the short time.
    static func text(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var day = Date.FormatStyle.dateTime.weekday(.wide).day().month(.wide).year()
        day.calendar = gregorian(in: calendar)
        day.timeZone = calendar.timeZone
        day.locale = locale
        return date.formatted(day) + " · " + time(date, calendar: calendar, locale: locale)
    }

    /// The locale's short time.
    static func time(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.calendar = gregorian(in: calendar)
        style.timeZone = calendar.timeZone
        style.locale = locale
        return date.formatted(style)
    }

    /// The Spine's calendar is ISO 8601 (Monday-first weeks), and ICU has no
    /// locale patterns for that calendar: a full date formatted on it falls back
    /// to root's "y MMMM d, EEEE" in every language. The same days and times in
    /// the same zone on the Gregorian calendar take the locale's own order.
    private static func gregorian(in calendar: Calendar) -> Calendar {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        return gregorian
    }
}
