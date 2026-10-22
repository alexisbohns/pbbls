import Foundation
import Testing
@testable import Pebbles

/// #1099 D8: Edit pebble remembers what it prefilled, so the payload can tell
/// an untouched field from a changed one. The Complete flow never sets it.
@Suite("CompleteDraft prefill")
struct CompleteDraftTests {
    @Test("init(from:) records the first domain and the first collection it prefilled")
    func prefilled() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.prefill == CompleteDraft.Prefill(
            domainId: MomentFixtures.uuid(MomentFixtures.domainId),
            collectionId: MomentFixtures.uuid(MomentFixtures.collectionId)
        ))
    }

    @Test("a legacy pebble with no domain and no collection prefills nils, not nothing")
    func legacy() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.legacyMoment)
        let draft = CompleteDraft(from: try #require(detail.pebble))
        #expect(draft.prefill == CompleteDraft.Prefill(domainId: nil, collectionId: nil))
    }

    @Test("a fresh draft, the Complete flow's, has no prefill")
    func fresh() {
        #expect(CompleteDraft().prefill == nil)
    }

    @Test("isEmpty ignores the prefill")
    func emptiness() {
        var draft = CompleteDraft()
        draft.prefill = CompleteDraft.Prefill(domainId: UUID(), collectionId: UUID())
        #expect(draft.isEmpty)
    }
}
