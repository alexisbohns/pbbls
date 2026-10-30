import Foundation
import Testing
@testable import Pebbles

/// The sheets' rows (#1306), as Android's sheets read the payload: served
/// items in the server's order, named or given a fallback, dated by their
/// served `on`, opening a moment only with a moment id.
@Suite("Achievements sheet rows")
@MainActor
struct AchievementsSheetRowsTests {
    typealias Rows = AchievementsSheetRows

    static func context(_ given: WrapLookups? = nil) -> Rows.Context {
        let lookups = given ?? (try? AchievementsLookups.fixture()) ?? WrapLookups()
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        return Rows.Context(lookups: lookups, calendar: calendar, locale: Locale(identifier: "en"),
                            dateLocale: Locale(identifier: "en_US"))
    }

    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }
    static func uuid(_ text: String) throws -> UUID { try #require(UUID(uuidString: text)) }

    @Test("Explorer: the first five, the six firsts, the 25th, then the rows that open Domains and Emotions")
    func explorer() throws {
        let page = try Self.design()
        let sections = Rows.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains,
                                     Self.context())
        #expect(sections.map(\.id) == ["first-five", "six-firsts", "twenty-five", "domains-emotions"])
        let five = sections[0]
        let firstMoment = try Self.uuid("a0000000-0000-4000-8000-000000000000")
        let firstPebble = try Self.uuid("a0000000-0000-4000-8000-000000000001")
        #expect(five.note == "five of five")
        #expect(five.rows.map(\.name) == ["First moment", "First pebble", "First collection", "First soul", "First glyph"])
        #expect(five.rows[0].sub == "Untitled" && five.rows[0].opens == .moment(firstMoment))
        #expect(five.rows[1].sub == "Mara moves in" && five.rows[1].date == "Mar 2")
        #expect(five.rows[1].opens == .moment(firstPebble))
        #expect(five.rows[2].opens == nil && five.rows[2].mark == .firstFive(.collection), "a collection opens no moment")
        #expect(five.rows[2].sub == "Elio, year one" && five.rows[2].date == "Apr 10")
        #expect(five.rows[1].accessibilityLabel == "First pebble, Mara moves in, Mar 2")
        let six = sections[1]
        #expect(six.rows.map(\.name) == ["Small", "Medium", "Large", "Lowlight", "Neutral", "Highlight"])
        #expect(six.rows[3].sub == "Gym, finally" && six.rows[3].date == "Mar 8")
        #expect(sections[2].note == "twenty-five of twenty-five" && sections[2].rows.first?.name == "The 25th")
        let outer = sections[3].rows
        #expect(outer.map(\.opens) == [.sheet(.domains), .sheet(.emotions)])
        #expect(outer.map(\.sub) == ["14 of 18", "23 of 38"])
        #expect(outer.map(\.mark) == [.domain, .rosace])
    }

    @Test("a first day: what is not done is a ghost row, undated, opening nothing; the 25th is still to come")
    func newUser() throws {
        let page = try AchievementsFixtures.payload("new-user").model
        let sections = Rows.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains,
                                     Self.context())
        #expect(sections[0].note == "one of five")
        #expect(sections[0].rows[0].mark == .firstFive(.moment) && sections[0].rows[0].sub == "Untitled")
        #expect(sections[0].rows.dropFirst().allSatisfy { $0.mark == .ghost && $0.date == nil && $0.opens == nil && $0.sub == nil })
        #expect(sections[1].rows.allSatisfy { $0.mark == .ghost && $0.opens == nil })
        #expect(sections[0].rows[2].accessibilityLabel == "Not yet: First collection")
        #expect(sections[1].rows[0].accessibilityLabel == "Not yet: Small")
        #expect(sections[2].note == "zero of twenty-five" && sections[2].rows.isEmpty)
        #expect(sections[2].quiet == "The 25th pebble is still to come.")
    }

    @Test("an unknown task or kind keeps its row under a fallback name")
    func unknownExplorer() throws {
        let page = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [{"slug": "first-dance", "done": true, "on": "2026-03-02"}],
                      "six_firsts": [{"kind": "tiny", "done": true}], "rings_done": []}}
        """).model
        let sections = Rows.explorer(try #require(page.explorer), emotions: nil, domains: nil, Self.context())
        #expect(sections.map(\.id) == ["first-five", "six-firsts"])
        #expect(sections[0].rows.first?.name == "A first" && sections[0].rows.first?.mark == .ghost)
        #expect(sections[0].rows.first?.date == "Mar 2")
        #expect(sections[1].rows.first?.name == "A first")
    }

    @Test("Emotions: the server's families in its order, met by the day met, then the rest as names")
    func emotions() throws {
        let page = try Self.design()
        let sections = Rows.emotions(try #require(page.emotions), Self.context())
        #expect(sections.count == 7)
        let joy = sections[0]
        #expect(joy.family == "joy" && joy.note == "5 of 6")
        #expect(joy.rows.map(\.id) == ["seat-3", "seat-4", "seat-1", "seat-2", "seat-0"])
        #expect(joy.notYet.count == 1)
        #expect(sections[5].rows.first { $0.id == "seat-29" }?.isNew == true)
        let valence = try #require(page.emotions?.seat(3)?.pebble?.valence)
        #expect(joy.rows.first?.mark == .pebble(valence, family: "joy"))
    }

    @Test("an unknown family reads Other emotions; an unknown emotion, An emotion")
    func unknownEmotions() throws {
        let page = try AchievementsFixtures.json("""
        {"emotions": {"count": 1, "of": 2,
                      "seats": [{"seat": 0, "emotion_id": "x", "family": "awe", "met": true, "on": "2026-03-02"},
                                {"seat": 1, "family": "awe", "met": false}],
                      "families": [{"family": "awe", "met": [0], "not_yet": [1]}]}}
        """).model
        let sections = Rows.emotions(try #require(page.emotions), Self.context())
        #expect(sections.first?.title == "Other emotions")
        #expect(sections.first?.rows.first?.name == "An emotion" && sections.first?.rows.first?.opens == nil)
        #expect(sections.first?.notYet == ["An emotion"])
    }

    @Test("Domains: the register newest first, numbered in the order they came; then what is not yet")
    func domains() throws {
        let page = try Self.design()
        let sections = Rows.domains(try #require(page.domains), Self.context())
        let entered = sections[0].rows
        #expect(entered.count == 14 && sections[0].note == "newest first")
        #expect(entered.first?.number == "No. 14" && entered.first?.isNew == true && entered.first?.date == "Oct 1")
        #expect(entered.last?.number == "No. 01")
        #expect(sections[1].notYet.count == 4)
        let none = try AchievementsFixtures.json("""
        {"domains": {"count": 1, "of": 1, "coverage": "all", "entered": [{"on": "2026-03-02"}], "not_yet": []}}
        """).model
        let only = Rows.domains(try #require(none.domains), Self.context())
        #expect(only.count == 1 && only[0].rows.first?.name == "A domain")
    }

    @Test("a counting stack: the levels reached, the next with what is left, the other steps, the tail")
    func ladder() throws {
        let page = try Self.design()
        let ctx = Self.context()
        let pebbles = try #require(page.pebbles)
        #expect(Rows.milestonesNote(pebbles, locale: ctx.locale) == "five of eight")
        let section = Rows.ladder(pebbles, id: "milestones", title: "Milestones", note: nil, ctx)
        #expect(section.rows.map(\.name) == ["1", "10", "25", "50", "100", "250", "500", "1,000"])
        #expect(section.rows.map(\.style) == [.number, .number, .number, .number, .number, .next, .ghostNumber, .ghostNumber])
        #expect(section.rows[0].sub == "Mara moves in" && section.rows[0].date == "Mar 2")
        #expect(section.rows[5].next == "113 to go" && section.rows[5].mark == .stage(index: 5, fill: 0.247))
        #expect(section.rows[5].date == nil && section.rows[5].opens == nil, "the next milestone is never dated")
        #expect(section.trailing == "then every 500")
        #expect(section.rows[6].accessibilityLabel == "Not yet: 500")
        let carved = Rows.ladder(try #require(page.glyphsCarved), id: "carved", title: "Glyphs carved", note: "12", ctx)
        #expect(carved.rows.map(\.name) == ["10", "25", "50", "75", "100"])
        #expect(carved.rows[0].sub == nil && carved.rows[0].date == "Sep 21" && carved.rows[0].opens == nil)
        #expect(carved.trailing == nil)
        let sales = Rows.ladder(try #require(page.glyphSales), id: "sales", title: "Glyph sales", note: "3",
                                closing: "The buyer is never named.", ctx)
        #expect(sales.closing == "The buyer is never named." && sales.trailing == nil,
                "a closing line, never a row and never in the tail's place")
    }

    @Test("a stack with no ladder draws no stage for its levels")
    func emptyLadder() throws {
        let page = try AchievementsFixtures.json("""
        {"pebbles": {"count": 3, "levels": [{"threshold": 1, "on": "2026-03-02"}], "ladder": [],
                     "next": {"threshold": 10, "left": 7, "progress": 0.2}}}
        """).model
        let section = Rows.ladder(try #require(page.pebbles), id: "milestones", title: "Milestones", note: nil, Self.context())
        #expect(section.rows.map(\.mark) == [.stage(index: -1, fill: 1), .stage(index: -1, fill: 0.2)])
        #expect(SkylineGeometry.drawn(-1) == nil)
    }

    @Test("the year: the listed months newest first, the served counts uncapped, done from the server")
    func year() throws {
        let page = try Self.design()
        let year = try #require(page.years.first)
        let rows = Rows.months(year, Self.context())
        #expect(rows.map(\.month.month) == [11, 10, 9, 8, 7, 6, 5, 4, 3])
        let november = rows[0]
        #expect(november.name == "November")
        #expect(november.moments == YearCell(value: "2", target: nil, fill: 2.0 / 14, isDone: false, usesHue: false))
        #expect(november.pebbles.value == "2" && november.pebbles.usesHue)
        #expect(november.weeks == YearCell(value: "—", target: nil, fill: nil, isDone: false, usesHue: false))
        let august = rows[3]
        #expect(august.moments == YearCell(value: "19", target: nil, fill: 1, isDone: true, usesHue: false),
                "19 days, uncapped; the gauge caps in the drawing")
        #expect(august.weeks == YearCell(value: "4", target: "/4", fill: 1, isDone: true, usesHue: false))
        #expect(november.accessibilityLabel == "November. Days with a moment: 2. With a pebble: 2. No full week yet.")
        let earlier = Rows.earlierYears(year, all: page.years, Self.context())
        #expect(earlier.rows.isEmpty && earlier.quiet == "None yet. 2026 is your first year here.")
    }

    @Test("Earlier years lists only the years before the one open")
    func earlierYears() throws {
        let years = try AchievementsHarnessPayloads.payload("get-achievements-year").model.years
        let current = try #require(years.first { $0.year == 2026 })
        let past = try #require(years.first { $0.year == 2025 })
        #expect(Rows.earlierYears(current, all: years, Self.context()).rows.map(\.opens) == [.sheet(.year(2025))])
        let first = Rows.earlierYears(past, all: years, Self.context())
        #expect(first.rows.isEmpty && first.quiet == "None yet. 2025 is your first year here.")
    }
}
