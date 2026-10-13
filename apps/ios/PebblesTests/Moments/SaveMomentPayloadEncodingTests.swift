import Foundation
import Testing
@testable import Pebbles

@Suite("SaveMomentPayload encoding")
struct SaveMomentPayloadEncodingTests {

    private func object(_ payload: SaveMomentPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    @Test("unset keys are omitted, never sent as null")
    func omitsUnset() throws {
        let json = try object(SaveMomentPayload())
        #expect(json.isEmpty)
    }

    @Test("happened_at is whole seconds, UTC, Z")
    func wholeSeconds() throws {
        var payload = SaveMomentPayload()
        payload.happenedAt = Date(timeIntervalSince1970: 1_791_793_800.517)
        #expect(try object(payload)["happened_at"] as? String == "2026-10-12T08:30:00Z")
    }

    @Test("ids are lowercase, snaps carry storage_path and sort_order")
    func idsAndSnaps() throws {
        let momentId = UUID(uuidString: "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B")!
        let snapId = UUID(uuidString: "A1B2C3D4-E5F6-4789-8ABC-DEF012345678")!
        var payload = SaveMomentPayload()
        payload.id = momentId
        payload.snaps = [.init(id: snapId, storagePath: "u/s", sortOrder: 0)]
        let json = try object(payload)
        #expect(json["id"] as? String == "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b")
        let snap = try #require((json["snaps"] as? [[String: Any]])?.first)
        #expect(snap["id"] as? String == "a1b2c3d4-e5f6-4789-8abc-def012345678")
        #expect(snap["storage_path"] as? String == "u/s")
        #expect(snap["sort_order"] as? Int == 0)
    }

    @Test("an empty snaps array is sent, so the photo is removed")
    func emptySnaps() throws {
        var payload = SaveMomentPayload()
        payload.snaps = []
        #expect((try object(payload)["snaps"] as? [Any])?.isEmpty == true)
    }

    @Test("an empty name and description are sent as \"\", which clears them")
    func emptyStringsClear() throws {
        var payload = SaveMomentPayload()
        payload.name = ""
        payload.description = ""
        let json = try object(payload)
        #expect(json["name"] as? String == "")
        #expect(json["description"] as? String == "")
    }

    @Test("snap descriptors from the coordinator's FormSnap")
    func snapsFromFormSnap() {
        let userId = UUID(uuidString: "5F0E1D2C-3B4A-4958-8776-655443322110")!
        let snapId = UUID()
        #expect(SaveMomentPayload.snaps(for: nil, userId: userId).isEmpty)
        let existing = SaveMomentPayload.snaps(for: .existing(id: snapId, storagePath: "a/b"), userId: userId)
        #expect(existing == [.init(id: snapId, storagePath: "a/b", sortOrder: 0)])
        let pending = AttachedSnap(id: snapId, localThumb: Data([0xFF]), state: .uploaded)
        let fromPending = SaveMomentPayload.snaps(for: .pending(pending), userId: userId)
        let path = "5f0e1d2c-3b4a-4958-8776-655443322110/\(snapId.uuidString.lowercased())"
        #expect(fromPending == [.init(id: snapId, storagePath: path, sortOrder: 0)])
    }
}
