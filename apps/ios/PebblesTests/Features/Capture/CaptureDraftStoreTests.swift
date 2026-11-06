import Foundation
import Testing
@testable import Pebbles

@Suite("CaptureDraftStore")
@MainActor
struct CaptureDraftStoreTests {
    private func store() -> (CaptureDraftStore, URL) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("capture-draft-\(UUID().uuidString)")
        return (CaptureDraftStore(directory: dir), dir)
    }

    private func draft() -> CaptureDraft {
        var draft = CaptureDraft()
        draft.name = "Lake"
        draft.description = "Cold"
        draft.happenedAt = Date(timeIntervalSince1970: 1_700_000_000)
        return draft
    }

    @Test("round-trips per owner with the snap reference and the edited flag")
    func roundTrip() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID(), other = UUID(), snap = UUID()
        let saved = CaptureDraftSnapshot.Saved(draft: draft(), hasEditedDate: true,
                                               snap: .init(id: snap, storagePath: "u/s"))
        store.save(saved, ownerId: owner)
        #expect(store.load(ownerId: owner) == saved)
        #expect(store.load(ownerId: other) == nil)
    }

    @Test("an empty draft is not kept")
    func empty() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        store.save(.init(draft: CaptureDraft(), hasEditedDate: false, snap: nil), ownerId: owner)
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("whole-second timestamps survive the file")
    func seconds() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        var d = draft()
        d.happenedAt = Date(timeIntervalSince1970: 1_700_000_000.7)
        store.save(.init(draft: d, hasEditedDate: false, snap: nil), ownerId: owner)
        #expect(store.load(ownerId: owner)?.draft.happenedAt == Date(timeIntervalSince1970: 1_700_000_000))
    }
}
