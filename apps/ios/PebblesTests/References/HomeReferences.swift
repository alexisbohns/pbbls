import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The Home greeting on a Monday morning in Paris, in the pass's language.
    @Suite("Home references") @MainActor
    struct HomeReferences {
        static func greeting() -> some View {
            let paris = TimeZone(identifier: "Europe/Paris")!
            let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]
            let monday = f.date(from: "2026-11-02T08:10:00+01:00")!
            let locale = Locale(identifier: ReferenceLocale.current.rawValue)
            return HomeGreetingView(lines: HomeGreeting.lines(at: monday, name: "Alexis", zone: paris, locale: locale))
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func greeting(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.greeting(), named: "greeting",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        /// One style, from its own defaults so the device's choice never leaks in.
        static func shortcuts(_ layout: HomeShortcutsLayout, stones: Bool, lead: HomeShortcut) -> some View {
            let name = "HomeReferences.shortcuts.\(layout.rawValue).\(stones).\(lead.rawValue)"
            let defaults = UserDefaults(suiteName: name)!
            defaults.removePersistentDomain(forName: name)
            defaults.set(layout.rawValue, forKey: HomeShortcutsPreference.layoutKey)
            defaults.set(stones, forKey: HomeShortcutsPreference.stonesKey)
            defaults.set(lead.rawValue, forKey: HomeShortcutsPreference.lastUsedKey)
            return HomeShortcuts(onCamera: {}, onQuick: {}, onPebble: {}, defaults: defaults)
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity)
                .background(Color.pebbles.surface)
        }

        /// The default: the pebble-shaped row.
        @Test(arguments: ReferenceScheme.allCases)
        func shortcuts(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.shortcuts(.row, stones: true, lead: .camera), named: "shortcuts",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func shortcutsStonesBento(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.shortcuts(.bento, stones: true, lead: .quick),
                                         named: "shortcutsStonesBento",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func shortcutsTilesRow(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.shortcuts(.row, stones: false, lead: .camera),
                                         named: "shortcutsTilesRow",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func shortcutsTilesBento(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.shortcuts(.bento, stones: false, lead: .pebble),
                                         named: "shortcutsTilesBento",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func toast() -> some View {
            MomentToast(
                landed: CameraMomentCoordinator.LandedMoment(
                    momentId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
                    shutter: Date(timeIntervalSince1970: 1_793_000_000), thumbnail: nil),
                onName: {}, onDismiss: {})
                .padding(Spacing.lg)
                .frame(width: 393)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func toast(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.toast(), named: "toast",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func septemberFolded() throws -> HomeMonth {
            try #require(HomePayloads.wraps().months.first { $0.month == CalendarMonth(year: 2026, month: 9) })
        }

        static func lookups() throws -> WrapLookups {
            SpineFixtureMonth.lookups(try MonthPayloadFixture.october())
        }

        static func wrapCards() throws -> some View {
            let sept = try septemberFolded()
            let at = Date(timeIntervalSince1970: 1_793_000_000)
            let months = [
                sept,
                HomeMonth(month: .init(year: 2026, month: 8), count: 4, revealedAt: at, cover: sept.cover),
                HomeMonth(month: .init(year: 2026, month: 7), count: 4, revealedAt: at, cover: sept.cover)
            ]
            // The row's horizontal ScrollView does not render under ImageRenderer: draw its content.
            let lookups = try lookups()
            return VStack(alignment: .leading, spacing: Spacing.sm) {
                SpineSectionHeader(String(localized: "home.wraps.title")) {
                    Text(verbatim: "\(months.count)").spineType(.sectionLabel)
                }
                HStack(spacing: Spacing.sm) {
                    ForEach(months) { HomeWrapCard(month: $0, lookups: lookups) }
                }
            }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapCards(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapCards(), named: "wrapCards",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func wrapsEmpty() throws -> some View {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                SpineSectionHeader(String(localized: "home.wraps.title")) {}
                HomeWrapPromise(liveMonth: CalendarMonth(year: 2026, month: 11))
            }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapsEmpty(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapsEmpty(), named: "wrapsEmpty",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func wrapSheetFolded() throws -> some View {
            HomeWrapFoldedView(month: try septemberFolded(), onUnfold: {}, isRevealing: false)
                .padding(Spacing.lg)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapSheetFolded(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapSheetFolded(), named: "wrapSheetFolded",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func checklist(_ done: Set<HomeChecklist.Task>) -> HomeChecklist {
            let slugs = HomeChecklist.Task.allCases.map(\.slug) + [HomeChecklist.blueSlug]
            return HomeChecklist.make(catalog: HomeChecklistTests.catalog(slugs), unlocked: Set(done.map(\.slug)))
        }

        static func checklistView(_ checklist: HomeChecklist) -> some View {
            HomeChecklistView(checklist: checklist, isClaiming: false, onTask: { _ in }, onClaim: {})
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func checklistEmpty(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.checklistView(Self.checklist([])), named: "checklistEmpty",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func checklistThree(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.checklistView(Self.checklist([.moment, .pebble, .soul])),
                                         named: "checklistThree",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func checklistThreeAx2() throws {
            ReferenceImage.assertMatches(Self.checklistView(Self.checklist([.moment, .pebble, .soul])),
                                         named: "checklistThree",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func checklistClaim(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.checklistView(Self.checklist(Set(HomeChecklist.Task.allCases))),
                                         named: "checklistClaim",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func taskSoul() -> some View {
            ChecklistTaskContent(task: .soul, onGo: {})
                .frame(width: 393, height: 420, alignment: .top)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func taskSoul(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.taskSoul(), named: "taskSoul",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static let guideFixtures: [Guide] = [
            Guide(slug: "stones", sortOrder: 1, illustration: .stones,
                  titleEn: "Stones", titleFr: "Les pierres",
                  summaryEn: "Nine shapes, by weight and light.", summaryFr: "Neuf formes, par poids et lumière.",
                  bodyMdEn: "A pebble is a stone.\n\n## Three sizes\n\nA day event, a milestone, a turning point.",
                  bodyMdFr: "Un pebble est une pierre.\n\n## Trois tailles\n\nUn événement, un jalon, un tournant."),
            Guide(slug: "emotions", sortOrder: 2, illustration: .emotions,
                  titleEn: "Emotions", titleFr: "Les émotions",
                  summaryEn: "Seven families, each with a colour.", summaryFr: "Sept familles, chacune sa couleur.",
                  bodyMdEn: "Seven families."),
            Guide(slug: "souls", sortOrder: 3, illustration: .souls,
                  titleEn: "Souls", titleFr: "Les âmes",
                  summaryEn: "The people in your moments.", summaryFr: "Les personnes de tes moments.",
                  bodyMdEn: "The people.")
        ]

        static func guidesList() -> some View {
            GuidesList(guides: guideFixtures)
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func guidesList(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.guidesList(), named: "guidesList",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func guidePageStones() -> some View {
            GuidePageContent(guide: guideFixtures[0])
                .padding(.vertical, Spacing.md)
                .frame(width: 393, height: 600, alignment: .top)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func guidePageStones(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.guidePageStones(), named: "guidePageStones",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        /// The whole page in one of the Lab's states, on its Monday-morning clock.
        /// The wraps row's horizontal ScrollView does not render under ImageRenderer,
        /// so the row may show empty here; the cards have their own reference above.
        static func page(_ state: HomeFixture.State) async -> some View {
            let model = await HomeFixture.model(state)
            let locale = Locale(identifier: ReferenceLocale.current.rawValue)
            return HomeContent(model: model, lookups: HomeFixture.lookups, liveMonth: HomeFixture.liveMonth,
                               clock: HomeFixture.clock, zone: HomeFixture.zone, locale: locale)
                .frame(width: 393, alignment: .top)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pageNewUser(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.page(.newUser), named: "pageNewUser",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pageReturning(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.page(.returning), named: "pageReturning",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pageClaim(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.page(.claim), named: "pageClaim",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }
    }
}

// The c3 greeting's checks (#1382 D10), beside the suite so its body stays in bounds.
extension References.HomeReferences {
    /// The evening greeting with a long name in French, at the mock's 358 pt
    /// (#1382 D10): it must hold one line.
    static func greetingLongFr() -> some View {
        let paris = TimeZone(identifier: "Europe/Paris")!
        let locale = Locale(identifier: ReferenceLocale.current.rawValue)
        return HomeGreetingView(lines: HomeGreeting.lines(at: HomeFixture.clock, name: "Alexandrine",
                                                          zone: paris, locale: locale))
            .frame(width: 358, alignment: .leading)
            .padding(Spacing.lg)
            .background(Color.pebbles.surface)
    }

    @Test func greetingLongFr() throws {
        ReferenceImage.assertMatches(Self.greetingLongFr(), named: "greetingLongFr",
                                     config: ReferenceConfig(scheme: .paper, locales: [.fr], width: nil))
    }

    /// The month's weeks at Home's size (#1382 D8): the fixture October on the
    /// mock's Saturday, so the last week is the live one.
    static func monthGrid() -> some View {
        HomeMonthGrid(month: HomeFixture.month, lookups: HomeFixture.lookups,
                      today: CalendarDay(year: 2026, month: 10, day: 31), onWeek: { _ in })
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.pebbles.surface)
    }

    @Test(arguments: ReferenceScheme.allCases)
    func monthGrid(scheme: ReferenceScheme) throws {
        let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
        ReferenceImage.assertMatches(Self.monthGrid(), named: "monthGrid",
                                     config: ReferenceConfig(scheme: scheme, locales: locales))
    }

    @Test func monthGridAx2() throws {
        ReferenceImage.assertMatches(Self.monthGrid(), named: "monthGrid",
                                     config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
    }

    /// November 2026 before its first pebble: the server sends no weeks, so the
    /// columns are laid out from the month alone (today Sunday the 1st).
    static func monthGridEmpty() -> some View {
        let november = MonthWrap(month: CalendarMonth(year: 2026, month: 11), daysInMonth: 30, firstWeekday: 6,
                                 pebbles: 0, people: 0, sections: [], cover: nil, weeks: nil, milestones: nil,
                                 persons: nil, previousMonth: nil, gem: nil, fineEmotions: nil, domains: nil,
                                 tone: nil, hours: nil)
        return HomeMonthGrid(month: november, lookups: HomeFixture.lookups,
                             today: CalendarDay(year: 2026, month: 11, day: 1), onWeek: { _ in })
            .padding(Spacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.pebbles.surface)
    }

    @Test(arguments: ReferenceScheme.allCases)
    func monthGridEmpty(scheme: ReferenceScheme) throws {
        ReferenceImage.assertMatches(Self.monthGridEmpty(), named: "monthGridEmpty",
                                     config: ReferenceConfig(scheme: scheme))
    }
}
