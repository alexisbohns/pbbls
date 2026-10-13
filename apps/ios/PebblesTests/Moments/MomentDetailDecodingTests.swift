import Foundation
import Testing
@testable import Pebbles

@Suite("MomentDetail decoding (v_moments_full)")
struct MomentDetailDecodingTests {

    private func decode(_ data: Data) throws -> MomentDetail {
        try JSONDecoder().decode(MomentDetail.self, from: data)
    }

    @Test("the owner's completed moment: every relation, the inherited glyph")
    func completed() throws {
        let detail = try decode(MomentFixtures.completedMoment)
        #expect(detail.name == "Coffee with Ana")
        #expect(detail.description == "First rain of autumn.")
        #expect(detail.userId == MomentFixtures.uuid(MomentFixtures.ownerId))
        #expect(detail.hiddenAt == nil)
        #expect(detail.snaps.map(\.sortOrder) == [0])
        let pebble = try #require(detail.pebble)
        #expect(pebble.id == MomentFixtures.uuid("7e6d5c4b-3a29-4817-8f6e-5d4c3b2a1908"))
        #expect(pebble.emotion.slug == "joy")
        #expect(pebble.valence == .highlightMedium)
        #expect(pebble.visibility == .private)
        #expect(pebble.glyph?.source == .collection)
        #expect(pebble.domains.map(\.name) == ["Work"])
        #expect(pebble.souls.map(\.name) == ["Ana"])
        #expect(pebble.collections.map(\.name) == ["Autumn", "Wins"])
        #expect(pebble.collections[0].glyphId == MomentFixtures.uuid("66666666-6666-4666-8666-666666666666"))
        #expect(pebble.collections[1].glyphId == nil)
    }

    @Test("a photo-only bare moment: null name, null description, null pebble")
    func photoOnly() throws {
        let detail = try decode(MomentFixtures.photoOnlyMoment)
        #expect(detail.name == nil)
        #expect(detail.description == nil)
        #expect(detail.pebble == nil)
        #expect(detail.snaps.count == 1)
    }

    @Test("a connection's moment: no pebble, no snaps")
    func connection() throws {
        let detail = try decode(MomentFixtures.connectionMoment)
        #expect(detail.pebble == nil)
        #expect(detail.snaps.isEmpty)
    }

    @Test("a legacy pebble: fractional happened_at, empty relations, no glyph")
    func legacy() throws {
        let detail = try decode(MomentFixtures.legacyMoment)
        #expect(detail.happenedAt > MomentFixtures.date("2026-09-29T16:00:00Z"))
        #expect(detail.description == "")
        let pebble = try #require(detail.pebble)
        #expect(pebble.id == detail.id)
        #expect(pebble.domains.isEmpty)
        #expect(pebble.glyph == nil)
    }

    @Test("snaps come back sorted by sort_order whatever the wire order")
    func snapOrder() throws {
        let json = String(decoding: MomentFixtures.photoOnlyMoment, as: UTF8.self)
            .replacingOccurrences(
                of: "\"snaps\": [",
                with: "\"snaps\": [{\"id\": \"c3d4e5f6-a7b8-4901-8cde-f01234567890\", \"storage_path\": \"x/y\", \"sort_order\": 3},"
            )
        let detail = try decode(Data(json.utf8))
        #expect(detail.snaps.map(\.sortOrder) == [0, 3])
    }
}
