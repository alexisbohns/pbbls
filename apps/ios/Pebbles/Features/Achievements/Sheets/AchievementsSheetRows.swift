import Foundation

/// One register row (the board's `.r`): a mark, a name, a line under it, a
/// date, the new dot. A row that opens something is a button of at least 44 pt;
/// it is read aloud as `accessibilityLabel`.
struct RegisterRowModel: Equatable, Identifiable {
    enum Mark: Equatable {
        case firstFive(HomeChecklist.Task)
        case sixFirst(AchievementsPage.StoneKind)
        case twentyFive
        /// A crossing pebble's seal, in its family's ink.
        case pebble(Valence, family: String?)
        case domain
        case stage(index: Int, fill: Double)
        case rosace
        case ghost
        case none
    }

    enum Opens: Equatable {
        case moment(UUID)
        case sheet(AchievementSheet)
    }

    enum Style: Equatable {
        /// Small-caps name.
        case name
        /// A milestone's number, reached; also an earlier year.
        case number
        /// The next milestone's number, in secondary ink.
        case next
        /// A milestone still ahead: its number alone, light.
        case ghostNumber
    }

    let id: String
    let mark: Mark
    let name: String
    var sub: String?
    /// The italic line of the next milestone ("113 to go").
    var next: String?
    var date: String?
    var isNew = false
    /// A domain's place in the order they came ("No. 14").
    var number: String?
    var style: Style = .name
    var opens: Opens?
    let accessibilityLabel: String
}

/// A dated section (the board's `.lh` and its rows).
struct RegisterSection: Equatable, Identifiable {
    let id: String
    let title: String
    /// The family whose hue the title takes (the Emotions sheet); nil for an unknown one.
    var family: String?
    var note: String?
    var rows: [RegisterRowModel] = []
    /// What is not yet, as dotted names, read aloud as one.
    var notYet: [String] = []
    /// A closing line ("then every 500", "The buyer is never named.").
    var trailing: String?
    /// A quiet line in place of rows ("The 25th pebble is still to come.").
    var quiet: String?
}

/// One gauge of the year table (the board's `cell`): the served count, an "/n"
/// for the weeks, its fill (nil when the server sent no target), and the done
/// style, which is the server's `done` alone.
struct YearCell: Equatable {
    let value: String
    let target: String?
    let fill: Double?
    let isDone: Bool
    /// The pebbles gauge wears the month's hue; moments and weeks stay ink.
    let usesHue: Bool
}

struct YearRowModel: Equatable, Identifiable {
    var id: Int { month.month }
    let month: AchievementsPage.Month
    let name: String
    let moments: YearCell
    let pebbles: YearCell
    let weeks: YearCell
    let accessibilityLabel: String
}

/// The five sheets' rows (#1306), built from the payload in its own order, as
/// Android's sheets read it: what this device cannot name keeps its place under
/// a fallback ("A first", "An emotion", "A domain", "Other emotions"), every
/// date is the served `on`, and a row opens its moment only when the server sent
/// a moment id. Nothing is sorted, and no count is set against a target.
enum AchievementsSheetRows {
    typealias Page = AchievementsPage
    typealias Copy = AchievementsCopy

    struct Context {
        let lookups: WrapLookups
        let calendar: Calendar
        let locale: Locale
        let dateLocale: Locale

        func day(_ day: CalendarDay?) -> String? {
            day.map { Copy.day($0, calendar: calendar, dateLocale: dateLocale) }
        }
    }

    // MARK: - Explorer

    static func explorer(_ explorer: Page.Explorer, emotions: Page.Emotions?, domains: Page.Domains?,
                         _ ctx: Context) -> [RegisterSection] {
        let locale = ctx.locale
        let five = explorer.firstFive.enumerated().map { index, item in
            let name = Copy.firstFiveTitle(item.task, locale: locale)
            let sub = item.subjectId != nil || item.subjectName != nil
                ? Copy.momentName(item.subjectName, locale: locale) : nil
            let date = ctx.day(item.on)
            let opensMoment = item.subjectKind == .moment || item.subjectKind == .pebble
            return RegisterRowModel(
                id: "five-\(index)", mark: item.done ? item.task.map { .firstFive($0) } ?? .ghost : .ghost,
                name: name, sub: sub, date: date, isNew: item.isNew,
                opens: opensMoment ? item.subjectId.map { .moment($0) } : nil,
                accessibilityLabel: Copy.row([name, sub, date], isNew: item.isNew, locale: locale))
        }
        let six = explorer.sixFirsts.enumerated().map { index, first in
            let name = Copy.kind(first.kind, locale: locale)
            let sub = first.pebble.map { Copy.momentName($0.name, locale: locale) }
            let date = ctx.day(first.on)
            return RegisterRowModel(
                id: "six-\(index)", mark: first.done ? first.kind.map { .sixFirst($0) } ?? .ghost : .ghost,
                name: name, sub: sub, date: date, isNew: first.isNew, opens: opens(first.pebble),
                accessibilityLabel: Copy.row([name, sub, date], isNew: first.isNew, locale: locale))
        }
        var sections = [
            RegisterSection(id: "first-five", title: Copy.sheet("firstFive", locale: locale),
                            note: Copy.ofWords(explorer.firstFive.filter(\.done).count, explorer.firstFive.count,
                                               locale: locale),
                            rows: five),
            RegisterSection(id: "six-firsts", title: Copy.sheet("sixFirsts", locale: locale),
                            note: Copy.ofWords(explorer.sixFirsts.filter(\.done).count, explorer.sixFirsts.count,
                                               locale: locale),
                            rows: six)
        ]
        if let twentyFive = explorer.twentyFive {
            var section = RegisterSection(
                id: "twenty-five", title: Copy.sheet("twentyFive", locale: locale),
                note: Copy.ofWords(twentyFive.count, ExplorerWheelGeometry.twentyFiveSeats, locale: locale))
            if twentyFive.done {
                let name = Copy.sheet("twentyFifth", locale: locale)
                let sub = twentyFive.pebble.map { Copy.momentName($0.name, locale: locale) }
                let date = ctx.day(twentyFive.on)
                section.rows = [RegisterRowModel(
                    id: "the-25th", mark: .twentyFive, name: name, sub: sub, date: date, isNew: twentyFive.isNew,
                    opens: opens(twentyFive.pebble),
                    accessibilityLabel: Copy.row([name, sub, date], isNew: twentyFive.isNew, locale: locale))]
            } else {
                section.quiet = Copy.sheet("twentyFiveWaiting", locale: locale)
            }
            sections.append(section)
        }
        var outer: [RegisterRowModel] = []
        if let domains {
            let name = Copy.title(.domains, locale: locale)
            let count = Copy.countOf(domains.count, domains.of, locale: locale)
            outer.append(RegisterRowModel(id: "open-domains", mark: .domain, name: name, sub: count,
                                          opens: .sheet(.domains),
                                          accessibilityLabel: Copy.row([name, count], locale: locale)))
        }
        if let emotions {
            let name = Copy.title(.emotions, locale: locale)
            let count = Copy.countOf(emotions.count, emotions.of, locale: locale)
            outer.append(RegisterRowModel(id: "open-emotions", mark: .rosace, name: name, sub: count,
                                          opens: .sheet(.emotions),
                                          accessibilityLabel: Copy.row([name, count], locale: locale)))
        }
        if !outer.isEmpty {
            sections.append(RegisterSection(id: "domains-emotions", title: Copy.sheet("domainsEmotions", locale: locale),
                                            rows: outer))
        }
        return sections
    }

    // MARK: - Emotions

    /// The server's families in its order: each with its met emotions by the
    /// day they were met, then the rest as names.
    static func emotions(_ emotions: Page.Emotions, _ ctx: Context) -> [RegisterSection] {
        let locale = ctx.locale
        let unknownEmotion = Copy.sheet("unknownEmotion", locale: locale)
        func name(_ seat: Page.Seat) -> String {
            seat.emotionId.flatMap(ctx.lookups.emotion)?.localizedName ?? unknownEmotion
        }
        return emotions.families.enumerated().map { index, family in
            let title = family.family.flatMap(ctx.lookups.family)?.localizedName
                ?? Copy.sheet("unknownFamily", locale: locale)
            let rows = family.met.compactMap(emotions.seat).map { seat in
                let title = name(seat)
                let sub = seat.pebble.map { Copy.momentName($0.name, locale: locale) }
                let date = ctx.day(seat.on)
                return RegisterRowModel(
                    id: "seat-\(seat.seat)", mark: .pebble(seat.pebble?.valence ?? .neutralMedium, family: seat.family),
                    name: title, sub: sub, date: date, isNew: seat.isNew, opens: opens(seat.pebble),
                    accessibilityLabel: Copy.row([title, sub, date], isNew: seat.isNew, locale: locale))
            }
            return RegisterSection(
                id: "family-\(index)", title: title, family: family.family,
                note: Copy.countOf(family.met.count, family.met.count + family.notYet.count, locale: locale),
                rows: rows, notYet: family.notYet.compactMap(emotions.seat).map(name))
        }
    }

    // MARK: - Domains

    /// The register newest first as served, numbered by arrival; then not yet.
    static func domains(_ domains: Page.Domains, _ ctx: Context) -> [RegisterSection] {
        let locale = ctx.locale
        let unknown = Copy.sheet("unknownDomain", locale: locale)
        func name(_ id: String?) -> String { id.flatMap(ctx.lookups.domain)?.localizedName ?? unknown }
        let total = domains.entered.count
        let rows = domains.entered.enumerated().map { index, entry in
            let number = Copy.registerNumber(total - index, locale: locale)
            let title = name(entry.domainId)
            let sub = entry.pebble.map { Copy.momentName($0.name, locale: locale) }
            let date = ctx.day(entry.on)
            return RegisterRowModel(
                id: "entry-\(index)", mark: .domain, name: title, sub: sub, date: date, isNew: entry.isNew,
                number: number, opens: opens(entry.pebble),
                accessibilityLabel: Copy.row([number, title, sub, date], isNew: entry.isNew, locale: locale))
        }
        var sections = [RegisterSection(id: "entered", title: Copy.sheet("entered", locale: locale),
                                        note: Copy.sheet("newestFirst", locale: locale), rows: rows)]
        if !domains.notYet.isEmpty {
            sections.append(RegisterSection(id: "not-yet", title: Copy.sheet("notYet", locale: locale),
                                            notYet: domains.notYet.map(name)))
        }
        return sections
    }

    // MARK: - Pebbles

    /// The Milestones note: how many of the drawn stages stand, in words.
    static func milestonesNote(_ ladder: Page.Ladder, locale: Locale) -> String {
        let steps = ladder.ladder.prefix(SkylineGeometry.stages.count)
        return Copy.ofWords(steps.filter(ladder.isReached).count, steps.count, locale: locale)
    }

    /// A stack's milestones: the levels the server lists (dated, opening their
    /// pebble), its next (what is left, never a date), the ladder's other steps,
    /// then the ladder's tail.
    static func ladder(_ ladder: Page.Ladder, id: String, title: String, note: String?, trailing: String? = nil,
                       _ ctx: Context) -> RegisterSection {
        let locale = ctx.locale
        func stage(_ threshold: Int) -> Int { ladder.ladder.firstIndex(of: threshold) ?? max(ladder.ladder.count - 1, 0) }
        var rows = ladder.levels.map { level in
            let name = Copy.number(level.threshold, locale: locale)
            let sub = level.pebble.map { Copy.momentName($0.name, locale: locale) }
            let date = ctx.day(level.on)
            return RegisterRowModel(
                id: "level-\(level.threshold)", mark: .stage(index: stage(level.threshold), fill: 1), name: name,
                sub: sub, date: date, isNew: level.isNew, style: .number, opens: opens(level.pebble),
                accessibilityLabel: Copy.row([name, sub, date], isNew: level.isNew, locale: locale))
        }
        if let next = ladder.next {
            let name = Copy.number(next.threshold, locale: locale)
            let left = Copy.toGo(next.left, locale: locale)
            rows.append(RegisterRowModel(id: "next-\(next.threshold)",
                                         mark: .stage(index: stage(next.threshold), fill: next.progress),
                                         name: name, next: left, style: .next,
                                         accessibilityLabel: Copy.row([name, left], locale: locale)))
        }
        for (index, step) in ladder.ladder.enumerated() where !ladder.isReached(step) && step != ladder.next?.threshold {
            let name = Copy.number(step, locale: locale)
            rows.append(RegisterRowModel(id: "ahead-\(step)", mark: .stage(index: index, fill: 0), name: name,
                                         style: .ghostNumber, accessibilityLabel: name))
        }
        return RegisterSection(id: id, title: title, note: note, rows: rows,
                               trailing: trailing ?? ladder.thenEvery.map { Copy.thenEvery($0, locale: locale) })
    }

    // MARK: - The year

    /// The table: the months the server lists, in its order (newest first). Each
    /// count is the served one, uncapped; a gauge fills by it over the served
    /// target (capped only in the drawing), and its done style is `done`.
    static func months(_ year: Page.Year, _ ctx: Context) -> [YearRowModel] {
        let locale = ctx.locale
        func fill(_ count: Int, _ target: Int?) -> Double? {
            target.flatMap { $0 > 0 ? min(max(Double(count) / Double($0), 0), 1) : nil }
        }
        return year.listed.compactMap(year.month).map { month in
            let weeks = month.fullWeeks > 0
                ? YearCell(value: Copy.number(month.weeksFilled, locale: locale),
                           target: "/" + Copy.number(month.fullWeeks, locale: locale),
                           fill: fill(month.weeksFilled, month.fullWeeks), isDone: month.done.weeks, usesHue: false)
                : YearCell(value: "—", target: nil, fill: nil, isDone: month.done.weeks, usesHue: false)
            return YearRowModel(
                month: month, name: Copy.monthHeading(month.month, locale: locale),
                moments: YearCell(value: Copy.number(month.momentDays, locale: locale), target: nil,
                                  fill: fill(month.momentDays, year.daysTarget), isDone: month.done.moments,
                                  usesHue: false),
                pebbles: YearCell(value: Copy.number(month.pebbleDays, locale: locale), target: nil,
                                  fill: fill(month.pebbleDays, year.daysTarget), isDone: month.done.pebbles,
                                  usesHue: true),
                weeks: weeks,
                accessibilityLabel: Copy.monthRow(month, locale: locale))
        }
    }

    /// The years before this one the payload carries, newest first as served,
    /// each opening its own sheet; from the earliest, the first-year line.
    static func earlierYears(_ year: Page.Year, all years: [Page.Year], _ ctx: Context) -> RegisterSection {
        let others = years.filter { $0.year < year.year }
        return RegisterSection(
            id: "earlier", title: Copy.sheet("earlierYears", locale: ctx.locale),
            rows: others.map { other in
                RegisterRowModel(id: "year-\(other.year)", mark: .none, name: String(other.year), style: .number,
                                 opens: .sheet(.year(other.year)), accessibilityLabel: String(other.year))
            },
            quiet: others.isEmpty ? Copy.firstYear(year.year, locale: ctx.locale) : nil)
    }

    // MARK: - Helpers

    /// A row opens its moment only when the server sent a moment id to open.
    private static func opens(_ pebble: Page.Pebble?) -> RegisterRowModel.Opens? {
        pebble?.momentId.map { .moment($0) }
    }
}
