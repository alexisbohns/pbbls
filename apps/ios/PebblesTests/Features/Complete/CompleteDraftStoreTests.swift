import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteDraftStore")
@MainActor
struct CompleteDraftStoreTests {

    private func store() -> (CompleteDraftStore, URL) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("complete-drafts-\(UUID().uuidString)")
        return (CompleteDraftStore(directory: dir), dir)
    }

    private func answered() -> CompleteDraft {
        var draft = CompleteDraft()
        draft.valence = .highlightSmall
        draft.emotionId = UUID()
        draft.soulIds = [UUID()]
        draft.visibility = .private
        return draft
    }

    @Test("round-trips per moment, owner-checked")
    func roundTrip() {
        let (store, _) = store()
        let owner = UUID(), moment = UUID(), other = UUID()
        let draft = answered()
        store.save(draft, momentId: moment, ownerId: owner)
        #expect(store.load(momentId: moment, ownerId: owner) == draft)
        #expect(store.load(momentId: other, ownerId: owner) == nil)
    }

    @Test("another account's file is deleted on read")
    func otherOwner() {
        let (store, _) = store()
        let moment = UUID()
        store.save(answered(), momentId: moment, ownerId: UUID())
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
    }

    @Test("the file body uses complete_pebble's keys (A3)")
    func wireKeys() throws {
        let (store, dir) = store()
        let moment = UUID()
        store.save(answered(), momentId: moment, ownerId: UUID())
        let url = dir.appendingPathComponent("\(moment.uuidString.lowercased()).json")
        let json = try #require(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        #expect(Set(json.keys) == ["owner_id", "moment_id", "intensity", "positiveness", "emotion_id", "soul_ids", "visibility"])
    }

    @Test("an empty draft clears; clear(momentId:) and clearAll scope correctly")
    func clearing() {
        let (store, _) = store()
        let owner = UUID(), a = UUID(), b = UUID()
        store.save(answered(), momentId: a, ownerId: owner)
        store.save(answered(), momentId: b, ownerId: owner)
        store.save(CompleteDraft(), momentId: a, ownerId: owner)
        #expect(store.load(momentId: a, ownerId: owner) == nil)
        store.clear(momentId: b)
        #expect(store.load(momentId: b, ownerId: owner) == nil)
        store.save(answered(), momentId: a, ownerId: owner)
        store.clearAll()
        #expect(store.load(momentId: a, ownerId: owner) == nil)
    }

    @Test("an unreadable file is discarded, not thrown")
    func unreadable() throws {
        let (store, dir) = store()
        let moment = UUID()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try Data("{nope".utf8).write(to: dir.appendingPathComponent("\(moment.uuidString.lowercased()).json"))
        #expect(store.load(momentId: moment, ownerId: UUID()) == nil)
    }

    @Test("autosave debounces, flush writes at once, clear drops the pending write")
    func autosave() async throws {
        let (store, _) = store()
        let owner = UUID(), moment = UUID()
        let autosave = CompleteAutosave(store: store, momentId: moment, debounce: .milliseconds(20), ownerId: { owner })
        autosave.schedule(answered())
        #expect(store.load(momentId: moment, ownerId: owner) == nil)
        try await Task.sleep(for: .milliseconds(80))
        #expect(store.load(momentId: moment, ownerId: owner) != nil)

        let other = UUID()
        let second = CompleteAutosave(store: store, momentId: other, debounce: .seconds(10), ownerId: { owner })
        second.schedule(answered())
        second.flush()
        #expect(store.load(momentId: other, ownerId: owner) != nil)
        second.schedule(answered())
        second.clear()
        #expect(store.load(momentId: other, ownerId: owner) == nil)
    }
}
