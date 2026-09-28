import Foundation
import Testing
@testable import Pebbles

/// The snapshot store is crash insurance, so its failure modes matter more than
/// its happy path: a corrupt or stale file must never take the composer with it.
@Suite("ComposerSnapshotStore")
@MainActor
struct ComposerSnapshotStoreTests {

    private let owner = UUID()

    /// A store over a unique temp file, plus that file's URL.
    private func makeStore() -> (ComposerSnapshotStore, URL) {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("composer-snapshot-test-\(UUID().uuidString).json")
        return (ComposerSnapshotStore(fileURL: url), url)
    }

    private func payload(name: String) -> PebbleDraftPayload {
        var draft = PebbleDraft()
        draft.name = name
        return PebbleDraftPayload(from: draft, formSnap: nil, userId: nil)
    }

    @Test("Loading with no file on disk returns nil")
    func loadWithNoFile() {
        let (store, _) = makeStore()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("A saved snapshot round-trips")
    func saveThenLoad() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Half a thought"), ownerId: owner)
        #expect(store.load(ownerId: owner)?.name == "Half a thought")
    }

    @Test("Saving replaces the previous snapshot rather than accumulating")
    func saveReplaces() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "First"), ownerId: owner)
        store.save(payload(name: "Second"), ownerId: owner)
        #expect(store.load(ownerId: owner)?.name == "Second")
    }

    @Test("Saving an empty payload clears the file instead of storing nothing")
    func savingEmptyClears() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Something"), ownerId: owner)
        store.save(PebbleDraftPayload(), ownerId: owner)

        #expect(store.load(ownerId: owner) == nil)
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test("clear() removes the snapshot")
    func clearRemoves() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Publish me"), ownerId: owner)
        store.clear()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("clear() on an absent file is a no-op, not a crash")
    func clearIsIdempotent() {
        let (store, _) = makeStore()
        store.clear()
        store.clear()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("An unreadable snapshot is discarded rather than propagated")
    func corruptSnapshotIsDiscarded() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        try Data("{ this is not json".utf8).write(to: url)

        #expect(store.load(ownerId: owner) == nil)
        // And the bad file is gone, so it cannot fail again on every launch.
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test("A well-formed but empty snapshot is treated as nothing to restore")
    func emptySnapshotIsNotOffered() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        let blank = OwnedComposerSnapshot(ownerId: owner, payload: PebbleDraftPayload())
        try JSONEncoder().encode(blank).write(to: url)
        #expect(store.load(ownerId: owner) == nil, "restoring a blank composer is not worth a prompt")
    }

    // MARK: - Ownership (shared and handed-over devices)

    @Test("Another account is never given the snapshot, and the file is deleted")
    func foreignSnapshotIsDiscarded() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Something only I should read"), ownerId: owner)

        #expect(store.load(ownerId: UUID()) == nil)
        // Withholding is not enough: it is still the previous user's writing.
        #expect(!FileManager.default.fileExists(atPath: url.path))
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("The file records who wrote it")
    func ownerIsWrittenToDisk() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Mine"), ownerId: owner)

        let onDisk = try JSONDecoder().decode(OwnedComposerSnapshot.self, from: Data(contentsOf: url))
        #expect(onDisk.ownerId == owner)
        #expect(onDisk.payload.name == "Mine")
    }

    @Test("A bare payload from a build before ownership is discarded, not offered")
    func legacyUnownedSnapshotIsDiscarded() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        // Exactly what the previous build wrote: the payload with no owner.
        try JSONEncoder().encode(payload(name: "Whose is this?")).write(to: url)

        #expect(store.load(ownerId: owner) == nil, "an unowned snapshot cannot prove it is this user's")
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }
}

@Suite("ComposerAutosave")
@MainActor
struct ComposerAutosaveTests {

    private let owner = UUID()

    private func makeStore() -> (ComposerSnapshotStore, URL) {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("composer-autosave-test-\(UUID().uuidString).json")
        return (ComposerSnapshotStore(fileURL: url), url)
    }

    private func payload(name: String) -> PebbleDraftPayload {
        var draft = PebbleDraft()
        draft.name = name
        return PebbleDraftPayload(from: draft, formSnap: nil, userId: nil)
    }

    @Test("A scheduled write does not hit disk before its debounce elapses")
    func debounceDelaysWrite() async throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(400), ownerId: { owner })

        autosave.schedule(payload(name: "Typing"))
        #expect(store.load(ownerId: owner) == nil, "the write should still be pending")

        try await Task.sleep(for: .milliseconds(700))
        #expect(store.load(ownerId: owner)?.name == "Typing")
    }

    @Test("Rapid keystrokes collapse into a single write of the final value")
    func rapidSchedulesCollapse() async throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(200), ownerId: { owner })

        for name in ["A", "Ar", "Arg", "Argu"] {
            autosave.schedule(payload(name: name))
        }
        try await Task.sleep(for: .milliseconds(500))

        #expect(store.load(ownerId: owner)?.name == "Argu")
    }

    @Test("flush() writes the pending snapshot immediately")
    func flushWritesNow() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .seconds(30), ownerId: { owner })

        autosave.schedule(payload(name: "Backgrounded mid-sentence"))
        autosave.flush()

        // This is the scene-phase path: without it, a process kill inside the
        // debounce window would lose everything typed.
        #expect(store.load(ownerId: owner)?.name == "Backgrounded mid-sentence")
    }

    @Test("flush() with nothing pending does not write")
    func flushWithoutPendingIsNoOp() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(100), ownerId: { owner })

        autosave.flush()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("clear() cancels a pending write so it cannot resurrect the snapshot")
    func clearCancelsPendingWrite() async throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(200), ownerId: { owner })

        autosave.schedule(payload(name: "About to publish"))
        autosave.clear()
        try await Task.sleep(for: .milliseconds(500))

        // Regression guard: if the debounced task still fired after clear(), a
        // published pebble would be re-offered as an unsaved draft.
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("A write that lands after sign-out is dropped, not stored unowned")
    func writeWithNoSessionIsDropped() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        var signedIn: UUID? = owner
        let autosave = ComposerAutosave(store: store, debounce: .seconds(30), ownerId: { signedIn })

        autosave.schedule(payload(name: "Typed just before signing out"))
        signedIn = nil
        autosave.flush()

        // Sign-out has already deleted the file; the pending write must not
        // bring it back.
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }
}
