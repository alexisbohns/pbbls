import Foundation

/// The Spine's dates (#1130), from the locale's own patterns so weekday and
/// month names need no keys: a port of `apps/web/lib/utils/spine/spine-dates.ts`
/// plus the month, weekday and time the opening, the dots and the entries print.
/// The calendar carries the zone and the locale the language; never `.current`.
/// Callers pass the bare language locale (`en`, `fr`), as iOS reads
/// `Locale.current`'s language and `SpineDatesTests` assumes; its hour cycle
/// (the user's 12/24 h setting) is part of each formatter's cache key.
enum SpineDates {
    /// "mon", "lun": short weekday, lowercase, trailing period stripped.
    static func shortWeekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        var text = format(date, "EEE", calendar, locale).lowercased(with: locale)
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }

    /// "Monday, October 5", "lundi 5 octobre": a seam's heading and a stone's date.
    static func longDay(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEEMMMMd", calendar, locale)
    }

    /// "Monday, October 5 – Sunday, October 11": the span a week's feed covers.
    static func dayRange(_ from: Date, _ to: Date, calendar: Calendar, locale: Locale) -> String {
        let formatter = DateIntervalFormatter()
        formatter.locale = locale
        formatter.calendar = gregorian(calendar)
        formatter.timeZone = calendar.timeZone
        formatter.dateTemplate = "EEEEMMMMd"
        return formatter.string(from: from, to: to)
    }

    /// "Monday", "lundi".
    static func weekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEE", calendar, locale)
    }

    /// "M", "L": the persons' day header, before lowercasing.
    static func narrowWeekday(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "EEEEE", calendar, locale)
    }

    /// "October", "octobre".
    static func month(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "MMMM", calendar, locale)
    }

    /// "10:15 AM", "10:15": a moment's time on the locale's clock.
    static func time(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        format(date, "jmm", calendar, locale)
    }

    // MARK: - Formatters, one per template, locale, hour cycle and zone

    private static let lock = NSLock()
    private static var formatters: [String: DateFormatter] = [:]

    private static func format(_ date: Date, _ template: String, _ calendar: Calendar, _ locale: Locale) -> String {
        let key = "\(template)|\(locale.identifier)|\(locale.hourCycle)|\(calendar.timeZone.identifier)"
        lock.lock()
        defer { lock.unlock() }
        if let formatter = formatters[key] { return formatter.string(from: date) }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.calendar = gregorian(calendar)
        formatter.timeZone = calendar.timeZone
        formatter.setLocalizedDateFormatFromTemplate(template)
        formatters[key] = formatter
        return formatter.string(from: date)
    }

    /// Formatters read names from a Gregorian calendar in the caller's zone; the
    /// ISO calendar differs only in week rules.
    private static func gregorian(_ calendar: Calendar) -> Calendar {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        return gregorian
    }
}
