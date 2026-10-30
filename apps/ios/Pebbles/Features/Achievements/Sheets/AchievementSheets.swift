import SwiftUI

extension AchievementsSheetRows.Context {
    /// The app's: the Spine's calendar, the app's language, the page's date locale.
    @MainActor
    static func current(_ lookups: WrapLookups, dateLocale: Locale? = nil) -> Self {
        Self(lookups: lookups, calendar: SpineCalendar.current, locale: SpineStrings.appLocale,
             dateLocale: dateLocale ?? SpineStrings.dateLocale)
    }
}

extension AchievementSheet {
    /// Whether `page` still holds what this sheet shows: a sheet whose section
    /// left the payload after a read is closed, never drawn empty.
    func isShown(by page: AchievementsPage?) -> Bool {
        guard let page else { return false }
        switch self {
        case .explorer: return page.explorer != nil
        case .emotions: return page.emotions != nil
        case .domains: return page.domains != nil
        case .pebbles: return page.pebbles != nil
        case .year(let year): return page.years.contains { $0.year == year }
        }
    }
}

/// The page's sheets (#1306): which one is open, and a row's moment to open on
/// the held card once the sheets are down. Every sheet is closed by its owner's
/// binding (never by `dismiss()` from a sheet that may be presenting another),
/// and the moment opens once, whichever way the dismissal reports.
struct AchievementSheetState: Equatable {
    var sheet: AchievementSheet?
    private(set) var pendingMoment: UUID?

    /// A row's moment: close the sheets, keep the moment for when they are down.
    mutating func open(_ moment: UUID) {
        pendingMoment = moment
        sheet = nil
    }

    /// The moment to open now, once: a second call has nothing.
    mutating func takePending() -> UUID? {
        defer { pendingMoment = nil }
        return pendingMoment
    }

    /// Closes the open sheet (and whatever it presents) when its section left the payload.
    mutating func close(ifGoneFrom page: AchievementsPage?) {
        if let open = sheet, !open.isShown(by: page) { sheet = nil }
    }
}

/// A set's ready mark on its sheet's band, while it is offered; every mark waits during a pull.
@MainActor
func sheetReadyMark(_ completion: AchievementsPage.Completion?, pulling: AchievementsPage.CeremonyStack?,
                    pull: @escaping (AchievementsPage.CeremonyStack) -> Void) -> ReadyMark? {
    guard let completion, completion.isReady else { return nil }
    return ReadyMark(stack: completion.stack, isPulling: pulling != nil, pull: { pull(completion.stack) })
}

/// Explorer (the board's `sheetExplorer`): the wheel large, a section per ring
/// inside out, then the rows that open Domains and Emotions on top of this sheet.
struct ExplorerSheetContent: View {
    let explorer: AchievementsPage.Explorer
    let emotions: AchievementsPage.Emotions?
    let domains: AchievementsPage.Domains?
    let lookups: WrapLookups
    var ready: ReadyMark?
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups, dateLocale: dateLocale)
        SheetBody(band: AchievementsCopy.title(.explorer, locale: ctx.locale),
                  hint: AchievementsCopy.explorerHint(locale: ctx.locale), ready: ready) {
            ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups, side: 340)
        } sections: {
            ForEach(AchievementsSheetRows.explorer(explorer, emotions: emotions, domains: domains, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, emotions: emotions, open: open)
            }
        }
    }
}

/// Emotions (the board's `sheetEmotions`): the rosace large, then the server's
/// families in its order, what was met with its pebble's seal and day, then the rest.
struct EmotionsSheetContent: View {
    let emotions: AchievementsPage.Emotions
    let lookups: WrapLookups
    var ready: ReadyMark?
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups, dateLocale: dateLocale)
        SheetBody(band: AchievementsCopy.title(.emotions, locale: ctx.locale),
                  hint: emotions.since.map { AchievementsCopy.sinceHint($0, calendar: ctx.calendar, locale: ctx.locale,
                                                                        dateLocale: ctx.dateLocale) },
                  ready: ready) {
            EmotionRosace(emotions: emotions, lookups: lookups, side: 330)
        } sections: {
            ForEach(AchievementsSheetRows.emotions(emotions, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, emotions: emotions, open: open)
            }
        }
    }
}

/// Domains (the board's `sheetDomains`): the band, what the coverage means, the
/// register newest first (numbered by arrival), then not yet.
struct DomainsSheetContent: View {
    let domains: AchievementsPage.Domains
    let lookups: WrapLookups
    var ready: ReadyMark?
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups, dateLocale: dateLocale)
        SheetBody(band: AchievementsCopy.title(.domains, locale: ctx.locale),
                  hint: AchievementsCopy.sheet("register", locale: ctx.locale),
                  lead: AchievementsCopy.coverage(domains.coverage, locale: ctx.locale), ready: ready) {
            DomainBand(domains: domains)
        } sections: {
            ForEach(AchievementsSheetRows.domains(domains, ctx)) { section in
                SheetSectionView(section: section, lookups: lookups, open: open)
            }
        }
    }
}

/// Pebbles (the board's `sheetPebbles`): the skyline, the pebbles kept, the
/// milestones, then the glyph stacks in the same grammar, smaller. The next
/// milestone shows what is left, never a date.
struct PebblesSheetContent: View {
    let pebbles: AchievementsPage.Ladder
    let carved: AchievementsPage.Ladder?
    let sales: AchievementsPage.Ladder?
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups, dateLocale: dateLocale)
        let locale = ctx.locale
        SheetBody(band: AchievementsCopy.title(.pebbles, locale: locale),
                  hint: pebbles.since.map { AchievementsCopy.sinceHint($0, calendar: ctx.calendar, locale: locale,
                                                                       dateLocale: ctx.dateLocale) },
                  lead: AchievementsCopy.pebblesLead(pebbles, locale: locale)) {
            PebbleSkyline(ladder: pebbles, width: 350)
        } sections: {
            SheetSectionView(
                section: AchievementsSheetRows.ladder(
                    pebbles, id: "milestones", title: AchievementsCopy.sheet("milestones", locale: locale),
                    note: AchievementsSheetRows.milestonesNote(pebbles, locale: locale), ctx),
                lookups: lookups, open: open)
            if let carved {
                SheetSectionView(
                    section: AchievementsSheetRows.ladder(
                        carved, id: "glyphs-carved", title: AchievementsCopy.glyphsCarved(locale: locale),
                        note: AchievementsCopy.number(carved.count, locale: locale), ctx),
                    lookups: lookups, compact: true, open: open)
            }
            if let sales {
                SheetSectionView(
                    section: AchievementsSheetRows.ladder(
                        sales, id: "glyph-sales", title: AchievementsCopy.glyphSales(locale: locale),
                        note: AchievementsCopy.number(sales.count, locale: locale),
                        trailing: AchievementsCopy.sheet("buyerNeverNamed", locale: locale), ctx),
                    lookups: lookups, compact: true, open: open)
            }
        }
    }
}

/// The year (the board's `sheetYear`): the docked disc large, the months the
/// server lists newest first with their three counters (not buttons), the next
/// step, then the earlier years, each opening its own sheet.
struct YearSheetContent: View {
    let year: AchievementsPage.Year
    let years: [AchievementsPage.Year]
    let lookups: WrapLookups
    let open: (RegisterRowModel.Opens) -> Void

    @Environment(\.achievementsDateLocale) private var dateLocale

    var body: some View {
        let ctx = AchievementsSheetRows.Context.current(lookups, dateLocale: dateLocale)
        SheetBody(band: String(year.year), hint: AchievementsCopy.yearHint(year, locale: ctx.locale)) {
            YearDisc(year: year, lookups: lookups, side: 330)
        } sections: {
            VStack(alignment: .leading, spacing: 0) {
                SheetSectionHead(title: AchievementsCopy.sheet("months", locale: ctx.locale),
                                 note: AchievementsCopy.sheet("newestFirst", locale: ctx.locale))
                YearColumnsHead()
                ForEach(AchievementsSheetRows.months(year, ctx)) { row in
                    YearRow(row: row, lookups: lookups)
                }
                if let step = year.nextStep {
                    Text(verbatim: AchievementsCopy.nextStep(step, locale: ctx.locale))
                        .spineType(.achievementsLead)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 14)
                }
            }
            .padding(.top, 28)
            SheetSectionView(section: AchievementsSheetRows.earlierYears(year, all: years, ctx), lookups: lookups,
                             open: open)
        }
    }
}

/// Presents one sheet (#1306, spec §4): it scrolls, sits at the large detent,
/// marks its card seen when it appears, closes when its section leaves the
/// payload, and presents a sheet a row opens on top of itself (Explorer's
/// Domains and Emotions, an earlier year). A row that opens a moment hands its
/// id to `openMoment`, which closes the sheets first: the held card is drawn
/// above the tabs, under any sheet.
struct AchievementSheetHost: View {
    let sheet: AchievementSheet
    /// The page as it is now: a later read reaches an open sheet.
    let page: AchievementsPage?
    let lookups: WrapLookups
    var pulling: AchievementsPage.CeremonyStack?
    let seen: (AchievementCard) -> Void
    let pull: (AchievementsPage.CeremonyStack) -> Void
    let openMoment: (UUID) -> Void

    @State private var pushed: AchievementSheet?

    var body: some View {
        ScrollView {
            content
        }
        .background(Color.pebbles.stock)
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .onAppear { seen(sheet.card) }
        // What this sheet presents is closed here, by its owner, when its section leaves the payload;
        // this sheet itself is closed by its own owner the same way.
        .onChange(of: page) { _, now in
            if let shown = pushed, !shown.isShown(by: now) { pushed = nil }
        }
        .sheet(item: $pushed) { inner in
            AchievementSheetHost(sheet: inner, page: page, lookups: lookups, pulling: pulling, seen: seen, pull: pull,
                                 openMoment: openMoment)
        }
    }

    @ViewBuilder
    private var content: some View {
        if let page {
            switch sheet {
            case .explorer:
                if let explorer = page.explorer {
                    ExplorerSheetContent(explorer: explorer, emotions: page.emotions, domains: page.domains,
                                         lookups: lookups, ready: ready(explorer.completion), open: route)
                }
            case .emotions:
                if let emotions = page.emotions {
                    EmotionsSheetContent(emotions: emotions, lookups: lookups, ready: ready(emotions.completion),
                                         open: route)
                }
            case .domains:
                if let domains = page.domains {
                    DomainsSheetContent(domains: domains, lookups: lookups, ready: ready(domains.completion),
                                        open: route)
                }
            case .pebbles:
                if let pebbles = page.pebbles {
                    PebblesSheetContent(pebbles: pebbles, carved: page.glyphsCarved, sales: page.glyphSales,
                                        lookups: lookups, open: route)
                }
            case .year(let number):
                if let year = page.years.first(where: { $0.year == number }) {
                    YearSheetContent(year: year, years: page.years, lookups: lookups, open: route)
                }
            }
        }
    }

    private func ready(_ completion: AchievementsPage.Completion?) -> ReadyMark? {
        sheetReadyMark(completion, pulling: pulling, pull: pull)
    }

    private func route(_ opens: RegisterRowModel.Opens) {
        switch opens {
        case .moment(let id): openMoment(id)
        case .sheet(let next): pushed = next
        }
    }
}
