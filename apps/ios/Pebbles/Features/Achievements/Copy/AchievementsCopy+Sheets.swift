import Foundation

/// The sheets' words (#1306), word for word Android's (the sheets' copy is
/// Android's first; iOS mirrors it): section titles and notes, row names and
/// dates, over `achievements.sheet.*`, `achievements.kind.*` and the catalog's
/// family titles. Every count is the served one; none is set against a target.
extension AchievementsCopy {
    static func sheet(_ key: String, locale: Locale) -> String {
        SpineStrings.string("achievements.sheet.\(key)", locale: locale)
    }

    /// A first-five task's title, as the shelf names it ("First moment"); one
    /// this build does not know reads "A first".
    static func firstFiveTitle(_ task: HomeChecklist.Task?, locale: Locale) -> String {
        guard let task else { return sheet("unknownFirst", locale: locale) }
        let family: String
        switch task {
        case .moment: family = "first_moment"
        case .pebble: family = "first_pebble"
        case .collection: family = "first_collection"
        case .soul: family = "first_soul"
        case .glyph: family = "first_glyph"
        }
        return SpineStrings.string("achievement.family.\(family).title", locale: locale)
    }

    /// The app's words for the six firsts: Small · Medium · Large · Lowlight ·
    /// Neutral · Highlight; one this build does not know reads "A first".
    static func kind(_ kind: Page.StoneKind?, locale: Locale) -> String {
        switch kind {
        case .small, .medium, .large: SpineStrings.string("achievements.kind.\(kind?.rawValue ?? "")", locale: locale)
        case .lowlight: SpineStrings.string("Lowlight", locale: locale)
        case .neutral: SpineStrings.string("Neutral", locale: locale)
        case .highlight: SpineStrings.string("Highlight", locale: locale)
        case nil: sheet("unknownFirst", locale: locale)
        }
    }

    /// "five of five", "cinq sur cinq": a ring's section note, in words.
    static func ofWords(_ count: Int, _ total: Int, locale: Locale) -> String {
        format("achievements.sheet.of", locale, NumberWords.cardinal(count, locale: locale),
               NumberWords.cardinal(total, locale: locale))
    }

    /// "14 of 18", "14 sur 18": a count beside its whole, in figures.
    static func countOf(_ count: Int, _ total: Int, locale: Locale) -> String {
        format("achievements.sheet.of", locale, number(count, locale: locale), number(total, locale: locale))
    }

    /// "No. 03": a domain's place in the order they came.
    static func registerNumber(_ place: Int, locale: Locale) -> String {
        format("achievements.sheet.number", locale, String(format: "%02ld", place))
    }

    /// "113 to go": the next milestone, never a date.
    static func toGo(_ left: Int, locale: Locale) -> String {
        format("achievements.sheet.toGo", locale, number(left, locale: locale))
    }

    /// "then every 500": the pebble ladder's tail.
    static func thenEvery(_ step: Int, locale: Locale) -> String {
        format("achievements.sheet.thenEvery", locale, number(step, locale: locale))
    }

    /// "None yet. 2026 is your first year here."
    static func firstYear(_ year: Int, locale: Locale) -> String {
        format("achievements.sheet.firstYear", locale, String(year))
    }

    /// A moment's name in a row, or "Untitled" for one the server sends without a name.
    static func momentName(_ name: String?, locale: Locale) -> String {
        name ?? SpineStrings.string("Untitled", locale: locale)
    }

    /// "Apr 21", "21 avr.": a served local day in the register.
    static func day(_ day: CalendarDay, calendar: Calendar, dateLocale: Locale) -> String {
        SpineDates.shortDayMonth(day.date(in: calendar.timeZone) ?? .distantPast, calendar: calendar,
                                 locale: dates(dateLocale))
    }

    // MARK: - Read aloud

    /// A row read aloud: its parts, then "not yet" for what is not done, then
    /// "new since you last looked" when it is ("250, 113 to go, not yet").
    static func row(_ parts: [String?], notYet: Bool = false, isNew: Bool = false, locale: Locale) -> String {
        let said = parts.compactMap { $0 }.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        let pending = notYet ? [SpineStrings.string("achievements.a11y.notYetPart", locale: locale)] : []
        let mark = isNew ? [SpineStrings.string("achievements.a11y.newSince", locale: locale)] : []
        return (said + pending + mark).joined(separator: ", ")
    }

    /// The quiet names read as one: "Not yet: Anxious, Bitter".
    static func notYet(_ names: [String], locale: Locale) -> String {
        format("achievements.a11y.notYet", locale, names.joined(separator: ", "))
    }

    /// A table row read aloud: the served counts as they are, never against a
    /// target, each marked "reached" where the server's `done` says so.
    static func monthRow(_ month: Page.Month, locale: Locale) -> String {
        func reached(_ text: String, _ done: Bool) -> String {
            done ? format("achievements.a11y.reached", locale, text) : text
        }
        let weeks = month.fullWeeks > 0
            ? format("achievements.a11y.weeksFilled", locale, number(month.weeksFilled, locale: locale),
                     number(month.fullWeeks, locale: locale))
            : SpineStrings.string("achievements.a11y.noFullWeek", locale: locale)
        return format("achievements.a11y.month", locale, monthHeading(month.month, locale: locale),
                      reached(number(month.momentDays, locale: locale), month.done.moments),
                      reached(number(month.pebbleDays, locale: locale), month.done.pebbles),
                      reached(weeks, month.done.weeks))
    }
}
