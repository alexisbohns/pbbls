import Foundation

/// A week's title in words (#1130 R18).
enum WeekTitle: Equatable {
    case sameMonth(from: String, to: String, month: String)
    case crossMonth(from: String, fromMonth: String, to: String, toMonth: String)
}

/// The words a week opens and closes with, a port of
/// `apps/web/lib/utils/spine/week-opening.ts`. A week is feminine in French
/// ("la quarante et unième semaine"). `previousNumber` and `nextNumber` are the
/// calendar's: the links name the pager's neighbours instead (D9, `WeekNeighbour`),
/// and only the mouth's "begins on Monday the D" reads `nextOrdinal` / `nextMonday`.
struct WeekWords: Equatable {
    let number: Int
    let year: Int
    let ordinal: String
    let title: WeekTitle
    let previousNumber: Int
    let nextNumber: Int
    let nextOrdinal: String
    let nextMonday: String
}

enum WeekOpening {
    /// - Parameter calendar: an ISO-8601 calendar in the reader's zone.
    static func weekWords(weekStart: Date, calendar: Calendar, locale: Locale) -> WeekWords {
        let sunday = LocalDays.adding(6, to: weekStart, calendar: calendar)
        let nextMonday = LocalDays.adding(7, to: weekStart, calendar: calendar)
        let previousMonday = LocalDays.adding(-7, to: weekStart, calendar: calendar)
        let number = calendar.component(.weekOfYear, from: weekStart)
        let nextNumber = calendar.component(.weekOfYear, from: nextMonday)
        let from = NumberWords.dayOfMonth(calendar.component(.day, from: weekStart), locale: locale)
        let to = NumberWords.dayOfMonth(calendar.component(.day, from: sunday), locale: locale)
        let title: WeekTitle
        if calendar.component(.month, from: weekStart) == calendar.component(.month, from: sunday) {
            title = .sameMonth(from: from, to: to, month: SpineDates.month(sunday, calendar: calendar, locale: locale))
        } else {
            title = .crossMonth(
                from: from, fromMonth: SpineDates.month(weekStart, calendar: calendar, locale: locale),
                to: to, toMonth: SpineDates.month(sunday, calendar: calendar, locale: locale)
            )
        }
        return WeekWords(
            number: number,
            year: calendar.component(.yearForWeekOfYear, from: weekStart),
            ordinal: NumberWords.ordinal(number, locale: locale, gender: .feminine),
            title: title,
            previousNumber: calendar.component(.weekOfYear, from: previousMonday),
            nextNumber: nextNumber,
            nextOrdinal: NumberWords.ordinal(nextNumber, locale: locale, gender: .feminine),
            nextMonday: NumberWords.dayOfMonth(calendar.component(.day, from: nextMonday), locale: locale)
        )
    }

    /// Pebbles are masculine in French ("un galet"), people feminine ("une personne").
    static func summaryWords(pebbles: Int, people: Int, locale: Locale) -> (pebblesWords: String, peopleWords: String) {
        (NumberWords.cardinal(pebbles, locale: locale, gender: .masculine),
         NumberWords.cardinal(people, locale: locale, gender: .feminine))
    }

    /// "eleven pebbles…" → "Eleven pebbles…": the summary opens a sentence.
    static func sentenceCase(_ text: String, locale: Locale) -> String {
        guard let first = text.first else { return text }
        return String(first).uppercased(with: locale) + text.dropFirst()
    }

    /// "m t w t f s s", "l m m j v s d": the persons' header, Monday first.
    static func weekdayLetters(weekStart: Date, calendar: Calendar, locale: Locale) -> [String] {
        (0..<7).map { index in
            let day = LocalDays.adding(index, to: weekStart, calendar: calendar)
            return SpineDates.narrowWeekday(day, calendar: calendar, locale: locale).lowercased(with: locale)
        }
    }

    /// The days a person's dots mark, as VoiceOver reads them: "Monday 5, Tuesday 6,
    /// and Thursday 8". Joined by hand (the web's `Intl.ListFormat` with the serial
    /// comma outside French): `ListFormatter` follows the region, not the language.
    static func dotDays(
        _ days: [Bool], weekStart: Date, calendar: Calendar, locale: Locale
    ) -> (count: Int, days: String) {
        let named = days.enumerated().filter { $0.element }.map { index, _ in
            let day = LocalDays.adding(index, to: weekStart, calendar: calendar)
            let weekday = SpineDates.weekday(day, calendar: calendar, locale: locale)
            return "\(weekday) \(calendar.component(.day, from: day))"
        }
        return (named.count, join(named, locale: locale))
    }

    private static func join(_ items: [String], locale: Locale) -> String {
        let french = NumberWords.isFrench(locale)
        let and = french ? " et " : " and "
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        case 2: return items[0] + and + items[1]
        default: return items.dropLast().joined(separator: ", ") + (french ? and : "," + and) + (items.last ?? "")
        }
    }
}
