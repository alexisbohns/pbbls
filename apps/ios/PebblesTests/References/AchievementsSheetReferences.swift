import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1306: the five sheets on the canonical design fixture, unrolled, Paper
    /// and Night; the Emotions and the year sheets in French too.
    @Suite("Achievements sheet references")
    @MainActor
    struct AchievementsSheetReferences {
        /// One sheet's content, unwrapped before it is built: a result builder cannot `try`.
        static func sheet(_ sheet: AchievementSheet) throws -> AnyView {
            let page = try AchievementsFixtures.payload("design-fixture").model
            let lookups = try AchievementsLookups.fixture()
            let open: (RegisterRowModel.Opens) -> Void = { _ in }
            let view: AnyView
            switch sheet {
            case .explorer:
                view = AnyView(ExplorerSheetContent(explorer: try #require(page.explorer), emotions: page.emotions,
                                                    domains: page.domains, lookups: lookups, open: open))
            case .emotions:
                view = AnyView(EmotionsSheetContent(emotions: try #require(page.emotions), lookups: lookups, open: open))
            case .domains:
                view = AnyView(DomainsSheetContent(domains: try #require(page.domains), lookups: lookups, open: open))
            case .pebbles:
                view = AnyView(PebblesSheetContent(pebbles: try #require(page.pebbles), carved: page.glyphsCarved,
                                                   sales: page.glyphSales, lookups: lookups, open: open))
            case .year:
                view = AnyView(YearSheetContent(year: try #require(page.years.first), years: page.years,
                                                lookups: lookups, open: open))
            }
            return AnyView(view.environment(\.achievementsDateLocale, AchievementsPageReferences.dateLocale))
        }

        static let sheets: [AchievementSheet] = [.explorer, .emotions, .domains, .pebbles, .year(2026)]

        @Test(arguments: ReferenceScheme.allCases)
        func sheets(scheme: ReferenceScheme) throws {
            for sheet in Self.sheets {
                let french = sheet == .emotions || sheet == .year(2026)
                let locales: Set<ReferenceLocale> = scheme == .paper && french ? [.en, .fr] : [.en]
                ReferenceImage.assertMatches(try Self.sheet(sheet), named: "sheet-\(sheet.id)",
                                             config: ReferenceConfig(scheme: scheme, locales: locales))
            }
        }
    }
}
