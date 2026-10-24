import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W4: the week's line (past, live, empty), its tiles settled (all
    /// seven) and live (dashed, *so far*), and each page of its sheet drawn
    /// without the sheet's scroll views, on the fixture week's hand-worked wrap.
    /// Android: the `WeekLine`, `WeekTiles` and `WeekSheet` previews.
    @Suite("Wrap week references")
    @MainActor
    struct WrapWeekReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func lines(scheme: ReferenceScheme) {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            let locale = SpineStrings.appLocale
            let view = VStack(alignment: .leading, spacing: 8) {
                WeekLine(isoWeek: 41, summary: WrapCopy.line(pebbles: 11, people: 5, live: false, hasMoments: true,
                                                             locale: locale))
                WeekLine(isoWeek: 43, summary: WrapCopy.line(pebbles: 13, people: 14, live: true, hasMoments: true,
                                                             locale: locale))
                WeekLine(isoWeek: 44, summary: WrapCopy.line(pebbles: 0, people: 0, live: true, hasMoments: false,
                                                             locale: locale), onCollect: {})
                WeekLine(isoWeek: 45, summary: nil)
            }
            .padding(.bottom, 16)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "lines",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func tiles(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            let page = try await Self.page()
            // Every tile of a settled week in one image: a plain row, since a scroll view renders blank.
            let view = VStack(alignment: .leading, spacing: 0) {
                Self.tileRow(F.weekWrap(live: false), page: page)
                Self.tileRow(F.weekWrap(live: true), page: page)
            }
            .fixedSize()
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "tiles",
                                         config: ReferenceConfig(scheme: scheme, locales: locales, width: nil))
        }

        @Test
        func tilesAx2() async throws {
            let page = try await Self.page()
            ReferenceImage.assertMatches(Self.tileRow(F.weekWrap(live: false), page: page)
                                            .background(Color.pebbles.surface), named: "tiles",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2,
                                                                 width: nil))
        }

        @Test(arguments: [WeekTile.persons, .emotions, .domains, .weight, .hours, .kept])
        func sheetPage(tile: WeekTile) async throws {
            let page = try await Self.page()
            let view = WeekSheetPage(tile: tile, wrap: F.weekWrap(live: false), page: page, lookups: F.lookups,
                                     readingDay: nil, onGo: { _ in })
                .padding(.bottom, 24)
                .frame(width: ReferenceConfig.phoneWidth, alignment: .topLeading)
                .background(Color.pebbles.surface)
            for scheme in ReferenceScheme.allCases {
                let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
                ReferenceImage.assertMatches(view, named: "sheet-\(tile.rawValue)",
                                             config: ReferenceConfig(scheme: scheme, locales: locales))
            }
        }

        @Test
        func invitation() async throws {
            let page = try await Self.page()
            let nobody = WeekWrap(weekStart: F.weekWrap(live: false).weekStart, isoWeek: 41, isoYear: 2026,
                                  isLive: false, pebbles: 3, people: 0, tiles: [.contents, .persons], persons: [],
                                  inviteMomentIds: [1, 2, 3].map { F.moments[$0 - 1].id.uuidString.lowercased() },
                                  families: nil, lead: nil, domains: nil, weight: nil, hours: nil, kept: nil)
            let view = VStack(alignment: .leading, spacing: 0) {
                Self.tileRow(nobody, page: page)
                WeekSheetPage(tile: .persons, wrap: nobody, page: page, lookups: F.lookups, readingDay: nil,
                              onGo: { _ in })
            }
            .frame(width: ReferenceConfig.phoneWidth, alignment: .topLeading)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "invitation",
                                         config: ReferenceConfig(scheme: .paper, locales: [.en, .fr]))
        }

        private static func page() async throws -> SpinePage {
            let model = await F.model()
            return try #require(model.page(for: F.weekStart))
        }

        /// The tiles as `WeekTiles` lays them out, without its scroll view.
        private static func tileRow(_ wrap: WeekWrap, page: SpinePage) -> some View {
            WeekTileRow(wrap: wrap, page: page, lookups: F.lookups, onOpen: { _ in })
                .fixedSize()
        }
    }
}
