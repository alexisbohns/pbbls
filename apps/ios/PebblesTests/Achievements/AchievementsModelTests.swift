import Foundation
import Testing
@testable import Pebbles

/// The page's state (#1306, spec §4): the kept copy at once, the grant, then the
/// read; a failed read keeps what is shown; seen marks fire and forget; a pull
/// is success only when a row came back.
@Suite("Achievements model")
@MainActor
struct AchievementsModelTests {
    static func page(daysKept: Int) throws -> AchievementsPage {
        var page = try AchievementsFixtures.payload("design-fixture").model
        page.daysKept = daysKept
        return page
    }

    static func model(_ fake: FakeAchievementsPageService) -> AchievementsModel {
        let model = AchievementsModel(zone: TimeZone(identifier: "Europe/Paris")!)
        model.service = fake
        return model
    }

    @Test("the kept page shows at once, the grant runs, then the read replaces it")
    func staleWhileRevalidate() async throws {
        let kept = try Self.page(daysKept: 248)
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(kept: kept, answers: [.success(fresh)])
        let model = Self.model(fake)
        var shownDuringGrant: AchievementsPage?
        await model.load {
            shownDuringGrant = model.page
            fake.record("grant")
        }
        #expect(shownDuringGrant == kept)
        #expect(model.page == fresh)
        #expect(fake.calls == ["kept", "grant", "read"])
        #expect(!model.failed && !model.isLoading)
    }

    @Test("a failed read keeps the page on screen and is not an error")
    func failedReadKeepsKept() async throws {
        let kept = try Self.page(daysKept: 248)
        let fake = FakeAchievementsPageService(kept: kept, answers: [.failure(FakeAchievementsPageService.Failure())])
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page == kept)
        #expect(!model.failed)
    }

    @Test("with nothing kept a failed read is the error state, and the next read clears it")
    func failedFirstVisit() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(answers: [.failure(FakeAchievementsPageService.Failure()), .success(fresh)])
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page == nil && model.failed)
        await model.load {}
        #expect(model.page == fresh && !model.failed)
    }

    @Test("a page already shown is not replaced by the kept copy on the next visit")
    func secondVisit() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(kept: try Self.page(daysKept: 200), answers: [.success(fresh)])
        let model = Self.model(fake)
        await model.load {}
        await model.load {}
        #expect(fake.calls == ["kept", "read", "read"])
        #expect(model.page == fresh)
    }

    @Test("a slow read landing after a newer one does not bring its older page back")
    func staleReadDropped() async throws {
        let ready = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(answers: [.success(ready), .success(try Self.pulledPage())])
        fake.answer(pull: .success(.pulled(karma: 30)))
        fake.hold(read: 0)
        let model = Self.model(fake)
        let load = Task { await model.load {} }
        for _ in 0..<1000 where !fake.isHolding { try await Task.sleep(nanoseconds: 1_000_000) }
        #expect(fake.isHolding)
        _ = await model.pull(.sixFirsts)
        #expect(model.page?.completion(.sixFirsts)?.pulledAt != nil)
        fake.release()
        await load.value
        #expect(model.page?.completion(.sixFirsts)?.pulledAt != nil, "the held read's ready page came back")
        #expect(model.page?.completion(.sixFirsts)?.isReady == false)
    }

    @Test("a cancelled read is not the error state")
    func cancelledRead() async {
        let fake = FakeAchievementsPageService(answers: [.failure(CancellationError())])
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page == nil && !model.failed)
        #expect(AchievementsModel.isCancellation(URLError(.cancelled)))
        #expect(!AchievementsModel.isCancellation(URLError(.timedOut)))
    }

    @Test("opening a sheet marks its card seen")
    func seen() async {
        let fake = FakeAchievementsPageService()
        let model = Self.model(fake)
        await model.opened(.emotions)?.value
        await model.opened(.year)?.value
        #expect(fake.seen == [.emotions, .year])
    }

    @Test("a ready ceremony pulls once, reports the karma it paid and reads the page again")
    func pull() async throws {
        let fresh = try Self.page(daysKept: 249)
        let fake = FakeAchievementsPageService(answers: [.success(fresh)])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 30))
        #expect(fake.calls == ["pull six_firsts", "read"])
        #expect(model.page == fresh && model.pulling == nil)
    }

    /// The design page with its Explorer completion pulled, as a re-read after a landed pull shows it.
    static func pulledPage() throws -> AchievementsPage {
        try AchievementsFixtures.json("""
        {"explorer": {"first_five": [], "six_firsts": [], "rings_done": ["six_firsts"],
                      "completion": {"completed_at": "2026-03-09T18:00:00Z", "offered_from": "2026-03-10T05:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-30T20:23:51Z"}}}
        """).model
    }

    @Test("no row, then a read showing the completion pulled, plays the ceremony without karma")
    func emptyAnswerConfirmedByRead() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.pulledPage())])
        fake.answer(pull: .success(.alreadyPulled))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulledWithoutKarma)
        #expect(fake.calls == ["pull six_firsts", "read"])
    }

    @Test("a lost answer, then a read showing the completion pulled, plays the ceremony without karma")
    func lostAnswerConfirmedByRead() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.pulledPage())])
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulledWithoutKarma)
    }

    @Test("a read showing another stack pulled confirms nothing")
    func otherStackPulled() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.pulledPage())])
        fake.answer(pull: .success(.alreadyPulled))
        let model = Self.model(fake)
        #expect(await model.pull(.emotions) == .alreadyPulled)
    }

    @Test("no row, not ready and a failure are never success")
    func pullFailures() async {
        let fake = FakeAchievementsPageService()
        let model = Self.model(fake)
        fake.answer(pull: .success(.alreadyPulled))
        #expect(await model.pull(.emotions) == .alreadyPulled)
        fake.answer(pull: .failure(CeremonyPullError.notReady))
        #expect(await model.pull(.emotions) == .notReady)
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        #expect(await model.pull(.emotions) == .failed)
    }

    @Test("without a service nothing runs")
    func noService() async {
        let model = AchievementsModel(zone: TimeZone(identifier: "Europe/Paris")!)
        await model.load {}
        #expect(model.page == nil && !model.failed)
        #expect(model.opened(.explorer) == nil)
    }
}
