import Foundation
import Testing
@testable import Pebbles

@Suite("SoulWithGlyph decoding")
struct SoulWithGlyphDecodingTests {

    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    @Test("decodes the joined PostgREST payload for the system default glyph")
    func decodesSystemDefault() throws {
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Alex",
          "glyph_id": "\(glyphId.uuidString)",
          "glyphs": {
            "id": "\(glyphId.uuidString)",
            "name": null,
            "strokes": [],
            "view_box": "0 0 200 200"
          }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.id == soulId)
        #expect(decoded.name == "Alex")
        #expect(decoded.glyphId == glyphId)
        #expect(decoded.glyph.id == glyphId)
        #expect(decoded.glyph.viewBox == "0 0 200 200")
        #expect(decoded.glyph.strokes.isEmpty)
    }

    @Test("decodes a soul with a user-carved glyph that has strokes")
    func decodesUserGlyph() throws {
        let userGlyphId = UUID()
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Sam",
          "glyph_id": "\(userGlyphId.uuidString)",
          "glyphs": {
            "id": "\(userGlyphId.uuidString)",
            "name": "wave",
            "strokes": [{"d": "M0,0 L10,10", "width": 6}],
            "view_box": "0 0 200 200"
          }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.glyph.name == "wave")
        #expect(decoded.glyph.strokes.count == 1)
        #expect(decoded.glyph.strokes.first?.d == "M0,0 L10,10")
    }

    @Test("soul accessor strips the joined glyph")
    func soulAccessor() throws {
        let json = """
        {
          "id": "\(soulId.uuidString)",
          "name": "Alex",
          "glyph_id": "\(glyphId.uuidString)",
          "glyphs": { "id": "\(glyphId.uuidString)", "name": null, "strokes": [], "view_box": "0 0 200 200" }
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(SoulWithGlyph.self, from: json)
        #expect(decoded.soul.id == soulId)
        #expect(decoded.soul.name == "Alex")
        #expect(decoded.soul.glyphId == glyphId)
    }
}
