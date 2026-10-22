import Foundation
import Testing
@testable import Pebbles

/// #1146 §State model: the page's model loads `v_moments_full` and reloads in
/// place; every save, delete and completion calls the opener once and bumps
/// `MomentsRevision` once from the opener's tab; a delete that closes the page
/// sets `leaving` before it returns, so that close never flies (E4).
@Suite("HeldCardModel")
@MainActor
struct HeldCardModelTests {
    struct Failure: Error {}
    final class Calls: @unchecked Sendable { var count = 0 }

    private func detail(_ data: Data = MomentFixtures.completedMoment) throws -> MomentDetail {
        try JSONDecoder().decode(MomentDetail.self, from: data)
    }

    private func model(_ calls: Calls = Calls()) -> HeldCardModel {
        HeldCardModel(momentId: MomentFixtures.uuid("0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b"), origin: .people,
                      onChanged: { calls.count += 1 })
    }

    @Test("the first load publishes the detail")
    func firstLoad() async throws {
        let model = model()
        #expect(model.phase == .loading)
        let fresh = try detail()
        await model.load { fresh }
        #expect(model.phase == .loaded)
        #expect(model.detail?.id == fresh.id)
    }

    @Test("a failed first load fails; a failed reload keeps the page")
    func failures() async throws {
        let first = model()
        await first.load { throw Failure() }
        #expect(first.phase == .failed)
        let shown = model()
        let fresh = try detail()
        await shown.load { fresh }
        await shown.load { throw Failure() }
        #expect(shown.phase == .loaded && shown.detail != nil)
    }

    @Test("only the latest load applies when two overlap")
    func latestLoadWins() async throws {
        let model = model()
        let stale = try detail()
        let fresh = try detail(MomentFixtures.Captured.detailPhoto)
        let gate = AsyncStream<Void>.makeStream()
        let started = Calls()
        async let slow: Void = model.load {
            started.count = 1
            for await _ in gate.stream { break }
            return stale
        }
        // The model runs on the main actor: let the slow load begin before the fast one.
        while started.count == 0 { await Task.yield() }
        await model.load { fresh }
        gate.continuation.yield()
        gate.continuation.finish()
        await slow
        #expect(model.detail?.id == fresh.id)
    }

    @Test("a change calls the opener once and bumps the revision once, from the opener's tab")
    func changed() {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        model.changed(revision)
        #expect(calls.count == 1)
        #expect(revision.value == 1 && revision.lastOrigin == .people)
    }

    @Test("deleting the moment closes the page, leaving first")
    func deleteMoment() async throws {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.moment, target: target, revision: revision) { _, _ in }
        #expect(outcome == .closes)
        #expect(model.leaving)
        #expect(calls.count == 1 && revision.value == 1)
    }

    @Test("deleting only the pebble reloads the page in place")
    func deletePebble() async throws {
        let model = model()
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.pebbleKeepMoment, target: target, revision: revision) { _, _ in }
        #expect(outcome == .reloads)
        #expect(!model.leaving)
        #expect(revision.value == 1)
    }

    @Test("a failed delete changes nothing and says so")
    func deleteFails() async throws {
        let calls = Calls()
        let model = model(calls)
        let revision = MomentsRevision()
        let target = MomentDeleteTarget(detail: try detail())
        let outcome = await model.delete(.moment, target: target, revision: revision) { _, _ in throw Failure() }
        #expect(outcome == .failed)
        #expect(!model.leaving && !model.isDeleting)
        #expect(calls.count == 0 && revision.value == 0)
    }

    // MARK: - Completion (D6)

    @Test("a reload finding a pebble on a bare moment publishes the pebble and the completion together")
    func completion() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        let completed = try detail()
        await model.load { bare }
        #expect(model.completion == nil)
        await model.load { completed }
        #expect(model.detail?.pebble != nil)
        #expect(model.completion == HeldCardModel.Completion(pebbleId: try #require(completed.pebble?.id)))
    }

    @Test("the completion holds through a later reload until it is shown")
    func holds() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        let completed = try detail()
        await model.load { bare }
        await model.load { completed }
        await model.load { completed }
        #expect(model.completion != nil)
        model.printEnded()
        #expect(model.completion == nil && model.printedOver && model.announcing)
        await model.load { completed }
        #expect(model.completion == nil)
        model.announced()
        #expect(!model.announcing)
    }

    @Test("the completion clears when the pebble disappears")
    func clears() async throws {
        let model = model()
        let bare = try detail(MomentFixtures.photoOnlyMoment)
        await model.load { bare }
        await model.load { try detail() }
        await model.load { bare }
        #expect(model.completion == nil)
    }

    @Test("a completed moment opened as such never prints")
    func noPrintOnOpen() async throws {
        let model = model()
        await model.load { try detail() }
        #expect(model.completion == nil)
    }
}
