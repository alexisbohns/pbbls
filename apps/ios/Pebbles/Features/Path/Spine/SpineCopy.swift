import Foundation

/// The Spine's copy, typed: one function per `path.spine.*` key, so no view
/// spells a key or an argument order. Counts that drive a plural come first.
enum SpineCopy {
    static func kicker(ordinal: String, year: Int, locale: Locale) -> String {
        SpineStrings.format("path.spine.kicker", locale: locale, ordinal, String(year))
    }

    static func title(_ title: WeekTitle, locale: Locale) -> String {
        switch title {
        case let .sameMonth(from, to, month):
            return SpineStrings.format("path.spine.titleSameMonth", locale: locale, from, to, month)
        case let .crossMonth(from, fromMonth, to, toMonth):
            return SpineStrings.format("path.spine.titleCrossMonth", locale: locale, from, fromMonth, to, toMonth)
        }
    }

    /// "Eleven pebbles, five people." The web's ICU `=0` branches are explicit
    /// here: no pebbles reads its own words, no people drops the clause.
    static func summary(pebbles: Int, people: Int, locale: Locale) -> String {
        let words = WeekOpening.summaryWords(pebbles: pebbles, people: people, locale: locale)
        let pebblesPart = pebbles == 0
            ? SpineStrings.string("path.spine.summaryNoPebbles", locale: locale)
            : SpineStrings.format("path.spine.summaryPebbles", locale: locale, pebbles, words.pebblesWords)
        let peoplePart = people == 0
            ? ""
            : SpineStrings.format("path.spine.summaryPeople", locale: locale, people, words.peopleWords)
        return WeekOpening.sentenceCase(pebblesPart + peoplePart + ".", locale: locale)
    }

    static func persons(locale: Locale) -> String { SpineStrings.string("path.spine.persons", locale: locale) }

    /// "and six others": the persons' fold.
    static func personsMore(count: Int, locale: Locale) -> String {
        let words = NumberWords.cardinal(count, locale: locale)
        return SpineStrings.format("path.spine.personsMore", locale: locale, count, words)
    }

    static func contents(locale: Locale) -> String { SpineStrings.string("path.spine.contents", locale: locale) }
    static func withWord(locale: Locale) -> String { SpineStrings.string("path.spine.with", locale: locale) }

    /// "With Mara and Elio." for VoiceOver; `names` is already listed for the locale.
    static func withNames(_ names: String, locale: Locale) -> String {
        names.isEmpty ? "" : SpineStrings.format("path.spine.withNames", locale: locale, names)
    }

    /// "& twelve others" in a people line.
    static func others(count: Int, words: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.others", locale: locale, count, words)
    }

    static func still(locale: Locale) -> String { SpineStrings.string("path.spine.still", locale: locale) }
    static func untitled(locale: Locale) -> String { SpineStrings.string("path.spine.untitled", locale: locale) }
    static func photoUnavailable(locale: Locale) -> String {
        SpineStrings.string("path.spine.photoUnavailable", locale: locale)
    }

    static func weekEnds(ordinal: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.weekEnds", locale: locale, ordinal)
    }

    static func nextBegins(ordinal: String, day: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.nextBegins", locale: locale, ordinal, day)
    }

    /// "week 35": the masthead's and the mouth's link, naming the pager's neighbour (D9).
    static func weekLink(_ number: Int, locale: Locale) -> String {
        SpineStrings.format("path.spine.weekLink", locale: locale, number)
    }

    static func dayDots(count: Int, days: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.dayDots", locale: locale, count, days)
    }

    /// The stone's and the card's name (A2, A3). An empty `withNames` leaves two
    /// spaces in the template; they close to one.
    static func stoneA11y(
        name: String, date: String, time: String, kind: String, emotion: String, withNames: String, locale: Locale
    ) -> String {
        tidy(SpineStrings.format("path.spine.stoneA11y", locale: locale, name, date, time, kind, emotion, withNames))
    }

    static func blankA11y(name: String, date: String, time: String, withNames: String, locale: Locale) -> String {
        tidy(SpineStrings.format("path.spine.blankA11y", locale: locale, name, date, time, withNames))
    }

    static func contentsHint(locale: Locale) -> String {
        SpineStrings.string("path.spine.contentsHint", locale: locale)
    }

    static func feedA11y(range: String, locale: Locale) -> String {
        SpineStrings.format("path.spine.feedA11y", locale: locale, range)
    }

    static func glanceA11y(locale: Locale) -> String { SpineStrings.string("path.spine.glanceA11y", locale: locale) }

    /// "Large gem", "Petite pierre de lave": the stone's kind by its size and polarity.
    static func stoneKind(_ valence: Valence, locale: Locale) -> String {
        let key = "path.spine.stone.\(valence.sizeGroup.rawValue).\(valence.polarity.rawValue)"
        return SpineStrings.string(key, locale: locale)
    }

    private static func tidy(_ text: String) -> String {
        text.replacingOccurrences(of: "  ", with: " ")
    }
}
