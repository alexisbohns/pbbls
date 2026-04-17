import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionInsertPayload encoding")
struct CollectionInsertPayloadEncodingTests {

    private let userId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!

    private func encode(_ payload: CollectionInsertPayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        return try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as! [String: Any]
    }

    @Test("encodes user_id as snake_case and name verbatim")
    func userIdAndName() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Summer", mode: "pack")
        let json = try encode(payload)
        // Swift's default JSONEncoder UUID strategy is `.deferredToUUID`, which
        // emits the same form as `UUID.uuidString`, so the round-trip is exact.
        #expect(json["user_id"] as? String == userId.uuidString)
        #expect(json["name"] as? String == "Summer")
    }

    @Test("encodes mode rawValue when set")
    func modeWhenSet() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "x", mode: "stack")
        let json = try encode(payload)
        #expect(json["mode"] as? String == "stack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionInsertPayload(userId: userId, name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
        #expect(json["mode"] is NSNull)
    }

    @Test("each mode rawValue round-trips")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let payload = CollectionInsertPayload(userId: userId, name: "x", mode: raw)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }
}
