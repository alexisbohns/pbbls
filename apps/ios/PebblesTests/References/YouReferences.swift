import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// You (#1382 D16–D19) from `YouFixture`: Alexis, March 2026, a glyph,
    /// Ripples 3 active today, 112 / 318 / 1,240, glyph counts 12 / 5.
    @Suite("You references") @MainActor
    struct YouReferences {
        private static func page(_ content: some View) -> some View {
            content
                .padding(.horizontal, 16)
                .padding(.vertical, Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .top)
                .background(Color.pebbles.surface)
        }

        static func header(glyph: Bool) async -> some View {
            let model = await YouFixture.model(glyph: glyph)
            let stats = YouFixture.stats
            return page(YouHeader(name: model.profile?.displayName, memberSince: model.profile?.createdAt,
                                  glyphStrokes: model.glyphStrokes, rippleLevel: stats.rippleLevel,
                                  activeToday: stats.activeToday))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func header(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.header(glyph: true), named: "header",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func headerWithoutGlyph() async throws {
            ReferenceImage.assertMatches(await Self.header(glyph: false), named: "headerWithoutGlyph",
                                         config: ReferenceConfig(scheme: .paper))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func counters(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.page(YouCounters(stats: YouFixture.stats)), named: "counters",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func countersLoading() throws {
            ReferenceImage.assertMatches(Self.page(YouCounters(stats: YouStats())), named: "countersLoading",
                                         config: ReferenceConfig(scheme: .paper))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func glyphs(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            let section = YouGlyphsSection(carved: YouFixture.stats.glyphsCarved, owned: YouFixture.stats.glyphsOwned)
            ReferenceImage.assertMatches(Self.page(section), named: "glyphs",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        /// Today's live RPC sends no counts: the lines stay count-free.
        @Test func glyphsPlain() throws {
            ReferenceImage.assertMatches(Self.page(YouGlyphsSection(carved: nil, owned: nil)), named: "glyphsPlain",
                                         config: ReferenceConfig(scheme: .paper, locales: [.en, .fr]))
        }

        static func wholePage() async -> some View {
            let model = await YouFixture.model()
            return YouContent(model: model, stats: YouFixture.stats)
                .frame(width: 393, alignment: .top)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func page(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.wholePage(), named: "page",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func pageAx2() async throws {
            ReferenceImage.assertMatches(await Self.wholePage(), named: "page",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        // MARK: - Achievements and the Lab (#1382 D20, D21)

        static func achievements(shelf: Bool) async -> some View {
            let model = await YouFixture.model(shelf: shelf)
            return page(YouAchievementsSection(badges: model.badges, count: model.earnedCount))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func achievements(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.achievements(shelf: true), named: "achievements",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func achievementsEmpty() async throws {
            ReferenceImage.assertMatches(await Self.achievements(shelf: false), named: "achievementsEmpty",
                                         config: ReferenceConfig(scheme: .paper, locales: [.en, .fr]))
        }

        @Test func achievementsAx2() async throws {
            ReferenceImage.assertMatches(await Self.achievements(shelf: true), named: "achievements",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        static func lab(announcement: Bool) async -> some View {
            let model = await YouFixture.model(announcement: announcement)
            return page(YouLabSection(announcement: model.announcement, changelog: model.changelog))
        }

        /// The drawn band (the fixture's announcement has no cover), the timeline and the community stone.
        @Test(arguments: ReferenceScheme.allCases)
        func lab(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(await Self.lab(announcement: true), named: "lab",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test func labWithoutAnnouncement() async throws {
            ReferenceImage.assertMatches(await Self.lab(announcement: false), named: "labWithoutAnnouncement",
                                         config: ReferenceConfig(scheme: .paper))
        }

        /// Both feeds failed: only the community stone stays.
        @Test(arguments: ReferenceScheme.allCases)
        func community(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(Self.page(YouLabSection(announcement: nil, changelog: [])),
                                         named: "community", config: ReferenceConfig(scheme: scheme))
        }
    }
}
