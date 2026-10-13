import Foundation
import Testing
@testable import Pebbles

@Suite("CompleteDraft init(from: MomentDetail.Pebble)")
struct CompleteDraftFromPebbleTests {

    @Test("prefills every pebble field, first domain and first collection (R5, A6)")
    func prefill() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let pebble = try #require(detail.pebble)
        let draft = CompleteDraft(from: pebble)
        #expect(draft.valence == .highlightMedium)
        #expect(draft.emotionId == MomentFixtures.uuid(MomentFixtures.joyId))
        #expect(draft.domainId == MomentFixtures.uuid(MomentFixtures.domainId))
        #expect(draft.soulIds == [MomentFixtures.uuid(MomentFixtures.soulId)])
        #expect(draft.collectionId == MomentFixtures.uuid(MomentFixtures.collectionId))
        #expect(draft.visibility == .private)
        #expect(draft.isComplete)
    }

    @Test("a legacy pebble with no domain is not complete until one is picked")
    func legacy() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.legacyMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.domainId == nil)
        #expect(!draft.isComplete)
    }

    @Test("the prefilled draft becomes a complete_pebble payload keeping one domain and one collection (A2)")
    func payload() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        let payload = try #require(CompletePebblePayload(momentId: detail.id, draft: draft))
        #expect(payload.momentId == detail.id)
        #expect(payload.domainIds == [MomentFixtures.uuid(MomentFixtures.domainId)])
        #expect(payload.collectionIds == [MomentFixtures.uuid(MomentFixtures.collectionId)])
        #expect(payload.visibility == .private)
    }
}
