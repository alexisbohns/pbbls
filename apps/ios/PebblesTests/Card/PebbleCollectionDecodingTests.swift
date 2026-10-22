import Foundation
import Testing
@testable import Pebbles

/// #1145 D9: the reference cache's collections carry their glyph, embedded as
/// the souls query embeds theirs; a collection without one decodes as before.
@Suite("PebbleCollection decoding")
struct PebbleCollectionDecodingTests {
    private func decode(_ json: String) throws -> PebbleCollection {
        try JSONDecoder().decode(PebbleCollection.self, from: Data(json.utf8))
    }

    @Test("the embedded glyph decodes with its strokes")
    func withGlyph() throws {
        let collection = try decode("""
        {"id": "55555555-5555-4555-8555-555555555555", "name": "Autumn",
         "glyph_id": "66666666-6666-4666-8666-666666666666",
         "glyphs": {"id": "66666666-6666-4666-8666-666666666666", "name": "Autumn",
                    "strokes": [{"d": "M 20,100 Q 100,20 180,100", "width": 6}], "view_box": "0 0 200 200"}}
        """)
        #expect(collection.name == "Autumn")
        #expect(collection.glyph?.strokes.count == 1)
        #expect(collection.glyph?.viewBox == "0 0 200 200")
    }

    @Test("a null glyph_id embeds null: no glyph")
    func nullGlyph() throws {
        let collection = try decode("""
        {"id": "99999999-9999-4999-8999-999999999999", "name": "Wins", "glyph_id": null, "glyphs": null}
        """)
        #expect(collection.glyph == nil)
    }

    @Test("a row from the old select, with no embed at all, still decodes")
    func oldSelect() throws {
        let collection = try decode(#"{"id": "99999999-9999-4999-8999-999999999999", "name": "Wins"}"#)
        #expect(collection.glyph == nil)
        #expect(collection.name == "Wins")
    }

    @Test("the memberwise initialiser defaults to no glyph")
    func memberwise() {
        #expect(PebbleCollection(id: UUID(), name: "Therapy").glyph == nil)
    }
}
