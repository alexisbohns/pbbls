import Foundation
import Testing
@testable import Pebbles

@Suite("Collection decoding")
struct CollectionDecodingTests {

    private func decoder() -> JSONDecoder { JSONDecoder() }

    @Test("decodes list-query shape with populated count aggregate")
    func listQueryPopulatedCount() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Summer 2026",
          "mode": "pack",
          "pebble_count": [{ "count": 5 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.id.uuidString.lowercased() == "11111111-1111-1111-1111-111111111111")
        #expect(collection.name == "Summer 2026")
        #expect(collection.mode == .pack)
        #expect(collection.pebbleCount == 5)
    }

    @Test("decodes list-query shape with empty count array → 0")
    func listQueryEmptyCountArray() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Empty one",
          "mode": "stack",
          "pebble_count": []
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.pebbleCount == 0)
    }

    @Test("decodes detail-shape without pebble_count key → 0")
    func missingPebbleCountKey() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "No count",
          "mode": "track"
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.pebbleCount == 0)
        #expect(collection.mode == .track)
    }

    @Test("decodes rows with null mode as nil")
    func nullMode() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Modeless",
          "mode": null,
          "pebble_count": [{ "count": 2 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.mode == nil)
        #expect(collection.pebbleCount == 2)
    }

    @Test("decodes rows with missing mode key as nil")
    func missingModeKey() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Modeless",
          "pebble_count": [{ "count": 0 }]
        }
        """.utf8)
        let collection = try decoder().decode(Collection.self, from: json)
        #expect(collection.mode == nil)
    }

    @Test("CollectionMode decodes stack / pack / track")
    func allModes() throws {
        for raw in ["stack", "pack", "track"] {
            let json = Data("""
            { "id": "11111111-1111-1111-1111-111111111111",
              "name": "x", "mode": "\(raw)", "pebble_count": [] }
            """.utf8)
            let collection = try decoder().decode(Collection.self, from: json)
            #expect(collection.mode?.rawValue == raw)
        }
    }
}
