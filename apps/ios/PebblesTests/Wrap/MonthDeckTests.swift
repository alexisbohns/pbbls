import Foundation
import Testing
@testable import Pebbles

/// #1241 D11: the month's deck deals a card for the cover and for each section that shows.
@Suite("Month deck")
@MainActor
struct MonthDeckTests {
    @Test("the fixture deals the cover, then each section in the server's order")
    func fixture() throws {
        let wrap = try MonthPayloadFixture.october()
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(cards == [.cover, .section(.weeks), .section(.milestones), .section(.persons), .section(.gem),
                          .section(.domains), .section(.tone), .section(.hours)])
    }

    @Test("an emptied section deals no card", arguments: MonthSection.allCases)
    func emptied(section: MonthSection) throws {
        let wrap = Self.emptying(try MonthPayloadFixture.october(), section)
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(!cards.contains(.section(section)))
        #expect(cards.count == 7)
    }

    @Test("a wrap without a cover deals none")
    func noCover() throws {
        let wrap = Self.emptying(try MonthPayloadFixture.october(), cover: true)
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(cards.first == .section(.weeks))
    }

    @Test("the insights head sits on the first tone or hours card dealt")
    func insights() throws {
        let october = try MonthPayloadFixture.october()
        let lookups = MonthPayloadFixture.lookups(october)
        let full = MonthDeck.cards(wrap: october, lookups: lookups)
        #expect(MonthDeck.leadsInsights(.section(.tone), in: full))
        #expect(!MonthDeck.leadsInsights(.section(.hours), in: full))
        #expect(!MonthDeck.leadsInsights(.section(.gem), in: full))
        // An empty tone deals no card: the head moves to the hours.
        let quiet = MonthDeck.cards(wrap: Self.emptying(october, .tone), lookups: lookups)
        #expect(MonthDeck.leadsInsights(.section(.hours), in: quiet))
    }

    /// The wrap with `section` drawing nothing, as the server sends an empty one, and without its cover if asked.
    private static func emptying(_ wrap: MonthWrap, _ section: MonthSection? = nil, cover: Bool = false) -> MonthWrap {
        MonthWrap(month: wrap.month, daysInMonth: wrap.daysInMonth, firstWeekday: wrap.firstWeekday,
                  pebbles: wrap.pebbles, people: wrap.people, sections: wrap.sections,
                  cover: cover ? nil : wrap.cover,
                  weeks: section == .weeks ? [] : wrap.weeks,
                  milestones: section == .milestones ? [] : wrap.milestones,
                  persons: section == .persons ? nil : wrap.persons,
                  previousMonth: wrap.previousMonth,
                  gem: section == .gem ? nil : wrap.gem,
                  fineEmotions: wrap.fineEmotions,
                  domains: section == .domains ? [] : wrap.domains,
                  tone: section == .tone ? wrap.tone.map { Tone(days: [], verdict: $0.verdict, first: nil, last: nil) }
                      : wrap.tone,
                  hours: section == .hours ? wrap.hours.map { MonthHours(items: [], busiest: $0.busiest) }
                      : wrap.hours)
    }
}
