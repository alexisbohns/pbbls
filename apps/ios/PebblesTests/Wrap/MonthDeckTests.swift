import Foundation
import Testing
@testable import Pebbles

/// #1241 D11: the month's deck deals a card for the cover and for each section that shows.
@Suite("Month deck")
@MainActor
struct MonthDeckTests {
    @Test("the fixture deals the cover, then each section in the server's order, then the end card")
    func fixture() throws {
        let wrap = try MonthPayloadFixture.october()
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(cards == [.cover, .section(.weeks), .section(.milestones), .section(.persons), .section(.gem),
                          .section(.domains), .section(.tone), .section(.hours), .end])
    }

    @Test("a month with nothing to deal deals no end card either")
    func nothing() throws {
        let october = try MonthPayloadFixture.october()
        let wrap = MonthWrap(month: october.month, daysInMonth: october.daysInMonth,
                             firstWeekday: october.firstWeekday, pebbles: 0, people: 0, sections: [], cover: nil,
                             weeks: nil, milestones: nil, persons: nil, previousMonth: nil, gem: nil,
                             fineEmotions: nil, domains: nil, tone: nil, hours: nil)
        #expect(MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap)).isEmpty)
    }

    @Test("the end card arrives, then its replay rises")
    func end() throws {
        let beats = IntroScore.end()
        #expect(try #require(beats.first { $0.key == .header }).tick == .arrive)
        let replay = try #require(beats.first { $0.key == .replay })
        #expect(replay.at == IntroScore.rowsAt)
        #expect(replay.tick == .light)
    }

    @Test("an emptied section deals no card", arguments: MonthSection.allCases)
    func emptied(section: MonthSection) throws {
        let wrap = Self.emptying(try MonthPayloadFixture.october(), section)
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(!cards.contains(.section(section)))
        #expect(cards.count == 8)
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

    @Test("the top card is drawn as it is, so its settled render is the page's")
    func topIsIdentity() {
        #expect(MonthDeckMotion.scale(0) == 1)
        #expect(MonthDeckMotion.turn(0) == 0)
        #expect(MonthDeckMotion.offset(0, page: 800) == 0)
    }

    @Test("a card to come is held in place beneath the top one, a little smaller, its foot peeking")
    func comingIsHeld() {
        // One page down, it is drawn a foot below the top: its layout (800) plus the offset.
        #expect(800 + MonthDeckMotion.offset(1, page: 800) == MonthDeckMotion.peek)
        // Half way up, it has risen half the foot, not half the page.
        #expect(400 + MonthDeckMotion.offset(0.5, page: 800) == MonthDeckMotion.peek / 2)
        #expect(abs(MonthDeckMotion.scale(1) - (1 - MonthDeckMotion.shrink)) < 1e-9)
    }

    @Test("a leaving card lifts and turns as it goes, and is not held")
    func leavingLifts() {
        #expect(abs(MonthDeckMotion.scale(-1) - (1 + MonthDeckMotion.lift)) < 1e-9)
        #expect(MonthDeckMotion.turn(-1) == -MonthDeckMotion.maxTurn)
        #expect(MonthDeckMotion.offset(-0.5, page: 800) == 0)
    }

    @Test("a card is on top once a throw rests on it, within a point; mid-throw, none is")
    func settled() {
        #expect(MonthDeckMotion.settled(offset: 0, page: 480) == 0)
        #expect(MonthDeckMotion.settled(offset: 960.4, page: 480) == 2)
        #expect(MonthDeckMotion.settled(offset: 959.2, page: 480) == 2)
        #expect(MonthDeckMotion.settled(offset: 700, page: 480) == nil)
        #expect(MonthDeckMotion.settled(offset: -0.5, page: 480) == 0)
        #expect(MonthDeckMotion.settled(offset: 100, page: 0) == nil)
    }

    @Test("the deck's static edges stand for the cards past the next, two at most")
    func edges() {
        #expect([0, 1, 2, 3, 7].map { MonthDeckMotion.edges(below: $0) } == [0, 0, 1, 2, 2])
    }

    @Test("each card knows its place in the deck, the segments filled up to it")
    func step() throws {
        let wrap = try MonthPayloadFixture.october()
        let cards = MonthDeck.cards(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
        #expect(MonthDeckStep.of(.cover, in: cards) == MonthDeckStep(index: 0, count: 9))
        let gem = try #require(MonthDeckStep.of(.section(.gem), in: cards))
        #expect(gem.index == 4)
        #expect((0..<gem.count).map(gem.isFilled) == [true, true, true, true, true, false, false, false, false])
        #expect(MonthDeckStep.of(.end, in: cards)?.index == 8)
    }

    @Test("a deck of one has no stepper, and a card it does not deal has no place")
    func noStep() {
        #expect(MonthDeckStep.of(.cover, in: [.cover]) == nil)
        #expect(MonthDeckStep.of(.section(.gem), in: [.cover, .section(.weeks)]) == nil)
    }

    @Test("a card's title is its first head as the page draws it; the cover has none, the month being its title")
    func titles() throws {
        let wrap = try MonthPayloadFixture.october()
        let lookups = MonthPayloadFixture.lookups(wrap)
        let cards = MonthDeck.cards(wrap: wrap, lookups: lookups)
        let head = { (card: IntroSequence) in
            MonthDeck.head(card, wrap: wrap, lookups: lookups, dayView: .circle,
                           leadsInsights: MonthDeck.leadsInsights(card, in: cards))
        }
        #expect(head(.cover) == nil)
        #expect(head(.end) == nil)
        #expect(head(.section(.weeks)) == WeeksSummary.head(wrap: wrap))
        #expect(head(.section(.persons)) == PersonsSection.head(wrap: wrap, lookups: lookups, dayView: .circle))
        #expect(head(.section(.gem)) == EmotionGem.head)
        // The tone leads the insights: its card is titled by their head, the hours by their own.
        #expect(head(.section(.tone)) == MonthSectionContent.insightsHead)
        #expect(head(.section(.hours)) == HoursFigure.head)
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
