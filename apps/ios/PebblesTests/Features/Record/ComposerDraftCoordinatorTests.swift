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

/// A shared or handed-over device: the next account signing in must never be
/// prompted to restore the previous one's half-written pebble.
@Suite("ComposerDraftCoordinator — snapshot ownership")
@MainActor
struct ComposerDraftOwnershipTests {

    private func makeStore() -> (ComposerSnapshotStore, URL) {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("composer-ownership-test-\(UUID().uuidString).json")
        return (ComposerSnapshotStore(fileURL: url), url)
    }

    private func makeCoordinator(store: ComposerSnapshotStore, signedIn: UUID?) -> ComposerDraftCoordinator {
        let supabase = SupabaseService()
        return ComposerDraftCoordinator(
            client: supabase.client,
            drafts: PebbleDraftsService(client: supabase.client),
            snapshots: store,
            ownerId: { signedIn }
        )
    }

    private func payload(name: String) -> PebbleDraftPayload {
        var payload = PebbleDraftPayload()
        payload.name = name
        return payload
    }

    @Test("the user who wrote the snapshot is offered it")
    func ownerIsOfferedRestore() {
        let owner = UUID()
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        store.save(payload(name: "half-typed"), ownerId: owner)
        let coordinator = makeCoordinator(store: store, signedIn: owner)

        let decision = coordinator.hydrate(resuming: nil, refsLoaded: true)

        #expect(decision == .offerRestore(payload(name: "half-typed")))
        #expect(coordinator.isRestorePromptPresented)
    }

    @Test("another account starts fresh and the snapshot is gone")
    func otherAccountStartsFresh() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        store.save(payload(name: "someone else's evening"), ownerId: UUID())
        let coordinator = makeCoordinator(store: store, signedIn: UUID())

        let decision = coordinator.hydrate(resuming: nil, refsLoaded: true)

        #expect(decision == .fresh)
        #expect(!coordinator.isRestorePromptPresented)
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }
}
