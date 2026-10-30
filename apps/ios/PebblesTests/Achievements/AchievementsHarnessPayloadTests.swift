import Foundation
import Testing
@testable import Pebbles

/// The contract on the server's own bodies (#1306, the foreign-payload rule):
/// each dump decodes with nothing dropped, carries what the harness asserted on
/// the server, and has no key the design fixture lacks, so every key the server
/// sends is one this decoder was written against.
@Suite("Achievements harness payloads")
struct AchievementsHarnessPayloadTests {
    typealias H = AchievementsHarnessPayloads

    @Test("every get_achievements dump decodes with no section dropped", arguments: H.reads)
    func decodes(dump: String) throws {
        let payload = try H.payload(dump)
        #expect(payload.skipped.isEmpty)
        #expect(payload.explorer != nil && payload.emotions != nil && payload.domains != nil)
        #expect(payload.pebbles != nil && payload.glyphsCarved != nil && payload.glyphSales != nil)
        #expect(payload.since != nil && payload.daysKept != nil && payload.shelf != nil && payload.shelfCount != nil)
    }

    @Test("get_achievements for three pebbles: the six firsts done, one emotion and one domain, 3 of 10")
    func threePebbles() throws {
        let page = try H.payload("get-achievements").model
        let explorer = try #require(page.explorer)
        #expect(explorer.firstPebble != nil)
        #expect(explorer.sixFirsts.compactMap(\.kind) == AchievementsPage.StoneKind.allCases)
        #expect(explorer.sixFirsts.allSatisfy { $0.done && $0.on != nil && $0.pebble != nil })
        #expect(explorer.firstFive.compactMap(\.task) == HomeChecklist.Task.allCases)
        #expect(explorer.firstFive.filter(\.done).allSatisfy { $0.on != nil && $0.subjectId != nil })
        #expect(explorer.firstFive.filter { !$0.done }.allSatisfy { $0.on == nil && $0.subjectKind == nil })
        #expect(explorer.ringsDone == [.sixFirsts])
        #expect(explorer.twentyFive?.count == 3 && explorer.twentyFive?.done == false && explorer.twentyFive?.on == nil)
        let completion = try #require(page.completion(.sixFirsts))
        #expect(!completion.isReady && completion.pulledAt == nil && completion.pebble != nil)
        #expect(completion.offeredFrom.map { $0 > (completion.completedAt ?? .distantFuture) } == true)

        let emotions = try #require(page.emotions)
        #expect(emotions.seats.count == 38 && emotions.count == 1 && emotions.of == 38)
        #expect(emotions.seats.filter(\.met).count == 1)
        #expect(emotions.seats.filter { !$0.met }.allSatisfy { $0.on == nil && $0.pebble == nil })
        #expect(emotions.families.map(\.family) == ["joy", "pride", "anger", "fear", "shame", "sadness", "peace"])
        #expect(emotions.families.flatMap { $0.met + $0.notYet }.count == 38)
        #expect(emotions.since == CalendarDay(year: 2026, month: 3, day: 2))

        let domains = try #require(page.domains)
        #expect(domains.count == 1 && domains.coverage == .few && domains.entered.count == 1)
        #expect(domains.entered.first?.on != nil && domains.notYet.count == domains.of - 1)

        let pebbles = try #require(page.pebbles)
        #expect(pebbles.count == 3)
        #expect(pebbles.next == AchievementsPage.Next(threshold: 10, left: 7, progress: 0.222))
        #expect(pebbles.thenEvery == 500 && pebbles.since == CalendarDay(year: 2026, month: 3, day: 2))
        #expect(page.glyphsCarved?.since == nil && page.glyphsCarved?.thenEvery == nil)
        #expect(explorer.sixFirsts.allSatisfy { !$0.isNew } && pebbles.levels.allSatisfy { !$0.isNew })

        let year = try #require(page.years.first)
        #expect(year.isCurrent && year.months.count == 12 && year.daysTarget == 14)
        #expect(page.seen.isEmpty)
        #expect(page.shelf.map(\.slug) == ["pebble-count-1", "first-moment", "first-pebble"])
        #expect(page.shelf.first?.threshold == 1 && page.shelf[1].threshold == nil, "an explicit null threshold")
        #expect(page.shelfCount == 3)
    }

    @Test("get_achievements with a past year: the current year then 2025, each a whole, bounded disc")
    func years() throws {
        let years = try H.payload("get-achievements-year").model.years
        #expect(years.count == 2 && years[0].isCurrent && years[1].year == 2025 && !years[1].isCurrent)
        #expect(years[0].nextStep != nil && years[1].nextStep == nil)
        for year in years {
            #expect(year.daysTarget == 14)
            #expect(year.months.map(\.month) == Array(1...12))
            #expect(year.listed == year.listed.sorted(by: >))
            #expect(year.months.allSatisfy { (0...2).contains($0.level) && (0...1).contains($0.progress) })
            #expect(year.months.allSatisfy { $0.state != .kept || $0.family != nil })
            #expect(year.months.allSatisfy { $0.state != .upcoming || $0.family == nil })
        }
        let june = try #require(years[1].month(6))
        #expect(june.level == 2 && june.done == AchievementsPage.MonthDone(moments: true, pebbles: true, weeks: true))
        #expect(years[1].month(4)?.done == AchievementsPage.MonthDone(moments: false, pebbles: false, weeks: true))
    }

    @Test("after the emotions card was seen, the one emotion first recorded since is new")
    func seen() throws {
        let page = try H.payload("get-achievements-seen").model
        let new = page.emotions?.seats.filter(\.isNew) ?? []
        #expect(new.count == 1 && new.first?.on != nil)
        #expect(Set(page.seen.keys) == [.emotions, .explorer])
        #expect(page.emotions?.families.first?.met.count == 2)
    }

    @Test("a pulled completion carries pulled_at and is no longer ready")
    func pulled() throws {
        let page = try H.payload("get-achievements-pulled").model
        let completion = try #require(page.completion(.sixFirsts))
        #expect(completion.pulledAt != nil && !completion.isReady && completion.pebble != nil)
    }

    @Test("check_achievements over a heavy first pebble returns only first-moment: silent rows never come back")
    func check() throws {
        let rows = try JSONDecoder().decode([AchievementCheckResult].self, from: H.data("check-achievements"))
        #expect(rows == [AchievementCheckResult(slug: "first-moment", karmaGranted: 0)])
    }

    @Test("every key the server sends is one the design fixture carries")
    func shape() throws {
        // The board's Explorer completion is ready, so not pulled: `pulled_at` is
        // carried by the pulled dump alone (and asserted in `pulled`).
        let design = try H.keyPaths(AchievementsFixtures.data("design-fixture"))
            .union(["explorer.completion.pulled_at"])
        for dump in H.reads {
            let unknown = try H.keyPaths(H.data(dump)).subtracting(design)
            #expect(unknown.isEmpty, "\(dump) sends keys the fixture lacks: \(unknown.sorted())")
        }
    }
}
