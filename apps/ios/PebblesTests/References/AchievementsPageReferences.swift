import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the achievements page on the canonical design fixture and on a new
    /// user's first day, Paper and Night, English and French, and at accessibility
    /// text size; and the Explorer card with its set offered, its ready mark in the band.
    @Suite("Achievements page references")
    @MainActor
    struct AchievementsPageReferences {
        static func content(_ page: AchievementsPage) throws -> some View {
            AchievementsPageContent(page: page, lookups: try AchievementsLookups.fixture())
                .background(Color.pebbles.surface)
        }

        static func page(_ fixture: String) throws -> some View {
            try content(try AchievementsFixtures.payload(fixture).model)
        }

        /// The design page with its six firsts offered and not yet pulled.
        static func ready() throws -> some View {
            var page = try AchievementsFixtures.payload("design-fixture").model
            let explorer = try #require(page.explorer)
            let completion = try #require(explorer.completion)
            page.explorer = AchievementsPage.Explorer(
                firstPebble: explorer.firstPebble, firstFive: explorer.firstFive, sixFirsts: explorer.sixFirsts,
                twentyFive: explorer.twentyFive, ringsDone: explorer.ringsDone,
                completion: AchievementsPage.Completion(stack: .sixFirsts, completedAt: completion.completedAt,
                                                        offeredFrom: completion.offeredFrom, isReady: true,
                                                        pulledAt: nil, pebble: completion.pebble))
            page.emotions = nil
            page.domains = nil
            page.pebbles = nil
            page.years = []
            return try content(page)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func page(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.page("design-fixture"), named: "page",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func pageAx2() throws {
            ReferenceImage.assertMatches(try Self.page("design-fixture"), named: "page",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func newUser(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(try Self.page("new-user"), named: "new-user",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test
        func readyMark() throws {
            ReferenceImage.assertMatches(try Self.ready(), named: "ready", config: ReferenceConfig(scheme: .paper))
        }
    }
}
