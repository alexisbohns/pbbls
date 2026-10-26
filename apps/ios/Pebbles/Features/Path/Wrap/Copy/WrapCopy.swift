import Foundation

/// The wraps' copy (#1226; Android: `WrapCopy`): the one place the payloads'
/// numbers, slugs and days become sentences, over `Localizable.xcstrings`'
/// `path.strip.*`, `path.wrap.*` and `path.month.*` keys, which are Android's
/// strings word for word. The language is passed in (views pass
/// `SpineStrings.appLocale`), so the tests pin `en` and `fr`; dates take the
/// Spine's calendar and its date locale.
enum WrapCopy {
    // MARK: - Strip

    static func stripNow(locale: Locale) -> String {
        SpineStrings.string("path.strip.now", locale: locale)
    }

    /// "Week of October 5, 4 moments"; the live week says "This week, from …, so far".
    static func stripWeek(_ week: StripWeek, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        let start = week.weekStart.date(in: calendar.timeZone) ?? .distantPast
        let day = SpineDates.dayMonth(start, calendar: calendar, locale: dateLocale)
        if !week.isFocusable {
            return SpineStrings.format("path.strip.spacerA11y", locale: locale, day)
        }
        let key = week.isLive ? "path.strip.liveWeekA11y" : "path.strip.weekA11y"
        return SpineStrings.format(key, locale: locale, day, week.moments)
    }

    /// "September, the month".
    static func stripMonth(_ month: CalendarMonth, locale: Locale) -> String {
        SpineStrings.format("path.strip.monthA11y", locale: locale, monthName(month, locale: locale))
    }

    // MARK: - The week

    /// "week 41": the week's number, said once, on its line.
    static func week(_ isoWeek: Int, locale: Locale) -> String {
        SpineStrings.format("path.wrap.week", locale: locale, isoWeek)
    }

    /// "Eleven pebbles, five people." / "…, five people so far." / "Nothing yet." / "No pebble yet."
    static func line(pebbles: Int, people: Int, live: Bool, hasMoments: Bool, locale: Locale) -> String {
        if pebbles == 0 {
            return SpineStrings.string(hasMoments ? "path.wrap.lineNoPebble" : "path.wrap.lineNothing",
                                       locale: locale)
        }
        let pebblePart = SpineStrings.format("path.spine.summaryPebbles", locale: locale, pebbles,
                                             words(pebbles, locale: locale))
        let peoplePart = people == 0 ? "" : SpineStrings.format("path.spine.summaryPeople", locale: locale, people,
                                                                 words(people, locale: locale, gender: .feminine))
        let soFar = live ? " " + Self.soFar(locale: locale) : ""
        return WeekOpening.sentenceCase(pebblePart + peoplePart + soFar + ".", locale: locale)
    }

    /// A week page's summary: the wrap's line, "Nothing yet." for an empty week,
    /// nothing until a week with moments has its wrap.
    static func weekSummary(_ wrap: WeekWrap?, isEmpty: Bool, locale: Locale) -> String? {
        if let wrap {
            return line(pebbles: wrap.pebbles, people: wrap.people, live: wrap.isLive, hasMoments: !isEmpty,
                        locale: locale)
        }
        return isEmpty ? line(pebbles: 0, people: 0, live: false, hasMoments: false, locale: locale) : nil
    }

    static func soFar(locale: Locale) -> String { SpineStrings.string("path.wrap.soFar", locale: locale) }

    /// A past week of a month without a pebble (#1241 D12), "No pebble.": a month page is always a completed month.
    static func emptyWeek(locale: Locale) -> String { SpineStrings.string("path.wrap.lineNoPebbles", locale: locale) }

    static func collect(locale: Locale) -> String { SpineStrings.string("path.wrap.collect", locale: locale) }

    static func weightCaption(locale: Locale) -> String {
        SpineStrings.string("path.wrap.weightCaption", locale: locale)
    }

    static func tile(_ tile: WeekTile, locale: Locale) -> String {
        SpineStrings.string("path.wrap.tile.\(tile.rawValue)", locale: locale)
    }

    /// A tile's VoiceOver label, "contents: thirteen."; its hint is `tileHint`.
    static func tileLabel(_ tile: WeekTile, body: String, locale: Locale) -> String {
        SpineStrings.format("path.wrap.tileLabel", locale: locale, Self.tile(tile, locale: locale), body)
    }

    /// "Opens the list.": what a tile does, said after its label.
    static func tileHint(locale: Locale) -> String { SpineStrings.string("path.wrap.tileHint", locale: locale) }

    /// The families in the server's lead order: "Mostly joy, then peace."
    static func emotionsLead(_ families: [String], locale: Locale) -> String {
        switch families.count {
        case 0: return ""
        case 1: return SpineStrings.format("path.wrap.leadMostly", locale: locale, families[0].lowercased(with: locale))
        default:
            return SpineStrings.format("path.wrap.leadMostlyThen", locale: locale, families[0].lowercased(with: locale),
                                       families[1].lowercased(with: locale))
        }
    }

    /// The domains in the server's rank order: "Friends first, then identity and family."
    static func domainsLead(_ domains: [String], locale: Locale) -> String {
        switch domains.count {
        case 0: return ""
        case 1: return SpineStrings.format("path.wrap.domainsFirst", locale: locale, domains[0])
        default:
            let rest = domains.dropFirst().prefix(2).map { $0.lowercased(with: locale) }
            return SpineStrings.format("path.wrap.domainsFirstThen", locale: locale, domains[0],
                                       and(Array(rest), locale: locale))
        }
    }

    /// "Six bright, four even, three heavy."
    static func weightLead(bright: Int, even: Int, heavy: Int, locale: Locale) -> String {
        WeekOpening.sentenceCase(SpineStrings.format("path.wrap.weightLead", locale: locale,
                                                     words(bright, locale: locale), words(even, locale: locale),
                                                     words(heavy, locale: locale)), locale: locale)
    }

    /// "From 8:02 AM to 10:30 PM." over two local decimal hours.
    static func hoursLead(from: Double, to: Double, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        SpineStrings.format("path.wrap.hoursLead", locale: locale,
                            time(from, calendar: calendar, locale: dateLocale),
                            time(to, calendar: calendar, locale: dateLocale))
    }

    static func hoursRange(from: Double, to: Double, calendar: Calendar, locale: Locale, dateLocale: Locale) -> String {
        SpineStrings.format("path.wrap.hoursRange", locale: locale,
                            time(from, calendar: calendar, locale: dateLocale),
                            time(to, calendar: calendar, locale: dateLocale))
    }

    /// "Was someone there for Flag march, Rent or Couldn't sleep?"; the long form
    /// adds how a soul joins.
    static func invitation(_ moments: [String], long: Bool, locale: Locale) -> String {
        let ask = SpineStrings.format("path.wrap.invite", locale: locale, or(moments, locale: locale))
        return long ? ask + " " + SpineStrings.string("path.wrap.inviteMore", locale: locale) : ask
    }

    static func photos(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.wrap.photos", locale: locale, words(count, locale: locale, gender: .feminine), count)
    }

    static func cards(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.wrap.cards", locale: locale, words(count, locale: locale, gender: .feminine), count)
    }

    static func cardsShort(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.wrap.cardsShort", locale: locale, count)
    }

    static func size(_ group: ValenceSizeGroup, locale: Locale) -> String {
        SpineStrings.string("path.wrap.\(group.rawValue)", locale: locale)
    }

    static func polarity(_ polarity: ValencePolarity, locale: Locale) -> String {
        let key: String
        switch polarity {
        case .highlight: key = "path.wrap.bright"
        case .neutral: key = "path.wrap.even"
        case .lowlight: key = "path.wrap.heavy"
        }
        return SpineStrings.string(key, locale: locale)
    }

    /// A local decimal hour on the reader's clock: 21.5 → "9:30 PM" / "21:30".
    static func time(_ hour: Double, calendar: Calendar, locale: Locale) -> String {
        let minutes = min(max(Int((hour * 60).rounded(.down)), 0), 24 * 60 - 1)
        // Any day will do: only the hour and minute print.
        let day = calendar.date(from: DateComponents(year: 2026, month: 1, day: 5, hour: minutes / 60,
                                                     minute: minutes % 60)) ?? .distantPast
        return SpineDates.time(day, calendar: calendar, locale: locale)
    }

    // MARK: - The month page

    /// Rows a month section shows before its *more* (spec §3.5: "shows 5, then more").
    static let shown = 5

    /// "part the tenth" / "dixième partie": the month as a chapter of the year.
    static func monthKicker(_ month: CalendarMonth, locale: Locale) -> String {
        SpineStrings.format("path.month.kicker", locale: locale,
                            NumberWords.ordinal(month.month, locale: locale, gender: .feminine))
    }

    static func personsInOrderLabel(locale: Locale) -> String {
        SpineStrings.string("path.month.personsLabel", locale: locale)
    }

    /// "Printed in joy, peace & pride, with anger, sadness & fear, on family,
    /// identity & friends." The inks are the server's ranked families (three,
    /// then the rest), the papers its top domains.
    static func colophon(inks: [String], papers: [String], locale: Locale) -> String {
        var parts: [String] = []
        if !inks.isEmpty {
            parts.append(SpineStrings.format("path.month.printed", locale: locale, amp(Array(inks.prefix(3)), locale)))
        }
        if inks.count > 3 {
            parts.append(SpineStrings.format("path.month.printedWith", locale: locale,
                                             amp(Array(inks.dropFirst(3)), locale)))
        }
        if !papers.isEmpty {
            parts.append(SpineStrings.format("path.month.printedOn", locale: locale, amp(papers, locale)))
        }
        return WeekOpening.sentenceCase(parts.joined(separator: ", ") + ".", locale: locale)
    }

    static func weeksHeader(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.month.weeks", locale: locale, words(count, locale: locale, gender: .feminine), count)
    }

    static func weeksHint(_ month: CalendarMonth, locale: Locale) -> String {
        SpineStrings.format("path.month.byWeek", locale: locale, monthName(month, locale: locale))
    }

    static func milestonesHeader(_ count: Int, locale: Locale) -> String {
        SpineStrings.format("path.month.milestones", locale: locale, words(count, locale: locale), count)
    }

    static func milestonesHint(locale: Locale) -> String { SpineStrings.string("path.month.largest", locale: locale) }

    static func clockwiseHint(locale: Locale) -> String { SpineStrings.string("path.month.clockwise", locale: locale) }

    /// The *more* link: "all six persons" when the list folds, "the persons, in detail" when it does not.
    static func morePersons(_ total: Int, locale: Locale) -> String {
        total > shown
            ? SpineStrings.format("path.month.morePersons", locale: locale,
                                  words(total, locale: locale, gender: .feminine))
            : SpineStrings.string("path.month.personsDetail", locale: locale)
    }

    static func moreDomains(_ total: Int, locale: Locale) -> String {
        total > shown
            ? SpineStrings.format("path.month.moreDomains", locale: locale, words(total, locale: locale))
            : SpineStrings.string("path.month.domainsDetail", locale: locale)
    }

    /// "new since September" / "pas vu·e en septembre".
    static func newSince(_ previous: CalendarMonth, locale: Locale) -> String {
        SpineStrings.format("path.month.newSince", locale: locale, monthInSentence(previous, locale: locale))
    }

    static func nobody(locale: Locale) -> String { SpineStrings.string("path.month.nobody", locale: locale) }

    /// "5–11 Oct": a week's days inside the month, from the server's 0-based indices.
    static func range(_ month: CalendarMonth, from: Int, to: Int, locale: Locale) -> String {
        SpineStrings.format("path.month.range", locale: locale, from + 1, to + 1, monthAbbreviation(month, locale))
    }

    /// "Mon 5 Oct": a milestone's day.
    static func dateline(_ day: CalendarDay, locale: Locale) -> String {
        let calendar = calendar(locale)
        let date = day.date(in: TimeZone(secondsFromGMT: 0) ?? .gmt) ?? .distantPast
        var utc = calendar
        utc.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
        var weekday = utc.shortWeekdaySymbols[utc.component(.weekday, from: date) - 1]
        if weekday.hasSuffix(".") { weekday.removeLast() }
        return "\(weekday) \(day.day) \(monthAbbreviation(CalendarMonth(day), locale))"
    }

    static func viewCircle(locale: Locale) -> String { SpineStrings.string("path.month.viewCircle", locale: locale) }

    static func viewCalendar(locale: Locale) -> String {
        SpineStrings.string("path.month.viewCalendar", locale: locale)
    }

    /// "Mara five times, Nadia once. Inès and Jules new since September." over the server's ranking.
    static func personsLead(top: [(name: String, count: Int)], fresh: [String], previous: CalendarMonth,
                            locale: Locale) -> String {
        let counts = top.prefix(2).map { person in
            // "fois" is feminine: "vingt et une fois".
            SpineStrings.format("path.month.times", locale: locale, person.name,
                                words(person.count, locale: locale, gender: .feminine), person.count)
        }
        let first = counts.isEmpty ? "" : counts.joined(separator: ", ") + "."
        let second = fresh.isEmpty ? "" : " " + SpineStrings.format("path.month.newNames", locale: locale,
                                                                     and(fresh, locale: locale),
                                                                     monthInSentence(previous, locale: locale))
        return (first + second).trimmingCharacters(in: .whitespaces)
    }

    /// "Eleven emotions in six families, weighed by intensity."
    static func emotionsLead(emotions: Int, families: Int, locale: Locale) -> String {
        WeekOpening.sentenceCase(SpineStrings.format("path.month.emotionsLead", locale: locale,
                                                     words(emotions, locale: locale, gender: .feminine),
                                                     words(families, locale: locale, gender: .feminine)),
                                 locale: locale)
    }

    /// "Seven domains; family in seven pebbles."
    static func domainsLead(domains: Int, top: String, topCount: Int, locale: Locale) -> String {
        WeekOpening.sentenceCase(SpineStrings.format("path.month.domainsLead", locale: locale,
                                                     words(domains, locale: locale), top.lowercased(with: locale),
                                                     words(topCount, locale: locale), topCount),
                                 locale: locale)
    }

    /// A month's name inside a sentence: "September" in English, "septembre" in French.
    static func monthInSentence(_ month: CalendarMonth, locale: Locale) -> String {
        calendar(locale).standaloneMonthSymbols[month.month - 1]
    }

    /// "Oct" / "Oct": the abbreviation a range and a dateline print, its period dropped, capitalised.
    private static func monthAbbreviation(_ month: CalendarMonth, _ locale: Locale) -> String {
        var short = calendar(locale).shortMonthSymbols[month.month - 1]
        if short.hasSuffix(".") { short.removeLast() }
        return short.prefix(1).uppercased(with: locale) + short.dropFirst()
    }

    private static func amp(_ items: [String], _ locale: Locale) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        default:
            return SpineStrings.format("path.month.amp", locale: locale, items.dropLast().joined(separator: ", "),
                                       items[items.count - 1])
        }
    }

    // MARK: - Months

    /// "October", "Octobre": the month standing alone, capitalised.
    static func monthName(_ month: CalendarMonth, locale: Locale) -> String {
        let name = calendar(locale).standaloneMonthSymbols[month.month - 1]
        return name.prefix(1).uppercased(with: locale) + name.dropFirst()
    }

    /// "Oct", "oct.": the strip's label for a month.
    static func monthShort(_ month: CalendarMonth, locale: Locale) -> String {
        calendar(locale).shortStandaloneMonthSymbols[month.month - 1]
    }

    // MARK: - Words and lists

    private static func words(_ n: Int, locale: Locale, gender: NumberWords.Gender = .masculine) -> String {
        NumberWords.cardinal(n, locale: locale, gender: gender)
    }

    /// "a", "a and b", "a, b and c".
    static func and(_ items: [String], locale: Locale) -> String { joined(items, key: "path.wrap.listAnd", locale) }

    static func or(_ items: [String], locale: Locale) -> String { joined(items, key: "path.wrap.listOr", locale) }

    private static func joined(_ items: [String], key: String, _ locale: Locale) -> String {
        switch items.count {
        case 0: return ""
        case 1: return items[0]
        default:
            let head = items.dropLast().joined(separator: ", ")
            return SpineStrings.format(key, locale: locale, head, items[items.count - 1])
        }
    }

    private static func calendar(_ locale: Locale) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return calendar
    }
}
