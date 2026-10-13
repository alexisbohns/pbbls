import Foundation
import Testing
@testable import Pebbles

@Suite("CompletePebblePayload from CompleteDraft")
struct CompletePebblePayloadFromDraftTests {

    private let momentId = UUID()
    private let emotion = UUID()
    private let domain = UUID()

    @Test("an incomplete draft makes no payload")
    func incomplete() {
        var draft = CompleteDraft()
        draft.valence = .neutralMedium
        draft.emotionId = emotion
        #expect(CompletePebblePayload(momentId: momentId, draft: draft) == nil)
    }

    @Test("a complete draft sends every key the flow owns (A2)")
    func complete() throws {
        var draft = CompleteDraft()
        draft.valence = .lowlightLarge
        draft.emotionId = emotion
        draft.domainId = domain
        let payload = try #require(CompletePebblePayload(momentId: momentId, draft: draft))
        #expect(payload.intensity == 3)
        #expect(payload.positiveness == -1)
        #expect(payload.domainIds == [domain])
        #expect(payload.soulIds == [])
        #expect(payload.collectionIds == [])
        #expect(payload.visibility == .secret)
    }

    @Test("sanitizing drops souls and collections that no longer exist")
    func sanitize() {
        let kept = UUID(), gone = UUID(), collection = UUID()
        var draft = CompleteDraft()
        draft.soulIds = [kept, gone]
        draft.collectionId = collection
        let clean = draft.sanitized(known: .init(soulIds: [kept], collectionIds: []))
        #expect(clean.soulIds == [kept])
        #expect(clean.collectionId == nil)
    }

    @Test("visibility alone is not an answer")
    func isEmpty() {
        var draft = CompleteDraft()
        draft.visibility = .public
        #expect(draft.isEmpty)
        draft.domainId = domain
        #expect(!draft.isEmpty)
    }
}
