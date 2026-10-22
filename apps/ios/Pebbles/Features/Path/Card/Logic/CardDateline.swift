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
        day.calendar = calendar
        day.timeZone = calendar.timeZone
        day.locale = locale
        return date.formatted(day) + " · " + time(date, calendar: calendar, locale: locale)
    }

    /// The locale's short time.
    static func time(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var style = Date.FormatStyle(date: .omitted, time: .shortened)
        style.calendar = calendar
        style.timeZone = calendar.timeZone
        style.locale = locale
        return date.formatted(style)
    }
}
