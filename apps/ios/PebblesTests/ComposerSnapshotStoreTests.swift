import Foundation
import Testing
@testable import Pebbles

/// The snapshot store is crash insurance, so its failure modes matter more than
/// its happy path: a corrupt or stale file must never take the composer with it.
@Suite("ComposerSnapshotStore")
@MainActor
struct ComposerSnapshotStoreTests {

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
        #expect(store.load() == nil)
    }

    @Test("A saved snapshot round-trips")
    func saveThenLoad() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Half a thought"))
        #expect(store.load()?.name == "Half a thought")
    }

    @Test("Saving replaces the previous snapshot rather than accumulating")
    func saveReplaces() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "First"))
        store.save(payload(name: "Second"))
        #expect(store.load()?.name == "Second")
    }

    @Test("Saving an empty payload clears the file instead of storing nothing")
    func savingEmptyClears() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Something"))
        store.save(PebbleDraftPayload())

        #expect(store.load() == nil)
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test("clear() removes the snapshot")
    func clearRemoves() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        store.save(payload(name: "Publish me"))
        store.clear()
        #expect(store.load() == nil)
    }

    @Test("clear() on an absent file is a no-op, not a crash")
    func clearIsIdempotent() {
        let (store, _) = makeStore()
        store.clear()
        store.clear()
        #expect(store.load() == nil)
    }

    @Test("An unreadable snapshot is discarded rather than propagated")
    func corruptSnapshotIsDiscarded() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        try Data("{ this is not json".utf8).write(to: url)

        #expect(store.load() == nil)
        // And the bad file is gone, so it cannot fail again on every launch.
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test("A well-formed but empty snapshot is treated as nothing to restore")
    func emptySnapshotIsNotOffered() throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }

        try JSONEncoder().encode(PebbleDraftPayload()).write(to: url)
        #expect(store.load() == nil, "restoring a blank composer is not worth a prompt")
    }
}

@Suite("ComposerAutosave")
@MainActor
struct ComposerAutosaveTests {

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
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(400))

        autosave.schedule(payload(name: "Typing"))
        #expect(store.load() == nil, "the write should still be pending")

        try await Task.sleep(for: .milliseconds(700))
        #expect(store.load()?.name == "Typing")
    }

    @Test("Rapid keystrokes collapse into a single write of the final value")
    func rapidSchedulesCollapse() async throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(200))

        for name in ["A", "Ar", "Arg", "Argu"] {
            autosave.schedule(payload(name: name))
        }
        try await Task.sleep(for: .milliseconds(500))

        #expect(store.load()?.name == "Argu")
    }

    @Test("flush() writes the pending snapshot immediately")
    func flushWritesNow() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .seconds(30))

        autosave.schedule(payload(name: "Backgrounded mid-sentence"))
        autosave.flush()

        // This is the scene-phase path: without it, a process kill inside the
        // debounce window would lose everything typed.
        #expect(store.load()?.name == "Backgrounded mid-sentence")
    }

    @Test("flush() with nothing pending does not write")
    func flushWithoutPendingIsNoOp() {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(100))

        autosave.flush()
        #expect(store.load() == nil)
    }

    @Test("clear() cancels a pending write so it cannot resurrect the snapshot")
    func clearCancelsPendingWrite() async throws {
        let (store, url) = makeStore()
        defer { try? FileManager.default.removeItem(at: url) }
        let autosave = ComposerAutosave(store: store, debounce: .milliseconds(200))

        autosave.schedule(payload(name: "About to publish"))
        autosave.clear()
        try await Task.sleep(for: .milliseconds(500))

        // Regression guard: if the debounced task still fired after clear(), a
        // published pebble would be re-offered as an unsaved draft.
        #expect(store.load() == nil)
    }
}
