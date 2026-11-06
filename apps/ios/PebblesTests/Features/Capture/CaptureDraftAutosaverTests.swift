import Foundation
import Testing
@testable import Pebbles

@Suite("CaptureDraftAutosaver")
@MainActor
struct CaptureDraftAutosaverTests {
    private func store() -> (CaptureDraftStore, URL) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("capture-autosave-\(UUID().uuidString)")
        return (CaptureDraftStore(directory: dir), dir)
    }

    private func saved(name: String) -> CaptureDraftSnapshot.Saved {
        var draft = CaptureDraft()
        draft.name = name
        draft.happenedAt = Date(timeIntervalSince1970: 1_700_000_000)
        return .init(draft: draft, hasEditedDate: false, snap: nil)
    }

    @Test("schedule then flush writes the last page once")
    func scheduleThenFlush() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        let autosaver = CaptureDraftAutosaver(store: store, debounce: .seconds(60), ownerId: { owner })
        autosaver.enabled = true
        autosaver.schedule(saved(name: "Lake"))
        autosaver.schedule(saved(name: "Lake at dawn"))
        #expect(store.load(ownerId: owner) == nil)
        autosaver.flush()
        #expect(store.load(ownerId: owner) == saved(name: "Lake at dawn"))
        // Nothing pending: a second flush does not touch the file.
        store.clear(ownerId: owner)
        autosaver.flush()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("a disabled autosaver never writes")
    func disabled() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        let autosaver = CaptureDraftAutosaver(store: store, debounce: .seconds(60), ownerId: { owner })
        autosaver.schedule(saved(name: "Lake"))
        autosaver.flush()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("clear deletes the file and stops later writes")
    func clear() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        store.save(saved(name: "Lake"), ownerId: owner)
        let autosaver = CaptureDraftAutosaver(store: store, debounce: .seconds(60), ownerId: { owner })
        autosaver.enabled = true
        autosaver.clear()
        #expect(store.load(ownerId: owner) == nil)
        autosaver.schedule(saved(name: "Lake"))
        autosaver.flush()
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("savedSnap keeps only a snap that lives in Storage")
    func savedSnap() {
        let userId = UUID(), snapId = UUID()
        #expect(CaptureDraftAutosaver.savedSnap(from: .existing(id: snapId, storagePath: "u/s"), userId: userId)
            == .init(id: snapId, storagePath: "u/s"))
        let uploaded = AttachedSnap(id: snapId, localThumb: Data(), state: .uploaded)
        #expect(CaptureDraftAutosaver.savedSnap(from: .pending(uploaded), userId: userId)
            == .init(id: snapId, storagePath: uploaded.storagePrefix(userId: userId)))
        let uploading = AttachedSnap(id: snapId, localThumb: Data(), state: .uploading)
        #expect(CaptureDraftAutosaver.savedSnap(from: .pending(uploading), userId: userId) == nil)
        #expect(CaptureDraftAutosaver.savedSnap(from: nil, userId: userId) == nil)
    }
}
