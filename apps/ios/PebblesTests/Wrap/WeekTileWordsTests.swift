import Foundation
import Testing
@testable import Pebbles

/// What each week tile counts and says (#1226; Android: `tileCount`,
/// `tileSentence`), on the fixture week's hand-worked wrap.
@Suite("Week tile words")
@MainActor
struct WeekTileWordsTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    private func say(_ tile: WeekTile, _ wrap: WeekWrap) -> String {
        WeekTileWords.sentence(tile, wrap: wrap, soulNames: F.soulNames, lookups: F.lookups, calendar: Vectors.paris,
                               locale: Vectors.en, dateLocale: Vectors.en)
    }

    @Test("the counts the tiles head with")
    func counts() {
        let wrap = F.weekWrap(live: false)
        #expect(WeekTileWords.count(.contents, wrap: wrap) == 13)
        #expect(WeekTileWords.count(.persons, wrap: wrap) == 14)
        #expect(WeekTileWords.count(.emotions, wrap: wrap) == 6)
        #expect(WeekTileWords.count(.domains, wrap: wrap) == 3)
        #expect(WeekTileWords.count(.weight, wrap: wrap) == nil)
    }

    @Test("each tile's sentence")
    func sentences() {
        let wrap = F.weekWrap(live: false)
        #expect(say(.contents, wrap) == "13")
        #expect(say(.persons, wrap).hasPrefix("Mara, Bruno, Elio, Jules, Nadia, Ana"))
        #expect(say(.emotions, wrap) == "Mostly joy, then peace.")
        #expect(say(.domains, wrap) == "Friends first, then family and identity.")
        #expect(say(.weight, wrap) == "Six bright, four even, three heavy.")
        #expect(say(.hours, wrap) == "From 3:10\u{202F}AM to 10:30\u{202F}PM.")
        #expect(say(.kept, wrap) == "six photos, three cards written")
    }

    @Test("a week with nobody named invites, naming its own moments")
    func invitation() {
        let full = F.weekWrap(live: false)
        let nobody = WeekWrap(weekStart: full.weekStart, isoWeek: 41, isoYear: 2026, isLive: false, pebbles: 3,
                              people: 0, tiles: [.contents, .persons], persons: [],
                              inviteMomentIds: [1, 2, 3].map { F.moments[$0 - 1].id.uuidString.lowercased() },
                              families: nil, lead: nil, domains: nil, weight: nil, hours: nil, kept: nil)
        #expect(WeekTileWords.count(.persons, wrap: nobody) == nil)
        #expect(say(.persons, nobody)
                == "Was someone there for Flag march with Nadia, Elio lost his first tooth or Rent went up again?")
    }
}
