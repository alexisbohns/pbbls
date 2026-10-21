import Foundation
import Testing
@testable import Pebbles

/// #1130 D2 and the foreign-payload rule: `path_moments` rows in the exact
/// shape the server emits since `20261018120000_path_moments_souls.sql`
/// (jsonb objects with `"key": value` spacing, souls ordered by name), and the
/// captured pre-#1121 rows, which keep decoding with no souls.
@Suite("path_moments souls decoding")
struct PathMomentDecodingTests {
    // swiftlint:disable line_length
    static let rows = Data(#"""
[{"id":"3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b","name":"Picnic on the square","happened_at":"2026-10-08T11:10:00+00:00","created_at":"2026-10-08T11:12:41.376669+00:00","first_snap_path":null,"pebble":{"id": "3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b", "glyph": null, "souls": [{"id": "a1b2c3d4-0000-4000-8000-000000000001", "name": "Elio", "glyph_id": "b7c8d9e0-0000-4000-8000-0000000000e1"}, {"id": "a1b2c3d4-0000-4000-8000-000000000002", "name": "Mara", "glyph_id": "b7c8d9e0-0000-4000-8000-0000000000e2"}], "emotion": {"id": "50a68890-f6bb-e67a-6655-e84ff0b71f49", "name": "Joy", "slug": "joyful"}, "intensity": 2, "visibility": "private", "positiveness": 1}},
 {"id":"4a2e3d5c-6b7a-4899-8b1c-2d3e4f5a6b7c","name":null,"happened_at":"2026-10-10T17:05:00.123456+00:00","created_at":"2026-10-10T17:05:01+00:00","first_snap_path":"907e7d76-00d3-4e53-82a3-4849bf0a9614/d49168b5-b97a-4888-a787-43ec65bb416a","pebble":{"id": "5b3f4e6d-7c8b-49aa-9c2d-3e4f5a6b7c8d", "glyph": null, "souls": [], "emotion": null, "intensity": 1, "visibility": "private", "positiveness": 0}},
 {"id":"6c4a5f7e-8d9c-4abb-8d3e-4f5a6b7c8d9e","name":"Bread from the corner bakery","happened_at":"2026-10-07T16:20:00+00:00","created_at":"2026-10-07T16:20:00+00:00","first_snap_path":null,"pebble":null},
 {"id":"7d5b6a8f-9e0d-4bcc-9e4f-5a6b7c8d9e0f","name":"Rain on the window","happened_at":"2026-10-09T05:30:00+00:00","created_at":"2026-10-09T05:30:00+00:00","first_snap_path":null,"pebble":{"id": "8e6c7b9a-0f1e-4cdd-8f5a-6b7c8d9e0f1a", "glyph": null, "souls": null, "emotion": null, "intensity": 3, "visibility": "private", "positiveness": -1}}]
"""#.utf8)
    // swiftlint:enable line_length

    private func decode(_ data: Data) throws -> [MomentSummary] {
        try JSONDecoder().decode([MomentSummary].self, from: data)
    }

    @Test("souls arrive in the server's order with their glyph ids")
    func soulsInOrder() throws {
        let pebble = try #require(try decode(Self.rows)[0].pebble)
        #expect(pebble.souls.map(\.name) == ["Elio", "Mara"])
        #expect(pebble.souls[1].glyphId == UUID(uuidString: "b7c8d9e0-0000-4000-8000-0000000000e2"))
        #expect(pebble.glyph == nil)
    }

    @Test("an empty souls array, a null name, a null emotion and microseconds decode")
    func emptySouls() throws {
        let row = try decode(Self.rows)[1]
        #expect(row.name == nil)
        #expect(row.pebble?.souls == [])
        #expect(row.pebble?.emotion == nil)
        #expect(row.happenedAt > Vectors.at("2026-10-10T17:05:00Z"))
        #expect(row.createdAt == Vectors.at("2026-10-10T17:05:01Z"))
    }

    @Test("a bare moment has no pebble and so no souls")
    func bare() throws {
        let row = try decode(Self.rows)[2]
        #expect(row.pebble == nil)
        #expect(row.soulIds == [])
        #expect(!row.isPebble)
    }

    @Test("an explicit null souls decodes as no souls")
    func nullSouls() throws {
        let row = try decode(Self.rows)[3]
        #expect(row.pebble?.souls == [])
        #expect(row.soulIds == [])
        #expect(row.isPebble)
    }

    @Test("the captured pre-#1121 rows decode with no souls")
    func capturedWithoutSouls() throws {
        let rows = try decode(MomentFixtures.Captured.pathMoments)
        #expect(rows[0].pebble?.souls == [])
        #expect(rows[1].pebble == nil)
    }

    @Test("a v_moments_full soul has no glyph_id and decodes with a nil glyph")
    func detailSoulWithoutGlyph() throws {
        let row = try JSONDecoder().decode(MomentSummary.self, from: MomentFixtures.Captured.detailCompleted)
        let soul = try #require(row.pebble?.souls.first)
        #expect(soul.name == "Ana")
        #expect(soul.glyphId == nil)
    }

    @Test("the logic's ids are lowercase, as web and Android hash and tie-break on them")
    func lowercaseIds() throws {
        let row = try decode(Self.rows)[0]
        #expect(row.spineId == "3f1d2c4b-5a69-4788-9a0b-1c2d3e4f5a6b")
        #expect(row.soulIds == ["a1b2c3d4-0000-4000-8000-000000000001", "a1b2c3d4-0000-4000-8000-000000000002"])
        #expect(row.isPebble)
    }
}
