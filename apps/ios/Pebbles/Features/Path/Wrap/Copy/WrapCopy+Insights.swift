import Foundation

/// The insights' words (#1226, D11–D12), Android's `WrapCopy` tone and hours
/// functions word for word; their strings are the catalogue's `path.insights.*`,
/// `path.tone.*` and `path.hours.*`. Times are written on the reader's clock.
extension WrapCopy {
    static func insights(locale: Locale) -> String {
        SpineStrings.string("path.insights.title", locale: locale)
    }

    static func insightsHint(locale: Locale) -> String {
        SpineStrings.string("path.insights.hint", locale: locale)
    }

    static func toneTitle(locale: Locale) -> String {
        SpineStrings.string("path.tone.title", locale: locale)
    }

    static func toneHint(locale: Locale) -> String {
        SpineStrings.string("path.tone.hint", locale: locale)
    }

    /// "Bright outweighed heavy two to one; it opened heavy and closed bright." from the server's verdict.
    static func toneLine(_ verdict: ToneVerdict, first: ValencePolarity?, last: ValencePolarity?,
                         locale: Locale) -> String {
        let key: String?
        switch verdict {
        case .brightMoreThanTwoToOne: key = "path.tone.brightMoreThanTwoToOne"
        case .brightTwoToOne: key = "path.tone.brightTwoToOne"
        case .balance: key = "path.tone.balance"
        case .heavyOutweighed: key = "path.tone.heavyOutweighed"
        case .nothingHeavy: key = "path.tone.nothingHeavy"
        case .nothingBright: key = "path.tone.nothingBright"
        case .allEven: key = "path.tone.allEven"
        case .unknown: key = nil
        }
        let lead = key.map { SpineStrings.string($0, locale: locale) }
        let arc = first.flatMap { first in
            last.map { last in
                SpineStrings.format("path.tone.openedClosed", locale: locale, polarity(first, locale: locale),
                                    polarity(last, locale: locale))
            }
        }
        return WeekOpening.sentenceCase([lead, arc].compactMap { $0 }.joined(separator: "; ") + ".", locale: locale)
    }

    static func hoursTitle(locale: Locale) -> String {
        SpineStrings.string("path.hours.title", locale: locale)
    }

    static func hoursHint(locale: Locale) -> String {
        SpineStrings.string("path.hours.hint", locale: locale)
    }

    static func hoursSub(locale: Locale) -> String {
        SpineStrings.string("path.hours.sub", locale: locale)
    }

    /// "of 24 collected", under the busiest window's count.
    static func hoursOf(_ total: Int, locale: Locale) -> String {
        SpineStrings.format("path.hours.of", locale: locale, total)
    }

    /// The busiest four hours: "8:00 PM – midnight" / "20:00 – minuit".
    static func hoursWindow(_ start: Int, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        SpineStrings.format("path.hours.window", locale: locale,
                            hourMark(start, calendar: calendar, locale: locale, dateLocale: dateLocale),
                            hourMark(start + 4, calendar: calendar, locale: locale, dateLocale: dateLocale))
    }

    // swiftlint:disable function_parameter_count
    /// The dial in words: "9 of 24 pebbles collected between 8:00 PM and midnight."
    static func hoursDescription(count: Int, total: Int, start: Int, calendar: Calendar, locale: Locale,
                                 dateLocale: Locale) -> String {
        SpineStrings.format("path.hours.a11y", locale: locale, count, total,
                            hourMark(start, calendar: calendar, locale: locale, dateLocale: dateLocale),
                            hourMark(start + 4, calendar: calendar, locale: locale, dateLocale: dateLocale))
    }
    // swiftlint:enable function_parameter_count

    private static func hourMark(_ hour: Int, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        hour % 24 == 0
            ? SpineStrings.string("path.hours.midnight", locale: locale)
            : time(Double(hour % 24), calendar: calendar, locale: dateLocale)
    }
}
