import Foundation
import Testing
@testable import Pebbles

/// `get_achievements` decoded from the board's fixture (#1306): the design's values
/// in the shape the server serves (spec §3.3), the precisions every surface emits,
/// omitted sections and a section that does not decode. The contract itself is
/// proved on the server's own bodies in `AchievementsHarnessPayloadTests`; this
/// fixture stays the mapping's, the copy's and the references' (the dumps hold
/// a handful of pebbles, the board 137).
@Suite("Achievements payload decoding")
struct AchievementsPayloadTests {
    @Test("the design fixture decodes every section")
    func designFixture() throws {
        let payload = try AchievementsFixtures.payload("design-fixture")
        #expect(payload.skipped.isEmpty)
        #expect(payload.since == CalendarDay(year: 2026, month: 3, day: 1))
        #expect(payload.daysKept == 249)

        let explorer = try #require(payload.explorer)
        #expect(explorer.firstFive.map(\.slug)
            == ["first-moment", "first-pebble", "first-collection", "first-soul", "first-glyph"])
        #expect(explorer.firstFive[2].on == CalendarDay(year: 2026, month: 3, day: 14))
        #expect(explorer.firstFive[2].subject?.kind == "collection")
        #expect(explorer.firstFive[2].subject?.name == "Summer")
        #expect(explorer.sixFirsts.map(\.kind) == ["small", "medium", "large", "lowlight", "neutral", "highlight"])
        #expect(explorer.sixFirsts[2].pebble?.positiveness == -1)
        #expect(explorer.sixFirsts[2].on == CalendarDay(year: 2026, month: 3, day: 9))
        #expect(explorer.twentyFive?.count == 25)
        #expect(explorer.twentyFive?.on == CalendarDay(year: 2026, month: 4, day: 2))
        #expect(explorer.ringsDone == ["first_five", "six_firsts", "twenty_five"])
        #expect(explorer.completion?.ready == true)
        #expect(explorer.completion?.pulledAt == nil)
        #expect(explorer.completion?.offeredFrom == ISO8601Flexible.parse("2026-03-10T05:00:00Z"))
        #expect(explorer.completion?.pebble?.name == "Argument about money")
        #expect(explorer.firstPebble?.name == "Mara moves in")
        #expect(explorer.firstPebble?.happenedAt == ISO8601Flexible.parse("2026-03-02T09:30:00Z"))

        let emotions = try #require(payload.emotions)
        #expect(emotions.count == 23 && emotions.of == 38)
        #expect(emotions.seats.count == 38)
        #expect(emotions.seats.filter(\.met).count == 23)
        #expect(emotions.seats.filter(\.isNew).map(\.seat) == [31, 35])
        #expect(emotions.seats[5].pebble?.momentId == UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))
        #expect(emotions.seats[5].on == CalendarDay(year: 2026, month: 3, day: 2))
        #expect(emotions.seats[4].on == nil && emotions.seats[4].pebble == nil)
        #expect(emotions.families.map(\.family) == ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
        #expect(emotions.families[0].met == [5, 2, 0, 1, 3] && emotions.families[0].notYet == [4])
        #expect(emotions.since == CalendarDay(year: 2026, month: 3, day: 2))
        #expect(emotions.completion == nil)

        let domains = try #require(payload.domains)
        #expect(domains.coverage == "most")
        #expect(domains.entered.count == 14 && domains.notYet.count == 4)
        #expect(domains.entered.first?.isNew == true)
        #expect(domains.entered.first?.on == CalendarDay(year: 2026, month: 10, day: 17))
        #expect(domains.completion == nil)

        #expect(payload.pebbles?.count == 137)
        #expect(payload.pebbles?.levels.map(\.threshold) == [1, 10, 25, 50, 100])
        #expect(payload.pebbles?.next == AchievementsPayload.Next(threshold: 250, left: 113, progress: 0.247))
        #expect(payload.pebbles?.thenEvery == 500)
        #expect(payload.pebbles?.since == CalendarDay(year: 2026, month: 3, day: 2))
        #expect(payload.glyphsCarved?.thenEvery == nil)
        #expect(payload.glyphsCarved?.since == CalendarDay(year: 2026, month: 6, day: 12))
        #expect(payload.glyphSales?.levels.first?.pebble == nil)
        #expect(payload.glyphSales?.levels.first?.on == CalendarDay(year: 2026, month: 5, day: 30))

        let year = try #require(payload.years?.first)
        #expect(year.year == 2026 && year.current)
        #expect(year.daysTarget == 14)
        #expect(year.months.count == 12)
        #expect(year.months[7].level == 2 && year.months[7].family == "joy")
        #expect(year.months[7].done == AchievementsPayload.Done(moments: true, pebbles: true, weeks: true))
        #expect(year.months[6].done == AchievementsPayload.Done(moments: true, pebbles: true, weeks: false))
        #expect(year.months[11].state == "upcoming" && year.months[11].family == nil)
        #expect(year.listed == [11, 10, 9, 8, 7, 6, 5, 4, 3])
        #expect(year.nextStep == AchievementsPayload.NextStep(month: 11, needs: "moments", daysLeft: 12, reachable: true))

        let seen = try #require(ISO8601Flexible.parse("2026-10-02T08:14:05Z"))
        #expect(payload.seen == ["explorer": seen, "emotions": seen, "domains": seen, "pebbles": seen])
        #expect(payload.shelf?.count == 6 && payload.shelfCount == 12)
        #expect(payload.shelf?.first == AchievementsPayload.ShelfItem(
            slug: "pebble-count-100", family: "pebble_count", threshold: 100,
            unlockedOn: CalendarDay(year: 2026, month: 8, day: 1)))
        #expect(payload.shelf?.last?.threshold == nil && payload.shelf?.last?.family == "first_glyph")
    }

    @Test("a pebble's happened_at reads at every precision the surfaces emit", arguments: [
        "2026-03-02T09:30:00Z", "2026-03-02T09:30:00.123Z", "2026-03-02T09:30:00.123456+00:00"
    ])
    func precision(raw: String) throws {
        let payload = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [], "six_firsts": [], "rings_done": [],
         "first_pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001",
                          "happened_at": "\(raw)", "intensity": 2, "positiveness": 1}},
         "seen": {"emotions": "\(raw)"}}
        """)
        let pebble = try #require(payload.explorer?.firstPebble)
        let seconds = try #require(pebble.happenedAt?.timeIntervalSince1970)
        #expect(abs(seconds - 1_772_443_800) < 1)
        #expect(pebble.name == nil && pebble.emotionId == nil, "an unnamed moment omits its name (D17)")
        let seen = try #require(payload.seen["emotions"]?.timeIntervalSince1970)
        #expect(abs(seen - 1_772_443_800) < 1)
    }

    @Test("explicit nulls read as absent, as an omitted key does")
    func explicitNulls() throws {
        let payload = try AchievementsFixtures.json("""
        {"explorer": {"first_pebble": {"moment_id": "a0000000-0000-4000-8000-000000000001", "name": null,
                                       "happened_at": null, "emotion_id": null, "intensity": null, "positiveness": null},
                      "first_five": null, "six_firsts": [], "rings_done": [], "twenty_five": null, "completion": null},
         "shelf": [{"slug": "first-moment", "family": "first_moment", "threshold": null, "unlocked_on": null}]}
        """)
        let pebble = try #require(payload.explorer?.firstPebble)
        #expect(pebble.name == nil && pebble.happenedAt == nil && pebble.emotionId == nil)
        #expect(pebble.intensity == 2 && pebble.positiveness == 0)
        #expect(payload.explorer?.firstFive == [] && payload.explorer?.twentyFive == nil)
        #expect(payload.explorer?.completion == nil)
        #expect(payload.shelf?.first?.threshold == nil && payload.shelf?.first?.unlockedOn == nil)
        #expect(payload.skipped.isEmpty)
    }

    @Test("absent sections stay absent and are not errors")
    func absentSections() throws {
        let payload = try AchievementsFixtures.json(#"{"years": []}"#)
        #expect(payload.explorer == nil && payload.emotions == nil && payload.domains == nil)
        #expect(payload.pebbles == nil && payload.glyphsCarved == nil && payload.glyphSales == nil)
        #expect(payload.since == nil && payload.daysKept == nil)
        #expect(payload.seen.isEmpty && payload.shelf == nil && payload.shelfCount == nil)
        #expect(payload.skipped.isEmpty)
    }

    @Test("a section that does not decode is dropped and named; the others survive")
    func brokenSection() throws {
        let payload = try AchievementsFixtures.json("""
        {"since": "2026-03-01", "days_kept": 249, "domains": {"count": "fourteen"},
         "pebbles": {"count": 3, "levels": [], "ladder": [1, 10],
                     "next": {"threshold": 10, "left": 7, "progress": 0.222}}}
        """)
        #expect(payload.domains == nil)
        #expect(payload.skipped == ["domains"])
        #expect(payload.pebbles?.count == 3)
        #expect(payload.daysKept == 249)
    }

    @Test("missing scalars take their defaults; a malformed day, id or seen time reads as absent")
    func defaults() throws {
        let payload = try AchievementsFixtures.json("""
        {"pebbles": {"levels": [{"threshold": 1, "on": "2 March"}]},
         "explorer": {"first_five": [{"slug": "first-moment", "subject": {"kind": "moment", "id": "not-a-uuid"}}]},
         "years": [{"year": 2026, "months": [{"month": 1}]}],
         "seen": {"explorer": "yesterday", "pebbles": "2026-10-02T08:14:05Z"}}
        """)
        #expect(payload.pebbles?.count == 0 && payload.pebbles?.ladder == [] && payload.pebbles?.next == nil)
        #expect(payload.pebbles?.since == nil)
        #expect(payload.pebbles?.levels.first?.on == nil)
        #expect(payload.pebbles?.levels.first?.isNew == false)
        #expect(payload.explorer?.firstFive.first?.done == false)
        #expect(payload.explorer?.firstFive.first?.subject?.id == nil)
        #expect(payload.years?.first?.daysTarget == nil)
        #expect(payload.years?.first?.months.first?.done == AchievementsPayload.Done(moments: false, pebbles: false, weeks: false))
        #expect(payload.seen.keys.sorted() == ["pebbles"])
        #expect(payload.skipped.isEmpty)
    }
}
