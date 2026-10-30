import Testing
@testable import Pebbles

/// Each card's margin ladder (#1306, pick 6): one bar per ring, family, step or
/// month. The done style is a server fact only; a fill reaching 1 never shows it.
@Suite("Achievements margin ladders")
struct MarginLadderTests {
    static func design() throws -> AchievementsPage { try AchievementsFixtures.payload("design-fixture").model }
    static func bar(_ fill: Double) -> LadderBar { LadderBar(fill: fill, done: false) }

    @Test("Explorer: a bar per ring, done when the server lists the ring in rings_done")
    func explorer() throws {
        let page = try Self.design()
        let bars = MarginLadders.explorer(try #require(page.explorer), emotions: page.emotions, domains: page.domains)
        #expect(bars == [.full, .full, .full, Self.bar(14.0 / 18), Self.bar(23.0 / 38)])
        let fresh = try AchievementsFixtures.payload("new-user").model
        #expect(MarginLadders.explorer(try #require(fresh.explorer), emotions: fresh.emotions, domains: fresh.domains)
            == [Self.bar(0.2), Self.bar(0), Self.bar(0), Self.bar(0), Self.bar(0)])
    }

    @Test("Emotions: a bar per served family, done when its not_yet is empty; Domains: done on `all`")
    func emotionsAndDomains() throws {
        let page = try Self.design()
        let bars = MarginLadders.emotions(try #require(page.emotions))
        #expect(bars.map(\.fill) == [5.0 / 6, 1, 3.0 / 6, 1.0 / 5, 1.0 / 4, 4.0 / 6, 6.0 / 8])
        #expect(bars.map(\.done) == [false, true, false, false, false, false, false], "pride: every seat met")
        #expect(MarginLadders.domains(try #require(page.domains)) == Self.bar(14.0 / 18))
    }

    @Test("a fill reaching 1 is not done unless the server says so")
    func fillIsNotDone() throws {
        let page = try AchievementsFixtures.json("""
        {"domains": {"count": 18, "of": 18, "coverage": "most", "entered": [], "not_yet": []},
         "emotions": {"count": 0, "of": 0, "seats": [], "families": [{"family": "joy", "met": [], "not_yet": []}]},
         "pebbles": {"count": 9, "levels": [], "ladder": [1, 10], "next": {"threshold": 1, "left": 0, "progress": 1}},
         "years": [{"year": 2026, "current": true, "listed": [],
                    "months": [\((1...12).map { #"{"month": \#($0), "state": "kept", "level": 1, "progress": 1}"# }
                        .joined(separator: ", "))]}]}
        """).model
        #expect(MarginLadders.domains(try #require(page.domains)) == LadderBar(fill: 1, done: false))
        #expect(MarginLadders.emotions(try #require(page.emotions)) == [Self.bar(0)], "an empty family is not done")
        #expect(MarginLadders.ladder(try #require(page.pebbles)) == [LadderBar(fill: 1, done: false), Self.bar(0)])
        #expect(MarginLadders.year(try #require(page.years.first)).allSatisfy { $0.fill == 1 && !$0.done })
        let all = try AchievementsFixtures.json("""
        {"domains": {"count": 18, "of": 18, "coverage": "all", "entered": [], "not_yet": []}}
        """).model
        #expect(MarginLadders.domains(try #require(all.domains)) == .full)
    }

    @Test("a counting stack: listed levels done, the next at the server's progress, the rest empty")
    func ladder() throws {
        let page = try Self.design()
        #expect(MarginLadders.ladder(try #require(page.pebbles))
            == [.full, .full, .full, .full, .full, Self.bar(0.247), Self.bar(0), Self.bar(0)])
        #expect(MarginLadders.ladder(try #require(page.glyphsCarved)) == [.full, Self.bar(0.133), Self.bar(0), Self.bar(0), Self.bar(0)])
    }

    @Test("the year: a bar per month, its place on the spoke over two, done on the rim; a month to come is empty")
    func year() throws {
        let bars = MarginLadders.year(try #require(try Self.design().years.first))
        #expect(bars.count == 12)
        #expect(bars[0] == Self.bar(0) && bars[7] == .full && bars[11] == Self.bar(0))
        #expect(abs(bars[2].fill - (1 + 0.857) / 2) < 1e-9 && !bars[2].done)
        #expect(abs(bars[10].fill - 0.143 / 2) < 1e-9)
    }

    @Test("a bar's fill is clamped to the drawing")
    func clamped() {
        #expect(LadderBar(fill: 1.4, done: false).fill == 1 && LadderBar(fill: -0.2, done: true).fill == 0)
    }
}
