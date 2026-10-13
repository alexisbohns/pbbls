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

    @Test("decodes the collection's glyph when the select embeds it")
    func decodesGlyph() throws {
        let json = Data("""
        {"id":"55555555-5555-4555-8555-555555555555","name":"Autumn","mode":null,
         "glyph_id":"66666666-6666-4666-8666-666666666666",
         "glyph":{"id":"66666666-6666-4666-8666-666666666666","name":"Leaf",
                  "strokes":[{"d":"M 20,100 Q 100,20 180,100","width":6}],
                  "view_box":"0 0 200 200","user_id":"5f0e1d2c-3b4a-4958-8776-655443322110"},
         "pebble_count":[{"count":3}]}
        """.utf8)
        let collection = try JSONDecoder().decode(Collection.self, from: json)
        #expect(collection.glyphId == UUID(uuidString: "66666666-6666-4666-8666-666666666666"))
        #expect(collection.glyph?.name == "Leaf")
        #expect(collection.pebbleCount == 3)
    }

    @Test("an explicit null glyph and a missing one both decode to nil")
    func nullGlyph() throws {
        let explicit = Data(#"{"id":"55555555-5555-4555-8555-555555555555","name":"A","mode":"stack","glyph_id":null,"glyph":null}"#.utf8)
        let missing = Data(#"{"id":"55555555-5555-4555-8555-555555555555","name":"A","mode":"stack"}"#.utf8)
        #expect(try JSONDecoder().decode(Collection.self, from: explicit).glyph == nil)
        #expect(try JSONDecoder().decode(Collection.self, from: missing).glyphId == nil)
    }
}
