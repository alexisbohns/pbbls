import Foundation
import Testing
@testable import Pebbles

@Suite("SaveMomentPayload from a capture")
struct SaveMomentPayloadCaptureTests {

    private let userId = UUID(uuidString: "5F0E1D2C-3B4A-4958-8776-655443322110")!

    @Test("a named capture without a photo: no id, no description, no snaps")
    func named() {
        var draft = CaptureDraft()
        draft.name = "  Coffee  "
        draft.happenedAt = Date(timeIntervalSince1970: 1_791_793_800)
        let payload = SaveMomentPayload(capture: draft, formSnap: nil, userId: userId)
        #expect(payload.id == nil)
        #expect(payload.name == "Coffee")
        #expect(payload.description == nil)
        #expect(payload.snaps == nil)
        #expect(payload.happenedAt == draft.happenedAt)
    }

    @Test("a photo-only capture omits the name and sends the snap")
    func photoOnly() {
        let snapId = UUID()
        let payload = SaveMomentPayload(
            capture: CaptureDraft(),
            formSnap: .existing(id: snapId, storagePath: "u/s"),
            userId: userId
        )
        #expect(payload.name == nil)
        #expect(payload.snaps == [.init(id: snapId, storagePath: "u/s", sortOrder: 0)])
    }
}
