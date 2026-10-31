import SwiftUI
import Testing
@testable import Pebbles

/// The figures over payloads the board never drew (#1306, spec §4 tolerance
/// rules): seat counts other than 38, 18 and 5, and a family no lookup resolves.
/// Each places its seats by the counts the server sent and renders without
/// trapping; an unresolved family draws in ink.
@Suite("Achievements figure tolerance")
@MainActor
struct AchievementsFigureToleranceTests {
    /// `count` emotion seats round the wheel, every third met, the families in runs of six.
    static func emotions(_ count: Int, family: (Int) -> String? = { EmotionFamilies.wheel[min($0 / 6, 6)] })
        -> AchievementsPage.Emotions {
        let seats = (0..<count).map { k in
            AchievementsPage.Seat(seat: k, emotionId: nil, family: family(k), met: k % 3 == 0, on: nil, pebble: nil,
                                  isNew: k == 1)
        }
        return AchievementsPage.Emotions(count: seats.filter(\.met).count, of: count, seats: seats, families: [],
                                         since: nil, completion: nil)
    }

    static func domains(entered: Int, notYet: Int) -> AchievementsPage.Domains {
        AchievementsPage.Domains(
            count: entered, of: entered + notYet, coverage: .half,
            entered: (0..<entered).map { AchievementsPage.DomainEntry(domainId: "d\($0)", on: nil, pebble: nil, isNew: $0 == 0) },
            notYet: (0..<notYet).map { "n\($0)" }, completion: nil)
    }

    static func explorer(firstFive: Int) throws -> AchievementsPage.Explorer {
        let items = (0..<firstFive).map { #"{"slug": "first-\#($0)", "done": \#($0 % 2 == 0), "new": \#($0 == 5)}"# }
        let page = try AchievementsFixtures.json("""
        {"explorer": {"first_five": [\(items.joined(separator: ", "))], "six_firsts": [], "rings_done": []}}
        """).model
        return try #require(page.explorer)
    }

    static func renders(_ view: some View) -> Bool {
        ImageRenderer(content: view.frame(width: 300)).uiImage != nil
    }

    @Test("37 and 39 seats: the wheel and the rosace seat what the server sent", arguments: [37, 39])
    func emotionCounts(count: Int) throws {
        let geometry = RosaceGeometry(side: 270, count: count, labelled: true)
        #expect(geometry.silhouette.count == count * 2)
        let step = ExplorerWheelGeometry.emotionAngle(1, of: count) - ExplorerWheelGeometry.emotionAngle(0, of: count)
        #expect(abs(step - 360 / Double(count)) < 1e-9)
        let emotions = Self.emotions(count)
        #expect(FamilyRun.runs(emotions.seats.map(\.family)).reduce(0) { $0 + $1.length } == count)
        let explorer = try Self.explorer(firstFive: 5)
        #expect(Self.renders(ExplorerWheel(explorer: explorer, emotions: emotions, domains: nil, lookups: WrapLookups())))
        #expect(Self.renders(EmotionRosace(emotions: emotions, lookups: WrapLookups())))
    }

    @Test("17 domains: the ring and the band space what the server sent")
    func seventeenDomains() throws {
        let domains = Self.domains(entered: 10, notYet: 7)
        #expect(abs(ExplorerWheelGeometry.seatAngle(16, of: 17) - 16.5 * 360 / 17) < 1e-9)
        #expect(abs(ShardBandGeometry(width: 300, height: 30, count: 17).shard(16)[1].x - 300) < 1e-9)
        let explorer = try Self.explorer(firstFive: 5)
        #expect(Self.renders(ExplorerWheel(explorer: explorer, emotions: nil, domains: domains, lookups: WrapLookups())))
        #expect(Self.renders(DomainBand(domains: domains)))
    }

    @Test("six first-five items, unknown ones among them, sit round their ring as ghosts")
    func sixFirstFive() throws {
        let explorer = try Self.explorer(firstFive: 6)
        #expect(explorer.firstFive.count == 6 && explorer.firstFive.allSatisfy { $0.task == nil })
        #expect(Self.renders(ExplorerWheel(explorer: explorer, emotions: nil, domains: nil, lookups: WrapLookups())))
    }

    @Test("a family no lookup resolves draws in ink: its seals, its name, its rosace facets")
    func unknownFamily() throws {
        let lookups = try AchievementsLookups.fixture()
        #expect(lookups.family("awe") == nil && lookups.family("joy") != nil)
        let inks = AchievementInks(scheme: .light)
        #expect(inks.sealInk(lookups.family("awe")?.palette, polarity: .highlight) == inks.ink)
        #expect(inks.labelInk(lookups.family("awe")?.palette, family: "awe") == inks.ink)
        #expect(inks.hue(lookups.family("awe")?.palette) == nil)
        let emotions = Self.emotions(38) { $0 < 6 ? "awe" : EmotionFamilies.wheel[min($0 / 6, 6)] }
        #expect(Self.renders(EmotionRosace(emotions: emotions, lookups: lookups)))
        let explorer = try Self.explorer(firstFive: 5)
        #expect(Self.renders(ExplorerWheel(explorer: explorer, emotions: emotions, domains: nil, lookups: lookups)))
    }

    @Test("a rim month in an unknown family renders, in ink")
    func unknownRimMonth() throws {
        let months = (1...12).map { number in
            AchievementsPage.Month(month: number, state: number == 8 ? .kept : .quiet, family: number == 8 ? "awe" : nil,
                                   momentDays: 0, pebbleDays: 0, fullWeeks: 4, weeksFilled: 4, level: number == 8 ? 2 : 0,
                                   progress: 0, done: AchievementsPage.MonthDone(moments: false, pebbles: false, weeks: false))
        }
        let year = AchievementsPage.Year(year: 2026, isCurrent: false, daysTarget: 14, months: months, listed: [8],
                                         nextStep: nil)
        let lookups = try AchievementsLookups.fixture()
        #expect(YearDisc.tone(year.month(8), lookups: lookups, inks: AchievementInks(scheme: .light))
            == AchievementInks(scheme: .light).ink)
        #expect(Self.renders(YearDisc(year: year, lookups: lookups)))
    }
}
