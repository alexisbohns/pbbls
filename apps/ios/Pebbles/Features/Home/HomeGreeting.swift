import Foundation

/// The Home tab's greeting (spec §1.1): a title, an italic line and a dateline,
/// from the hour band (morning < 12, afternoon 12–18, evening ≥ 18) with the
/// Monday-morning and Sunday-evening overrides. Pure: the clock, zone and
/// locale are passed in so tests pin every band in both languages.
enum HomeGreeting {
    struct Lines: Equatable {
        let title: String
        let line: String
        let dateline: String
    }

    enum Band: String { case morning, afternoon, evening }
    enum Override: String { case mondayMorning = "monday-morning", sundayEvening = "sunday-evening" }

    static func band(hour: Int) -> Band {
        switch hour {
        case ..<12: .morning
        case 12..<18: .afternoon
        default: .evening
        }
    }

    static func lines(at date: Date, name: String, zone: TimeZone, locale: Locale) -> Lines {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        calendar.locale = locale
        let hour = calendar.component(.hour, from: date)
        let weekday = calendar.component(.weekday, from: date) // 1 = Sunday … 7 = Saturday
        let band = band(hour: hour)
        let override: Override? = switch (weekday, band) {
        case (2, .morning): .mondayMorning
        case (1, .evening): .sundayEvening
        default: nil
        }
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = override?.rawValue ?? band.rawValue
        let greeting = SpineStrings.string("home.greeting.\(key).title", locale: locale)
        let title = name.isEmpty ? greeting : "\(greeting), \(name)"
        let line = SpineStrings.string("home.greeting.\(key).line", locale: locale)
        return Lines(title: title, line: line, dateline: dateline(date, calendar: calendar, locale: locale))
    }

    private static let lock = NSLock()
    private static var formatters: [String: DateFormatter] = [:]

    /// "Monday 2 November" / "lundi 2 novembre": weekday, day, month, no year.
    /// One formatter per locale and zone, as `SpineDates` caches them.
    static func dateline(_ date: Date, calendar: Calendar, locale: Locale) -> String {
        let key = "\(locale.identifier)|\(calendar.timeZone.identifier)"
        lock.lock()
        defer { lock.unlock() }
        if let formatter = formatters[key] { return formatter.string(from: date) }
        let f = DateFormatter()
        f.calendar = calendar
        f.timeZone = calendar.timeZone
        f.locale = locale
        // Day before month in both shipped languages (en's template would give "Monday, November 2").
        f.dateFormat = "EEEE d MMMM"
        formatters[key] = f
        return f.string(from: date)
    }
}
