import Foundation
import Testing
@testable import Pebbles

@Suite("ComposerDraftCoordinator — hydration decision")
struct ComposerDraftHydrationTests {

    private func record(name: String) -> PebbleDraftRecord {
        var payload = PebbleDraftPayload()
        payload.name = name
        return PebbleDraftRecord(
            id: UUID(), payload: payload, updatedAt: Date(timeIntervalSince1970: 0)
        )
    }

    /// #647: hydrating before reference data lands sanitizes the draft against
    /// empty sets and silently drops every soul and collection.
    @Test("nothing is decided while reference data is still loading")
    func waitsForReferenceData() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: record(name: "Ferry"), refsLoaded: false, snapshot: nil
        )
        #expect(decision == nil)
    }

    @Test("a resumed server draft wins over a local snapshot")
    func serverDraftWins() {
        var snapshot = PebbleDraftPayload()
        snapshot.name = "half-typed"
        let resuming = record(name: "Ferry")

        let decision = ComposerDraftCoordinator.hydration(
            resuming: resuming, refsLoaded: true, snapshot: snapshot
        )

        #expect(decision == .resume(resuming.payload))
    }

    @Test("a non-empty snapshot with no server draft offers a restore")
    func offersRestore() {
        var snapshot = PebbleDraftPayload()
        snapshot.name = "half-typed"

        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: snapshot
        )

        #expect(decision == .offerRestore(snapshot))
    }

    @Test("an empty snapshot is not worth prompting about")
    func emptySnapshotIsFresh() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: PebbleDraftPayload()
        )
        #expect(decision == .fresh)
    }

    @Test("no draft and no snapshot starts fresh")
    func nothingToRestore() {
        let decision = ComposerDraftCoordinator.hydration(
            resuming: nil, refsLoaded: true, snapshot: nil
        )
        #expect(decision == .fresh)
    }
}
