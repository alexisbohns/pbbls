import Foundation
import Testing
@testable import Pebbles

/// Payload → page (#1306): one-to-one, typed, clamped, every unknown enum on its
/// fallback (D17). Nothing is counted, compared or ordered by the mapping.
@Suite("Achievements page mapping")
struct AchievementsMappingTests {
    @Test("the design fixture maps to the page the views draw")
    func design() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        #expect(page.since == CalendarDay(year: 2026, month: 3, day: 1) && page.daysKept == 249)

        let explorer = try #require(page.explorer)
        #expect(explorer.ringsDone == [.firstFive, .sixFirsts, .twentyFive])
        #expect(explorer.firstFive.compactMap(\.task) == HomeChecklist.Task.allCases)
        #expect(explorer.firstFive[1].subjectId == UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))
        #expect(explorer.firstFive[1].subjectKind == .pebble)
        #expect(explorer.sixFirsts.compactMap(\.kind) == AchievementsPage.StoneKind.allCases)
        #expect(explorer.sixFirsts[3].pebble?.valence == .lowlightMedium)
        #expect(explorer.sixFirsts[3].on == CalendarDay(year: 2026, month: 3, day: 8))
        #expect(explorer.twentyFive?.on == CalendarDay(year: 2026, month: 4, day: 21))
        #expect(explorer.firstPebble?.valence == .highlightMedium)
        #expect(explorer.completion?.stack == .sixFirsts && explorer.completion?.isReady == false)
        #expect(explorer.completion?.pulledAt != nil, "the board's six firsts were pulled long ago: no ready mark")
        #expect(explorer.completion?.pebble?.valence == .lowlightMedium)
        #expect(page.completion(.sixFirsts) == explorer.completion)
        #expect(page.completion(.emotions) == nil && page.completion(.domains) == nil)

        let emotions = try #require(page.emotions)
        #expect(emotions.families.map(\.family) == ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
        #expect(emotions.families[5].met == [26, 24, 28, 29])
        #expect(emotions.seat(29)?.isNew == true && emotions.seat(29)?.on == CalendarDay(year: 2026, month: 10, day: 6))
        #expect(emotions.since == CalendarDay(year: 2026, month: 3, day: 2))

        let domains = try #require(page.domains)
        #expect(domains.coverage == .most)
        #expect(domains.entered.first?.domainId == "d0000000-0000-4000-8000-000000000002")
        #expect(domains.entered.first?.on == CalendarDay(year: 2026, month: 10, day: 1))
        #expect(domains.arrival.first?.domainId == "d0000000-0000-4000-8000-00000000000c")
        #expect(domains.arrival.last?.isNew == true)

        let pebbles = try #require(page.pebbles)
        #expect(pebbles.reachedSteps == [1, 10, 25, 50, 100])
        #expect(pebbles.isReached(100) && !pebbles.isReached(250))
        #expect(pebbles.since == CalendarDay(year: 2026, month: 3, day: 2))
        #expect(page.glyphSales?.since == CalendarDay(year: 2026, month: 10, day: 2))

        let year = try #require(page.years.first)
        #expect(year.isCurrent && year.id == 2026 && year.daysTarget == 14)
        #expect(year.month(8)?.level == 2 && year.month(8)?.isMuted == false)
        #expect(year.month(3)?.done == AchievementsPage.MonthDone(moments: true, pebbles: false, weeks: true))
        #expect(year.month(1)?.state == .quiet && year.month(1)?.isMuted == true)
        #expect(year.month(12)?.state == .upcoming && year.month(12)?.isMuted == true)
        #expect(year.nextStep == AchievementsPage.NextStep(month: 11, needs: .moments, daysLeft: 12, reachable: true))

        let seen = try #require(ISO8601Flexible.parse("2026-10-02T19:00:00Z"))
        #expect(page.seen == [.explorer: seen, .emotions: seen, .domains: seen, .pebbles: seen])
        #expect(page.shelf.map(\.slug)
            == ["glyph-sales-1", "glyph-count-10", "pebble-count-100", "pebble-count-50", "blue-pebble", "first-glyph"])
        #expect(page.shelfCount == 13)
    }

    /// Twelve months: `special` by month number, the rest plain.
    static func months(_ special: [Int: String]) -> String {
        (1...12).map { special[$0] ?? #"{"month": \#($0), "state": "quiet"}"# }.joined(separator: ", ")
    }

    @Test("values this build does not know fall back safely (D17)")
    func fallbacks() throws {
        let months = Self.months([3: #"{"month": 3, "state": "dormant", "level": 5, "progress": 1.4}"#,
                                  4: #"{"month": 4, "state": "kept", "level": -1, "progress": -0.2}"#])
        let page = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [{"slug": "first-dance", "done": true},
                                     {"slug": "first-moment", "done": true,
                                      "subject": {"kind": "boat", "id": "a0000000-0000-4000-8000-000000000001"}}],
                      "six_firsts": [{"kind": "tiny", "done": true}], "rings_done": ["constellation", "six_firsts"]},
         "domains": {"count": 1, "of": 18, "coverage": "plenty", "entered": [], "not_yet": []},
         "emotions": {"count": 0, "of": 1, "seats": [{"seat": 0, "emotion_id": "x", "family": "awe", "met": false}],
                      "families": [{"family": "awe", "met": [], "not_yet": [0]}]},
         "years": [{"year": 2026, "current": true, "listed": [0, 4, 3, 13],
                    "months": [\(months)],
                    "next_step": {"month": 3, "needs": "photos", "days_left": 2, "reachable": true}},
                   {"year": 2025, "current": false, "listed": [], "months": [{"month": 1, "state": "quiet"}]}],
         "seen": {"constellation": "2026-10-02T08:14:05Z", "year": "2026-10-02T08:14:05Z"}}
        """).model
        #expect(page.explorer?.firstFive.count == 2, "an unknown task keeps its seat")
        #expect(page.explorer?.firstFive.first?.task == nil)
        let boat = try #require(page.explorer?.firstFive.last)
        #expect(boat.task == .moment && boat.subjectKind == nil && boat.subjectId != nil,
                "an unknown subject kind is kept, its kind nil")
        #expect(page.explorer?.sixFirsts.count == 1 && page.explorer?.sixFirsts.first?.kind == nil)
        #expect(page.explorer?.ringsDone == [.sixFirsts])
        #expect(page.domains?.coverage == .unknown)
        #expect(page.emotions?.seats.first?.family == "awe")
        #expect(page.emotions?.families.first?.family == "awe")
        let march = try #require(page.years.first?.month(3))
        #expect(march.state == .quiet && march.level == 2 && march.progress == 1)
        let april = try #require(page.years.first?.month(4))
        #expect(april.level == 0 && april.progress == 0)
        #expect(!april.isMuted, "a kept month without a family still draws, in ink")
        #expect(page.years.map(\.year) == [2026], "a year without twelve months can't be drawn")
        #expect(page.years.first?.listed == [4, 3])
        #expect(page.years.first?.nextStep == nil)
        #expect(page.years.first?.daysTarget == nil)
        #expect(Array(page.seen.keys) == [.year])
        #expect(page.shelf.isEmpty && page.shelfCount == nil)
    }

    @Test("a pebble outside the 3 × 3 grid draws as the even medium stone")
    func offGridPebble() throws {
        let page = try AchievementsFixtures.json(#"{"explorer": {"first_pebble": {"intensity": 4, "positiveness": 2}}}"#).model
        #expect(page.explorer?.firstPebble?.valence == .neutralMedium)
    }

    @Test("each completion carries its stack, its pebble, and pulled_at when pulled")
    func completions() throws {
        let page = try AchievementsFixtures.json("""
        {"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-01T10:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-03T08:00:00.5+00:00",
                                     "pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001",
                                                "happened_at": "2026-10-01T09:00:00Z", "intensity": 3, "positiveness": 1}}},
         "domains": {"count": 18, "of": 18, "coverage": "all", "entered": [], "not_yet": [],
                     "completion": {"completed_at": "2026-10-01T10:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                    "ready": true}}}
        """).model
        #expect(page.emotions?.completion?.stack == .emotions)
        #expect(page.emotions?.completion?.pulledAt != nil && page.emotions?.completion?.isReady == false)
        #expect(page.emotions?.completion?.pebble?.valence == .highlightLarge)
        #expect(page.domains?.completion?.stack == .domains && page.domains?.completion?.isReady == true)
        #expect(page.domains?.completion?.pulledAt == nil && page.domains?.completion?.pebble == nil)
        #expect(page.completion(.domains) == page.domains?.completion)
    }
}
