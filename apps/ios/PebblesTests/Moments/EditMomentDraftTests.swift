import Foundation
import Testing
@testable import Pebbles

@Suite("EditMomentDraft and its payload")
struct EditMomentDraftTests {

    private let userId = MomentFixtures.uuid(MomentFixtures.ownerId)

    @Test("prefills from the moment; null name and description become empty")
    func prefill() throws {
        let photoOnly = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.photoOnlyMoment)
        let draft = EditMomentDraft(from: photoOnly)
        #expect(draft.name == "")
        #expect(draft.description == "")
        #expect(draft.happenedAt == photoOnly.happenedAt)
    }

    @Test("prefills a named moment verbatim")
    func prefillNamed() throws {
        let completed = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.completedMoment)
        let draft = EditMomentDraft(from: completed)
        #expect(draft.name == "Coffee with Ana")
        #expect(draft.description == "First rain of autumn.")
    }

    @Test("savable with a name or a photo (R6)")
    func canSave() {
        var draft = EditMomentDraft(happenedAt: .now, name: " ", description: "")
        #expect(!draft.canSave(hasSnap: false))
        #expect(draft.canSave(hasSnap: true))
        draft.name = "Coffee"
        #expect(draft.canSave(hasSnap: false))
    }

    @Test("blank name and description are sent as \"\" to clear (A2); snaps always sent")
    func payload() throws {
        let momentId = UUID()
        let draft = EditMomentDraft(
            happenedAt: Date(timeIntervalSince1970: 1_791_793_800), name: "  ", description: " \n"
        )
        let payload = SaveMomentPayload(edit: draft, momentId: momentId, formSnap: nil, userId: userId)
        #expect(payload.id == momentId)
        #expect(payload.name == "")
        #expect(payload.description == "")
        #expect(payload.snaps == [])
        #expect(payload.happenedAt == draft.happenedAt)

        let json = try #require(
            JSONSerialization.jsonObject(with: JSONEncoder().encode(payload)) as? [String: Any]
        )
        #expect(json["id"] as? String == momentId.uuidString.lowercased())
        #expect(json["name"] as? String == "")
        #expect(json["description"] as? String == "")
        #expect(json["happened_at"] as? String == "2026-10-12T08:30:00Z")
        #expect((json["snaps"] as? [Any])?.isEmpty == true)
    }

    @Test("a kept photo is sent as the whole snap set")
    func payloadKeepsPhoto() throws {
        let snapId = UUID()
        let draft = EditMomentDraft(happenedAt: .now, name: "Coffee", description: "")
        let payload = SaveMomentPayload(
            edit: draft,
            momentId: UUID(),
            formSnap: .existing(id: snapId, storagePath: "uid/\(snapId)"),
            userId: userId
        )
        #expect(payload.snaps == [SaveMomentPayload.Snap(id: snapId, storagePath: "uid/\(snapId)", sortOrder: 0)])
        #expect(payload.name == "Coffee")
    }

    @Test("the name is clamped to 40 like Capture")
    func clamp() {
        var draft = EditMomentDraft(happenedAt: .now, name: "", description: "")
        draft.setName(String(repeating: "b", count: 50))
        #expect(draft.name.count == 40)
    }
}
