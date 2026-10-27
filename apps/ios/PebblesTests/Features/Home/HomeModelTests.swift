import Foundation
import Testing
@testable import Pebbles

@Suite("Home model")
@MainActor
struct HomeModelTests {
    @Test func loadsTheName() async {
        let model = HomeModel()
        model.service = FakeHomeService(answer: { "Alexis" })
        await model.load()
        #expect(model.name == "Alexis")
        #expect(model.hasLoadedOnce)
    }

    @Test func keepsAnEmptyNameWhenTheLoadFails() async {
        let model = HomeModel()
        model.service = FakeHomeService(answer: { throw FakeHomeService.Failure() })
        await model.load()
        #expect(model.name == "")
        #expect(model.hasLoadedOnce)
    }

    @Test func wrapsLoadAndKeepTheLastValueOnFailure() async throws {
        let fake = FakeHomeService(answer: { "A" })
        let wraps = try HomePayloads.wraps()
        fake.answer(wraps: .success(wraps))
        let model = HomeModel(); model.service = fake
        await model.load()
        #expect(model.wraps == wraps && model.wrapsError == nil)
        fake.answer(wraps: .failure(FakeHomeService.Failure()))
        await model.loadWraps()
        #expect(model.wraps == wraps && model.wrapsError != nil)
    }

    @Test func aRevealFlipsTheMonthInPlace() async throws {
        let fake = FakeHomeService(answer: { "A" })
        let wraps = try HomePayloads.wraps()
        fake.answer(wraps: .success(wraps))
        let sept = CalendarMonth(year: 2026, month: 9)
        let at = ISO8601Flexible.parse("2026-10-26T22:24:21Z")
        fake.answer(reveal: sept, .success(HomeReveal(month: sept, revealedAt: at)))
        let model = HomeModel(); model.service = fake
        await model.load()
        let reveal = try await model.reveal(sept, zone: TimeZone(identifier: "Europe/Paris")!)
        #expect(reveal.revealedAt == at)
        #expect(model.wraps?.months.first { $0.month == sept }?.isFolded == false)
    }

    // MARK: - The guides

    private static let stones = Guide(slug: "stones", sortOrder: 1, illustration: .stones,
                                      titleEn: "Stones", summaryEn: "Nine shapes.", bodyMdEn: "Nine shapes.")

    @Test func guidesLoadAndKeepTheLastListOnFailure() async {
        let fake = FakeHomeService(answer: { "A" })
        fake.answer(guides: .success([Self.stones]))
        let model = HomeModel(); model.service = fake
        #expect(model.guides == nil)
        await model.load()
        #expect(model.guides == [Self.stones])
        #expect(fake.guidesCalls == 1)
        fake.answer(guides: .failure(FakeHomeService.Failure()))
        await model.loadGuides()
        #expect(model.guides == [Self.stones])
    }

    @Test func aFailedFirstReadLeavesTheGuidesUnloaded() async {
        let fake = FakeHomeService(answer: { "A" })
        fake.answer(guides: .failure(FakeHomeService.Failure()))
        let model = HomeModel(); model.service = fake
        await model.load()
        #expect(model.guides == nil)
        #expect(fake.guidesCalls == 1)
    }

    @Test func aFailedFirstReadIsFlaggedUntilOneSucceeds() async {
        let fake = FakeHomeService(answer: { "A" })
        fake.answer(guides: .failure(FakeHomeService.Failure()))
        let model = HomeModel(); model.service = fake
        #expect(model.guidesFailed == false)
        await model.load()
        #expect(model.guides == nil && model.guidesFailed)
        fake.answer(guides: .success([Self.stones]))
        await model.loadGuides()
        #expect(model.guides == [Self.stones] && !model.guidesFailed)
    }

    // MARK: - The claim

    private static func claimable() -> (FakeAchievementsProvider, HomeModel) {
        let fake = FakeAchievementsProvider()
        fake.catalog = HomeChecklistTests.catalog(HomeChecklistTests.allSlugs)
        fake.unlocks = unlocks(Set(HomeChecklist.Task.allCases.map(\.slug)), in: fake.catalog)
        let model = HomeModel()
        model.achievements = fake
        return (fake, model)
    }

    private static func unlocks(_ slugs: Set<String>, in catalog: [AchievementRecord]) -> [AchievementUnlockRecord] {
        catalog.filter { slugs.contains($0.slug) }
            .map { AchievementUnlockRecord(achievementId: $0.id, unlockedAt: Date(timeIntervalSince1970: 1_793_000_000)) }
    }

    @Test func anEmptyClaimIsNotAGrant() async {
        let (fake, model) = Self.claimable()
        await model.loadChecklist()
        #expect(model.checklist?.state == .claimable)
        let catalogCalls = fake.catalogCalls
        fake.claimAnswer = .success([])
        await model.claim()
        #expect(model.claimError != nil)
        #expect(model.checklist?.state == .claimable)
        #expect(fake.catalogCalls == catalogCalls)
    }

    @Test func aGrantReloadsToDone() async {
        let (fake, model) = Self.claimable()
        await model.loadChecklist()
        fake.claimAnswer = .success([AchievementCheckResult(slug: HomeChecklist.blueSlug, karmaGranted: 50)])
        fake.onClaim = { fake.unlocks = Self.unlocks(Set(HomeChecklistTests.allSlugs), in: fake.catalog) }
        await model.claim()
        #expect(model.checklist?.state == .done)
        #expect(model.claimError == nil)
    }
}
