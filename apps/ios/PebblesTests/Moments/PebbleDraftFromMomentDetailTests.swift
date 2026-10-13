import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDraft init(from: MomentDetail)")
struct PebbleDraftFromMomentDetailTests {

    @Test("a completed moment prefills every field the old form edits")
    func completed() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = try #require(PebbleDraft(from: detail))
        #expect(draft.name == "Coffee with Ana")
        #expect(draft.description == "First rain of autumn.")
        #expect(draft.happenedAt == detail.happenedAt)
        #expect(draft.emotionId == MomentFixtures.uuid(MomentFixtures.joyId))
        #expect(draft.domainId == MomentFixtures.uuid(MomentFixtures.domainId))
        #expect(draft.valence == .highlightMedium)
        #expect(draft.soulIds == [MomentFixtures.uuid(MomentFixtures.soulId)])
        #expect(draft.collectionId == MomentFixtures.uuid(MomentFixtures.collectionId))
        #expect(draft.visibility == .private)
        #expect(draft.glyphId == nil)
    }

    @Test("a bare moment has no pebble to edit")
    func bare() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        #expect(PebbleDraft(from: detail) == nil)
    }
}
