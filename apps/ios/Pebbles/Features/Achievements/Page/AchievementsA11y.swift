import Foundation

/// The page's VoiceOver labels (#1306, spec §5), assembled from the payload:
/// each card reads as its values (a ring done, or how many of how many; the
/// coverage's meaning; the pebbles line), the disc as the server's lists (on
/// the rim, on the inner disc, on their way) and what is new as a count of the
/// server's `new` flags. The figures themselves are hidden from VoiceOver.
/// Android: `AchievementsA11y`.
enum AchievementsA11y {
    typealias Page = AchievementsPage

    /// "249 days, since Sunday, March 1, 2026".
    static func header(daysKept: Int, since: CalendarDay?, calendar: Calendar, locale: Locale,
                       dateLocale: Locale) -> String {
        let days = AchievementsCopy.number(daysKept, locale: locale) + " "
            + AchievementsCopy.daysUnit(daysKept, locale: locale)
        guard let since else { return days }
        return AchievementsCopy.format("achievements.a11y.header", locale, days,
                                       AchievementsCopy.since(since, calendar: calendar, locale: locale,
                                                              dateLocale: dateLocale))
    }

    /// "Explorer: the first five, done; …; emotions, 23 of 38. 3 new." A ring is
    /// done when `rings_done` says so; an absent section is left out, never read
    /// as 0. "N new" adds the `new` flags of everything the wheel draws: the first
    /// five, the six firsts, the 25th, the domain entries and the emotion seats (spec §5).
    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?,
                         locale: Locale) -> String {
        var rings = [
            ring(.firstFive, explorer, count: explorer.firstFive.filter(\.done).count, of: explorer.firstFive.count, locale),
            ring(.sixFirsts, explorer, count: explorer.sixFirsts.filter(\.done).count, of: explorer.sixFirsts.count, locale)
        ]
        if let twentyFive = explorer.twentyFive {
            rings.append(ring(.twentyFive, explorer, count: twentyFive.count,
                              of: ExplorerWheelGeometry.twentyFiveSeats, locale))
        }
        if let domains { rings.append(ring(.domains, explorer, count: domains.count, of: domains.of, locale)) }
        if let emotions { rings.append(ring(.emotions, explorer, count: emotions.count, of: emotions.of, locale)) }
        // Summed in steps: one long expression defeats the type checker.
        let ownNew = explorer.firstFive.filter(\.isNew).count + explorer.sixFirsts.filter(\.isNew).count
        let quarterNew = explorer.twentyFive?.isNew == true ? 1 : 0
        let seatsNew = emotions?.seats.filter(\.isNew).count ?? 0
        let domainsNew = domains?.entered.filter(\.isNew).count ?? 0
        let fresh = ownNew + quarterNew + seatsNew + domainsNew
        let body = rings.joined(separator: SpineStrings.string("achievements.a11y.join", locale: locale))
        return AchievementsCopy.format("achievements.a11y.explorer", locale, body) + newSuffix(fresh, locale)
    }

    /// "Emotions, 23 of 38. 2 new."
    static func emotions(_ emotions: Page.Emotions, locale: Locale) -> String {
        AchievementsCopy.format("achievements.a11y.emotions", locale, number(emotions.count, locale),
                                number(emotions.of, locale))
            + newSuffix(emotions.seats.filter(\.isNew).count, locale)
    }

    /// "Domains, 14 of 18. <what the coverage means>. 1 new."
    static func domains(_ domains: Page.Domains, locale: Locale) -> String {
        var label = AchievementsCopy.format("achievements.a11y.domains", locale, number(domains.count, locale),
                                            number(domains.of, locale))
        if let meaning = AchievementsCopy.coverage(domains.coverage, locale: locale) { label += " " + meaning }
        return label + newSuffix(domains.entered.filter(\.isNew).count, locale)
    }

    /// "Pebbles. 137 pebbles. Five stones stand; … Glyphs carved: 12. Glyph sales: 3."
    static func pebbles(_ pebbles: Page.Ladder, carved: Page.Ladder?, sales: Page.Ladder?, locale: Locale) -> String {
        var parts = [AchievementsCopy.format("achievements.a11y.pebbles", locale,
                                             AchievementsCopy.pebblesLine(pebbles, locale: locale))]
        if let carved {
            parts.append(AchievementsCopy.format("achievements.a11y.glyphsCarved", locale, number(carved.count, locale)))
        }
        if let sales {
            parts.append(AchievementsCopy.format("achievements.a11y.glyphSales", locale, number(sales.count, locale)))
        }
        let fresh = [pebbles, carved, sales].compactMap { $0 }.map { $0.levels.filter(\.isNew).count }.reduce(0, +)
        return parts.joined(separator: " ") + newSuffix(fresh, locale)
    }

    /// "The year 2026. On the rim: August. On the inner disc: …. On their way: November. <next step>"
    static func year(_ year: Page.Year, locale: Locale) -> String {
        var label = disc(year, locale: locale)
        if let step = year.nextStep { label += " " + AchievementsCopy.nextStep(step, locale: locale) }
        return label
    }

    /// The disc alone, as the server's lists: "The year 2026. On the rim: August. …
    /// On their way: November." It labels the disc wherever it is drawn.
    static func disc(_ year: Page.Year, locale: Locale) -> String {
        let begun = year.months.filter { $0.state != .upcoming }
        return AchievementsCopy.format(
            "achievements.a11y.year", locale, String(year.year),
            names(begun.filter { YearDiscGeometry.level($0) == 2 }, locale),
            names(begun.filter { YearDiscGeometry.level($0) == 1 }, locale),
            names(begun.filter { YearDiscGeometry.level($0) == 0 && $0.progress > 0 }, locale))
    }

    /// "The six firsts: the ceremony is ready."
    static func ready(_ stack: Page.CeremonyStack, locale: Locale) -> String {
        AchievementsCopy.format("achievements.a11y.ready", locale, AchievementsCopy.ceremonyTitle(stack, locale: locale))
    }

    // MARK: - Helpers

    private static func ring(_ ring: Page.Ring, _ explorer: Page.Explorer, count: Int, of: Int,
                             _ locale: Locale) -> String {
        let name = SpineStrings.string("achievements.a11y.ring.\(ring.rawValue)", locale: locale)
        if explorer.ringsDone.contains(ring) { return AchievementsCopy.format("achievements.a11y.ringDone", locale, name) }
        return AchievementsCopy.format("achievements.a11y.ringPart", locale, name, number(count, locale), number(of, locale))
    }

    /// " 3 new." after a label; nothing when nothing is new.
    private static func newSuffix(_ count: Int, _ locale: Locale) -> String {
        guard count > 0 else { return "" }
        return " " + AchievementsCopy.format("achievements.a11y.new", locale, count, number(count, locale))
    }

    private static func names(_ months: [Page.Month], _ locale: Locale) -> String {
        guard !months.isEmpty else { return SpineStrings.string("achievements.a11y.none", locale: locale) }
        return months.map { AchievementsCopy.monthInSentence($0.month, locale: locale) }
            .formatted(.list(type: .and).locale(locale))
    }

    private static func number(_ value: Int, _ locale: Locale) -> String {
        AchievementsCopy.number(value, locale: locale)
    }
}
