import Foundation

/// The achievements page's words (#1306, spec §5): the one place the payload's
/// numbers, tiers and steps become sentences, over `Localizable.xcstrings`'
/// `achievements.*` keys. The language is passed in (views pass
/// `SpineStrings.appLocale`, dates `SpineStrings.dateLocale`), so the tests pin
/// `en` and `fr`. Plurals are the catalog's, number words `NumberWords`'.
/// Choosing a sentence by how many stages stand is wording, not counting: the
/// stages are the ones the server lists. Android: `AchievementsCopy.kt`.
enum AchievementsCopy {
    typealias Page = AchievementsPage

    // MARK: - The header

    /// "since Sunday, March 1, 2026" (the header's small caps set it).
    static func since(_ day: CalendarDay, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        let date = day.date(in: calendar.timeZone) ?? .distantPast
        return format("achievements.header.since", locale,
                      SpineDates.fullDate(date, calendar: calendar, locale: dates(dateLocale)))
    }

    /// The date locale as one plain `language_REGION`: a region carried as an
    /// override is honoured by some templates and not others, so the header's
    /// full date and a card's day could disagree on day/month order.
    static func dates(_ locale: Locale) -> Locale {
        guard let language = locale.language.languageCode?.identifier else { return locale }
        guard let region = locale.region?.identifier else { return Locale(identifier: language) }
        return Locale(identifier: "\(language)_\(region)")
    }

    /// "days", or "day" on the first one.
    static func daysUnit(_ days: Int, locale: Locale) -> String {
        format("achievements.header.days", locale, days)
    }

    // MARK: - The cards

    static func title(_ card: AchievementCard, locale: Locale) -> String {
        SpineStrings.string("achievements.card.\(card.rawValue)", locale: locale)
    }

    static func explorerHint(locale: Locale) -> String {
        SpineStrings.string("achievements.hint.explorer", locale: locale)
    }

    /// "since March 2": a card's served `since` (the emotions' and each ladder's first day).
    static func sinceHint(_ day: CalendarDay, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        let date = day.date(in: calendar.timeZone) ?? .distantPast
        return format("achievements.hint.since", locale,
                      SpineDates.dayMonth(date, calendar: calendar, locale: dates(dateLocale)))
    }

    static func domainsHint(locale: Locale) -> String {
        SpineStrings.string("achievements.hint.domains", locale: locale)
    }

    /// "in progress" on the current year; nothing on a past one.
    static func yearHint(_ year: Page.Year, locale: Locale) -> String? {
        year.isCurrent ? SpineStrings.string("achievements.hint.inProgress", locale: locale) : nil
    }

    /// What the coverage tier means for the insights (D11); nil for a tier this build does not know.
    static func coverage(_ tier: Page.Coverage, locale: Locale) -> String? {
        tier == .unknown ? nil : SpineStrings.string("achievements.coverage.\(tier.rawValue)", locale: locale)
    }

    /// "of 38", "sur 38": under the rosace's count.
    static func rosaceOf(_ of: Int, locale: Locale) -> String {
        format("achievements.rosace.of", locale, number(of, locale: locale))
    }

    // MARK: - Pebbles

    /// "137 pebbles. Five stones stand; the sixth rises toward 250."
    static func pebblesLine(_ ladder: Page.Ladder, locale: Locale) -> String {
        format("achievements.pebbles.kept", locale, ladder.count, number(ladder.count, locale: locale))
            + " " + stones(ladder, locale: locale)
    }

    /// "137 pebbles kept.": the Pebbles sheet's lead.
    static func pebblesLead(_ ladder: Page.Ladder, locale: Locale) -> String {
        format("achievements.pebbles.keptLead", locale, ladder.count, number(ladder.count, locale: locale))
    }

    /// The skyline in words: how many of its stages the server lists as reached,
    /// and the one rising toward `next`.
    static func stones(_ ladder: Page.Ladder, locale: Locale) -> String {
        guard let next = ladder.next else { return SpineStrings.string("achievements.pebbles.top", locale: locale) }
        let target = number(next.threshold, locale: locale)
        let standing = ladder.reachedSteps.count
        if standing >= ladder.ladder.count { return format("achievements.pebbles.all", locale, target) }
        let rising = NumberWords.ordinal(standing + 1, locale: locale, gender: .feminine)
        switch standing {
        case 0:
            return format("achievements.pebbles.none", locale, target)
        case 1:
            return format("achievements.pebbles.one", locale, rising, target)
        default:
            let words = NumberWords.cardinal(standing, locale: locale, gender: .feminine)
            return WeekOpening.sentenceCase(format("achievements.pebbles.some", locale, words, rising, target),
                                            locale: locale)
        }
    }

    /// "1,500", "1 500": a number with the locale's grouping.
    static func number(_ value: Int, locale: Locale) -> String {
        value.formatted(.number.locale(locale))
    }

    // MARK: - The year

    /// "Twelve more days with a moment, one in each full week, bring November to the inner disc."
    static func nextStep(_ step: Page.NextStep, locale: Locale) -> String {
        let month = monthInSentence(step.month, locale: locale)
        let key = "achievements.year.\(step.needs.rawValue)"
        // Out of reach this month, for whichever reason (a full week passed
        // without it, or too few days left): the copy names neither (spec §3.3).
        guard step.reachable else {
            return WeekOpening.sentenceCase(format("\(key).unreachable", locale, month), locale: locale)
        }
        switch step.daysLeft {
        case ...0:
            return format("\(key).keep", locale, month)
        case 1:
            return format("\(key).one", locale, month)
        default:
            let words = NumberWords.cardinal(step.daysLeft, locale: locale)
            return WeekOpening.sentenceCase(format("\(key).some", locale, words, month), locale: locale)
        }
    }

    /// "November", "novembre": a month inside a sentence.
    static func monthInSentence(_ month: Int, locale: Locale) -> String {
        let names = gregorian(locale).monthSymbols
        return names.indices.contains(month - 1) ? names[month - 1] : String(month)
    }

    /// "November", "Novembre": a month heading a row.
    static func monthHeading(_ month: Int, locale: Locale) -> String {
        let names = gregorian(locale).standaloneMonthSymbols
        guard names.indices.contains(month - 1) else { return String(month) }
        return WeekOpening.sentenceCase(names[month - 1], locale: locale)
    }

    // MARK: - The ceremony

    static func ceremonyTitle(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        SpineStrings.string("achievements.ceremony.\(stack.rawValue).title", locale: locale)
    }

    static func ceremonyBody(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        SpineStrings.string("achievements.ceremony.\(stack.rawValue).body", locale: locale)
    }

    /// The unlock moment's card for a pulled ceremony, with the karma the pull
    /// actually paid. Nil karma (the pull's answer was lost or empty, and the
    /// re-read shows `pulled_at`, spec §3.4) plays it without a karma line.
    static func ceremonyCard(_ stack: Page.CeremonyStack, karma: Int?, locale: Locale) -> AchievementMomentCard {
        AchievementMomentCard(id: ceremonySlug(stack), title: ceremonyTitle(stack, locale: locale),
                              description: ceremonyBody(stack, locale: locale), karmaGranted: karma ?? 0)
    }

    /// The `set_complete` catalog row a stack pays from (backend plan Task 1).
    static func ceremonySlug(_ stack: Page.CeremonyStack) -> String {
        switch stack {
        case .emotions: "complete-emotions"
        case .domains: "complete-domains"
        case .sixFirsts: "complete-six-firsts"
        }
    }

    // MARK: - The error

    static func errorTitle(locale: Locale) -> String { SpineStrings.string("achievements.error.title", locale: locale) }
    static func errorBody(locale: Locale) -> String { SpineStrings.string("achievements.error.body", locale: locale) }
    static func retry(locale: Locale) -> String { SpineStrings.string("achievements.error.retry", locale: locale) }

    // MARK: - The page's small labels

    /// "ready": a set whose ceremony can be pulled.
    static func ready(locale: Locale) -> String { SpineStrings.string("achievements.ready", locale: locale) }
    static func glyphsCarved(locale: Locale) -> String { SpineStrings.string("achievements.card.glyphsCarved", locale: locale) }
    static func glyphSales(locale: Locale) -> String { SpineStrings.string("achievements.card.glyphSales", locale: locale) }

    // MARK: - Helpers

    /// The format for `key` in `locale`, its plurals resolved by the catalog's variations.
    static func format(_ key: String, _ locale: Locale, _ arguments: CVarArg...) -> String {
        String(format: SpineStrings.string(key, locale: locale), locale: locale, arguments: arguments)
    }

    private static func gregorian(_ locale: Locale) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return calendar
    }
}
