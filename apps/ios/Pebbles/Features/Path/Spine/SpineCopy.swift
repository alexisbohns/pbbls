import Foundation

/// The Spine's copy, typed: one function per `path.spine.*` key, so no view
/// spells a key or an argument order. Counts that drive a plural come first.
enum SpineCopy {
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

    // swiftlint:disable function_parameter_count
    /// The stone's and the card's name (A2, A3). An empty `withNames` leaves two
    /// spaces in the template; `tidy` collapses double spaces across the whole sentence, names included.
    static func stoneA11y(
        name: String, date: String, time: String, kind: String, emotion: String, withNames: String, locale: Locale
    ) -> String {
        tidy(SpineStrings.format("path.spine.stoneA11y", locale: locale, name, date, time, kind, emotion, withNames))
    }
    // swiftlint:enable function_parameter_count

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
