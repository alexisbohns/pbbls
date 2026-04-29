import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDetail decoding with embedded soul glyph")
struct PebbleDetailSoulGlyphDecodingTests {

    private let pebbleId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
    private let emotionId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    @Test("decodes a pebble whose soul includes a joined glyph")
    func decodesPebbleWithSoulGlyph() throws {
        let json = Data("""
        {
          "id": "\(pebbleId.uuidString)",
          "name": "Test pebble",
          "description": null,
          "happened_at": "2026-04-28T10:00:00Z",
          "intensity": 2,
          "positiveness": 1,
          "visibility": "private",
          "render_svg": null,
          "render_version": null,
          "glyph_id": null,
          "emotion": {
            "id": "\(emotionId.uuidString)",
            "slug": "joy",
            "name": "Joy",
            "color": "#FFCC00"
          },
          "pebble_domains": [],
          "pebble_souls": [
            {
              "soul": {
                "id": "\(soulId.uuidString)",
                "name": "Alex",
                "glyph_id": "\(glyphId.uuidString)",
                "glyphs": {
                  "id": "\(glyphId.uuidString)",
                  "name": null,
                  "strokes": [{"d": "M0,0 L10,10", "width": 6}],
                  "view_box": "0 0 200 200"
                }
              }
            }
          ],
          "collection_pebbles": [],
          "snaps": []
        }
        """.utf8)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let detail = try decoder.decode(PebbleDetail.self, from: json)

        #expect(detail.souls.count == 1)
        #expect(detail.souls[0].id == soulId)
        #expect(detail.souls[0].name == "Alex")
        #expect(detail.souls[0].glyph.id == glyphId)
        #expect(detail.souls[0].glyph.strokes.count == 1)
        #expect(detail.souls[0].glyph.strokes.first?.d == "M0,0 L10,10")
    }

    @Test("decodes a pebble with zero souls")
    func decodesPebbleWithoutSouls() throws {
        let json = Data("""
        {
          "id": "\(pebbleId.uuidString)",
          "name": "No souls",
          "description": null,
          "happened_at": "2026-04-28T10:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "render_svg": null,
          "render_version": null,
          "glyph_id": null,
          "emotion": {
            "id": "\(emotionId.uuidString)",
            "slug": "calm",
            "name": "Calm",
            "color": "#88AACC"
          },
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": [],
          "snaps": []
        }
        """.utf8)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let detail = try decoder.decode(PebbleDetail.self, from: json)

        #expect(detail.souls.isEmpty)
    }
}
