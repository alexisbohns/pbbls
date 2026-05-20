import Foundation
import Testing
@testable import Pebbles

@Suite("Pebble decoding (path_pebbles RPC shape)")
struct PebbleDecodingTests {

    private func decoder() -> JSONDecoder {
        let dec = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        dec.dateDecodingStrategy = .custom { decoder in
            let raw = try decoder.singleValueContainer().decode(String.self)
            // Allow both with and without fractional seconds.
            if let date = formatter.date(from: raw) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            return formatter.date(from: raw) ?? Date()
        }
        return dec
    }

    @Test("decodes a row with intensity, emotion, and first_snap_path")
    func decodesFullRow() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Sunday walk",
          "happened_at": "2026-05-10T08:30:00Z",
          "created_at": "2026-05-10T08:31:00Z",
          "intensity": 3,
          "positiveness": 0,
          "render_svg": "<svg/>",
          "emotion": { "id": "22222222-2222-2222-2222-222222222222", "slug": "joy", "name": "Joy" },
          "first_snap_path": "user-uuid/snap-uuid"
        }
        """.data(using: .utf8)!

        let pebble = try decoder().decode(Pebble.self, from: json)
        #expect(pebble.intensity == 3)
        #expect(pebble.firstSnapPath == "user-uuid/snap-uuid")
        #expect(pebble.emotion?.slug == "joy")
    }

    @Test("decodes a row with no emotion and no snap")
    func decodesMinimal() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Quiet evening",
          "happened_at": "2026-05-10T20:00:00Z",
          "created_at": "2026-05-10T20:05:00Z",
          "intensity": 1,
          "positiveness": 0,
          "render_svg": null,
          "emotion": null,
          "first_snap_path": null
        }
        """.data(using: .utf8)!

        let pebble = try decoder().decode(Pebble.self, from: json)
        #expect(pebble.intensity == 1)
        #expect(pebble.emotion == nil)
        #expect(pebble.firstSnapPath == nil)
        #expect(pebble.renderSvg == nil)
    }

    @Test func decodesPositiveness() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Sample",
          "happened_at": "2026-05-19T10:00:00Z",
          "created_at": "2026-05-19T10:00:00Z",
          "intensity": 2,
          "positiveness": -1,
          "render_svg": null,
          "emotion": null,
          "first_snap_path": null
        }
        """.data(using: .utf8)!
        let pebble = try decoder().decode(Pebble.self, from: json)
        #expect(pebble.positiveness == -1)
        #expect(pebble.valence == .lowlightMedium)
    }

    @Test func valenceCoversAllNineCases() {
        let cases: [(Int, Int, Valence)] = [
            (-1, 1, .lowlightSmall),  (-1, 2, .lowlightMedium),  (-1, 3, .lowlightLarge),
            ( 0, 1, .neutralSmall),   ( 0, 2, .neutralMedium),   ( 0, 3, .neutralLarge),
            ( 1, 1, .highlightSmall), ( 1, 2, .highlightMedium), ( 1, 3, .highlightLarge),
        ]
        for (pos, int, expected) in cases {
            let pebble = Pebble(
                id: UUID(),
                name: "x",
                happenedAt: .now,
                createdAt: .now,
                intensity: int,
                positiveness: pos,
                renderSvg: nil,
                emotion: nil,
                firstSnapPath: nil
            )
            #expect(pebble.valence == expected, "(\(pos), \(int))")
        }
    }
}
