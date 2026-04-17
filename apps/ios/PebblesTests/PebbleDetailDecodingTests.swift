import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDetail decoding")
struct PebbleDetailDecodingTests {

    private func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            if let date = formatter.date(from: string) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO8601: \(string)"
            )
        }
        return decoder
    }

    private let fullJSON = Data("""
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "Shipped the thing",
      "description": "Finally.",
      "happened_at": "2026-04-14T15:42:00Z",
      "intensity": 3,
      "positiveness": 1,
      "visibility": "private",
      "emotion": {
        "id": "22222222-2222-2222-2222-222222222222",
        "name": "Joy",
        "color": "#FFD166"
      },
      "pebble_domains": [
        { "domain": { "id": "33333333-3333-3333-3333-333333333333", "name": "Work" } }
      ],
      "pebble_souls": [
        { "soul": { "id": "44444444-4444-4444-4444-444444444444", "name": "Alex" } }
      ],
      "collection_pebbles": [
        { "collection": { "id": "55555555-5555-5555-5555-555555555555", "name": "Wins" } }
      ]
    }
    """.utf8)

    @Test("decodes a full row with one of each relation")
    func decodesFullRow() throws {
        let detail = try makeDecoder().decode(PebbleDetail.self, from: fullJSON)

        #expect(detail.name == "Shipped the thing")
        #expect(detail.description == "Finally.")
        #expect(detail.intensity == 3)
        #expect(detail.positiveness == 1)
        #expect(detail.visibility == .private)
        #expect(detail.emotion.name == "Joy")
        #expect(detail.emotion.color == "#FFD166")
        #expect(detail.domains.map(\.name) == ["Work"])
        #expect(detail.souls.map(\.name) == ["Alex"])
        #expect(detail.collections.map(\.name) == ["Wins"])
    }

    @Test("valence is derived from intensity + positiveness")
    func derivesValence() throws {
        let detail = try makeDecoder().decode(PebbleDetail.self, from: fullJSON)
        #expect(detail.valence == .highlightLarge)
    }

    @Test("empty join arrays decode as empty")
    func decodesEmptyRelations() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Quiet moment",
          "description": null,
          "happened_at": "2026-04-14T08:00:00Z",
          "intensity": 1,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Calm",
            "color": "#88CCEE"
          },
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.utf8)

        let detail = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(detail.description == nil)
        #expect(detail.domains.isEmpty)
        #expect(detail.souls.isEmpty)
        #expect(detail.collections.isEmpty)
        #expect(detail.valence == .neutralSmall)
    }

    @Test("multiple domains flatten cleanly")
    func decodesMultipleDomains() throws {
        let json = Data("""
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Cross-domain moment",
          "description": null,
          "happened_at": "2026-04-14T08:00:00Z",
          "intensity": 2,
          "positiveness": -1,
          "visibility": "public",
          "emotion": {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Sad",
            "color": "#6699CC"
          },
          "pebble_domains": [
            { "domain": { "id": "33333333-3333-3333-3333-333333333333", "name": "Work" } },
            { "domain": { "id": "44444444-4444-4444-4444-444444444444", "name": "Health" } }
          ],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.utf8)

        let detail = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(detail.domains.map(\.name) == ["Work", "Health"])
        #expect(detail.visibility == .public)
        #expect(detail.valence == .lowlightMedium)
    }

    @Test("decodes render columns when present")
    func decodesRenderColumns() throws {
        let json = Data("""
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "test",
          "happened_at": "2026-04-15T12:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": [],
          "render_svg": "<svg/>",
          "render_version": "0.1.0"
        }
        """.utf8)

        let decoded = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(decoded.renderSvg == "<svg/>")
        #expect(decoded.renderVersion == "0.1.0")
    }

    @Test("decodes when render columns are absent (legacy pebble)")
    func decodesLegacy() throws {
        let json = Data("""
        {
          "id": "550e8400-e29b-41d4-a716-446655440000",
          "name": "legacy",
          "happened_at": "2026-04-15T12:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {"id": "550e8400-e29b-41d4-a716-446655440001", "name": "joy", "color": "#fff"},
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.utf8)

        let decoded = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(decoded.renderSvg == nil)
        #expect(decoded.renderVersion == nil)
    }
}
