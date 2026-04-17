import Foundation
import Testing
@testable import Pebbles

@Suite("CollectionUpdatePayload encoding")
struct CollectionUpdatePayloadEncodingTests {

    private func encode(_ payload: CollectionUpdatePayload) throws -> [String: Any] {
        let data = try JSONEncoder().encode(payload)
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return try #require(object as? [String: Any])
    }

    @Test("encodes name and mode rawValue")
    func basicEncoding() throws {
        let payload = CollectionUpdatePayload(name: "Summer", mode: "pack")
        let json = try encode(payload)
        #expect(json["name"] as? String == "Summer")
        #expect(json["mode"] as? String == "pack")
    }

    @Test("encodes nil mode as JSON null, not absent")
    func nilModeEncodesAsNull() throws {
        let payload = CollectionUpdatePayload(name: "Modeless", mode: nil)
        let data = try JSONEncoder().encode(payload)
        let raw = String(data: data, encoding: .utf8) ?? ""
        #expect(raw.contains("\"mode\":null"))
        let object = try JSONSerialization.jsonObject(with: data)
        let json = try #require(object as? [String: Any])
        #expect(json["mode"] is NSNull)
    }

    @Test("each mode rawValue round-trips")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let payload = CollectionUpdatePayload(name: "x", mode: raw)
            let json = try encode(payload)
            #expect(json["mode"] as? String == raw)
        }
    }
}
