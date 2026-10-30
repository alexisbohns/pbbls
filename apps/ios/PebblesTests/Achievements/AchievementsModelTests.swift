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
        let ready = try Self.readyPage()
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

    @Test("the zone is read when each load starts: a zone changed between visits asks and keeps in the new one")
    func zoneReadPerLoad() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.page(daysKept: 249))])
        var current = try #require(TimeZone(identifier: "Europe/Paris"))
        let model = AchievementsModel(zone: { current })
        model.service = fake
        await model.load {}
        current = try #require(TimeZone(identifier: "Asia/Tokyo"))
        await model.load {}
        #expect(fake.zones == ["Europe/Paris", "Europe/Paris", "Asia/Tokyo"])
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

    @Test("the shelf's read: a kept copy under fifteen minutes old is enough; an older one, or none, is read")
    func loadIfStale() async throws {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let kept = try Self.page(daysKept: 248)
        let fresh = try Self.page(daysKept: 249)

        let recent = FakeAchievementsPageService(kept: kept, answers: [.success(fresh)])
        recent.keptAtDate = now.addingTimeInterval(-5 * 60)
        let shelf = Self.model(recent)
        await shelf.loadIfStale(maxAge: 15 * 60, now: { now })
        #expect(recent.calls == ["kept"] && shelf.page == kept)

        let old = FakeAchievementsPageService(kept: kept, answers: [.success(fresh)])
        old.keptAtDate = now.addingTimeInterval(-20 * 60)
        let stale = Self.model(old)
        await stale.loadIfStale(maxAge: 15 * 60, now: { now })
        #expect(old.calls == ["kept", "read"] && stale.page == fresh)

        let none = FakeAchievementsPageService(answers: [.success(fresh)])
        let first = Self.model(none)
        await first.loadIfStale(maxAge: 15 * 60, now: { now })
        #expect(none.calls == ["kept", "read"] && first.page == fresh)
    }

    @Test("opening a sheet marks its card seen")
    func seen() async {
        let fake = FakeAchievementsPageService()
        let model = Self.model(fake)
        await model.opened(.emotions)?.value
        await model.opened(.year)?.value
        #expect(fake.seen == [.emotions, .year])
    }

    /// The six firsts whole and offered, not yet pulled (the design's were pulled long ago).
    static func readyPage() throws -> AchievementsPage {
        try AchievementsFixtures.json("""
        {"explorer": {"first_five": [], "six_firsts": [], "rings_done": ["six_firsts"],
                      "completion": {"completed_at": "2026-03-08T18:00:00Z", "offered_from": "2026-03-09T05:00:00Z",
                                     "ready": true}}}
        """).model
    }

    /// The Explorer completion pulled, as a read after a landed pull shows it.
    static func pulledPage() throws -> AchievementsPage {
        try AchievementsFixtures.json("""
        {"explorer": {"first_five": [], "six_firsts": [], "rings_done": ["six_firsts"],
                      "completion": {"completed_at": "2026-03-09T18:00:00Z", "offered_from": "2026-03-10T05:00:00Z",
                                     "ready": false, "pulled_at": "2026-10-30T20:23:51Z"}}}
        """).model
    }

    @Test("a ready ceremony is read first, pulled once, reports the karma it paid and is read again")
    func pull() async throws {
        let ready = try Self.readyPage()
        let fake = FakeAchievementsPageService(answers: [.success(ready)])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 30))
        #expect(fake.calls == ["read", "pull six_firsts", "read"])
        #expect(model.pulling == nil)
        // The fake's read still says ready; the model marks what it pulled itself.
        #expect(model.page?.completion(.sixFirsts)?.isReady == false && model.page?.completion(.sixFirsts)?.pulledAt != nil)
        #expect(model.page?.explorer?.ringsDone == ready.explorer?.ringsDone)
    }

    @Test("a ceremony plays once: a second tap after a failed re-read is already pulled, never played again")
    func playsOnce() async throws {
        let fake = FakeAchievementsPageService(answers: [
            .success(try Self.readyPage()), .failure(FakeAchievementsPageService.Failure()), .success(try Self.pulledPage())
        ])
        fake.answer(pull: .success(.pulled(karma: 5)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 5))
        #expect(model.page?.completion(.sixFirsts)?.isReady == false, "the mark goes though the re-read failed")
        #expect(model.page?.completion(.sixFirsts)?.pulledAt != nil)
        fake.answer(pull: .success(.alreadyPulled))
        #expect(await model.pull(.sixFirsts) == .alreadyPulled)
        #expect(fake.calls.filter { $0.hasPrefix("pull") }.count == 1, "the second tap asks nothing")
    }

    @Test("a row paying no karma is still a pull")
    func pullWithoutPay() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage())])
        fake.answer(pull: .success(.pulled(karma: 0)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 0))
    }

    @Test("a page already read this session decides the pull without reading again first")
    func pullAfterLoad() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage())])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        await model.load {}
        #expect(await model.pull(.sixFirsts) == .pulled(karma: 30))
        #expect(fake.calls == ["kept", "read", "pull six_firsts", "read"])
    }

    @Test("no row, then a read showing the completion pulled, plays the ceremony without karma")
    func emptyAnswerConfirmedByRead() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage()), .success(try Self.pulledPage())])
        fake.answer(pull: .success(.alreadyPulled))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulledWithoutKarma)
        #expect(fake.calls == ["read", "pull six_firsts", "read"])
    }

    @Test("a lost answer, then a read showing the completion pulled, plays the ceremony without karma")
    func lostAnswerConfirmedByRead() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage()), .success(try Self.pulledPage())])
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .pulledWithoutKarma)
    }

    @Test("the kept copy shows ready but the server has it pulled: no call, never success")
    func keptReadyServerPulled() async throws {
        let fake = FakeAchievementsPageService(
            kept: try Self.readyPage(),
            answers: [.failure(FakeAchievementsPageService.Failure()), .success(try Self.pulledPage())])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        await model.load {}
        #expect(model.page?.completion(.sixFirsts)?.isReady == true, "the kept copy is on screen")
        #expect(await model.pull(.sixFirsts) == .alreadyPulled)
        #expect(fake.calls == ["kept", "read", "read"])
    }

    @Test("a failure over a completion already pulled before this pull is never played")
    func failureOverOldPull() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.pulledPage())])
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        let model = Self.model(fake)
        await model.load {}
        #expect(await model.pull(.sixFirsts) == .alreadyPulled)
        #expect(!fake.calls.contains("pull six_firsts"))
    }

    @Test("a stack with no completion, or one not offered yet, is not pulled")
    func notOffered() async throws {
        let notYet = try AchievementsFixtures.json("""
        {"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-30T20:00:00Z", "offered_from": "2026-10-31T05:00:00Z",
                                     "ready": false}}}
        """).model
        let fake = FakeAchievementsPageService(answers: [.success(notYet)])
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        #expect(await model.pull(.emotions) == .notReady)
        #expect(await model.pull(.domains) == .notReady)
        #expect(!fake.calls.contains { $0.hasPrefix("pull") })
    }

    @Test("no row, not ready and a failure are never success")
    func pullFailures() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage())])
        let model = Self.model(fake)
        fake.answer(pull: .success(.alreadyPulled))
        #expect(await model.pull(.sixFirsts) == .alreadyPulled)
        fake.answer(pull: .failure(CeremonyPullError.notReady))
        #expect(await model.pull(.sixFirsts) == .notReady)
        fake.answer(pull: .failure(FakeAchievementsPageService.Failure()))
        #expect(await model.pull(.sixFirsts) == .failed)
    }

    @Test("with no page read, the pull is not asked")
    func pullWithoutRead() async {
        let fake = FakeAchievementsPageService()
        fake.answer(pull: .success(.pulled(karma: 30)))
        let model = Self.model(fake)
        #expect(await model.pull(.sixFirsts) == .failed)
        #expect(fake.calls == ["read"])
    }

    @Test("a pull asked while another runs is busy and does nothing")
    func busy() async throws {
        let fake = FakeAchievementsPageService(answers: [.success(try Self.readyPage())])
        fake.answer(pull: .success(.pulled(karma: 30)))
        fake.hold(read: 0)
        let model = Self.model(fake)
        let first = Task { await model.pull(.sixFirsts) }
        for _ in 0..<1000 where !fake.isHolding { try await Task.sleep(nanoseconds: 1_000_000) }
        #expect(model.pulling == .sixFirsts)
        #expect(await model.pull(.emotions) == .busy)
        fake.release()
        #expect(await first.value == .pulled(karma: 30))
    }

    @Test("without a service nothing runs")
    func noService() async {
        let model = AchievementsModel(zone: TimeZone(identifier: "Europe/Paris")!)
        await model.load {}
        #expect(model.page == nil && !model.failed)
        #expect(model.opened(.explorer) == nil)
        #expect(await model.pull(.sixFirsts) == .failed)
    }
}
