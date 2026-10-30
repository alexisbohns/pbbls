import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the achievements page's five figures on the design fixture, Paper
    /// and Night: the wheel, the rosace, the band, the three skylines, the disc.
    @Suite("Achievements figure references")
    @MainActor
    struct AchievementsFigureReferences {
        static func figures() throws -> some View {
            let page = try AchievementsFixtures.payload("design-fixture").model
            let lookups = AchievementsLookups.fixture(page)
            let explorer = try #require(page.explorer)
            let emotions = try #require(page.emotions)
            let domains = try #require(page.domains)
            let pebbles = try #require(page.pebbles)
            let carved = try #require(page.glyphsCarved)
            let sales = try #require(page.glyphSales)
            let year = try #require(page.years.first)
            return VStack(spacing: 24) {
                ExplorerWheel(explorer: explorer, emotions: emotions, domains: domains, lookups: lookups)
                EmotionRosace(emotions: emotions, lookups: lookups)
                DomainBand(domains: domains).frame(width: 300)
                PebbleSkyline(ladder: pebbles)
                HStack(spacing: 24) {
                    PebbleSkyline(ladder: carved, box: SkylineGeometry.narrow, end: 126, width: 103)
                    PebbleSkyline(ladder: sales, box: SkylineGeometry.narrow, end: 126, width: 103)
                }
                YearDisc(year: year, lookups: lookups)
                HStack(spacing: 8) {
                    ForEach(0..<8, id: \.self) { index in
                        SkylineStage(index: index, fill: index < 5 ? 1 : index == 5 ? 0.247 : 0)
                    }
                }
            }
            // The card's width: each figure takes it, up to its board size.
            .frame(width: 300)
            .padding(24)
            .background(Color.pebbles.stock)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func figures(scheme: ReferenceScheme) throws {
            ReferenceImage.assertMatches(try Self.figures(), named: "figures",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }
    }
}
