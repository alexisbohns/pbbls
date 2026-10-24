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
