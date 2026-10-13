import Foundation
import Testing
@testable import Pebbles

/// Decodes the bodies in `MomentFixtures.Captured`, which came from the linked
/// project itself, with the production types. A same-surface round-trip cannot
/// catch a decoder that disagrees with what the server really emits.
@Suite("Payloads captured from the linked project")
struct CapturedPayloadDecodingTests {

    private struct ErrorBody: Decodable {
        let code: String
        let message: String
    }

    @Test("path_moments: a completed row whose glyph is inherited from a collection")
    func pathCompleted() throws {
        let rows = try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.Captured.pathMoments)
        #expect(rows.count == 2)
        let row = rows[0]
        #expect(row.name == "Coffee")
        #expect(row.isCompleted)
        let pebble = try #require(row.pebble)
        #expect(pebble.emotion?.slug == "disgusted")
        let glyph = try #require(pebble.glyph)
        #expect(glyph.source == .collection)
        #expect(glyph.strokes.count == 5)
    }

    @Test("path_moments: a photo-only bare moment")
    func pathBare() throws {
        let rows = try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.Captured.pathMoments)
        let row = rows[1]
        #expect(row.name == nil)
        #expect(row.pebble == nil)
        #expect(row.firstSnapPath != nil)
    }

    @Test("v_moments_full: a completed moment")
    func detailCompleted() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.Captured.detailCompleted)
        #expect(detail.description == nil)
        #expect(detail.snaps.isEmpty)
        let pebble = try #require(detail.pebble)
        #expect(pebble.souls.map(\.name) == ["Ana"])
        #expect(pebble.domains.map(\.slug) == ["hobbies"])
        #expect(pebble.collections.map(\.name) == ["Autumn", "Wins"])
        #expect(pebble.collections[0].glyphId != nil)
        #expect(pebble.collections[1].glyphId == nil)
        #expect(pebble.glyph?.strokes.count == 5)
    }

    @Test("v_moments_full: a photo-only moment")
    func detailPhoto() throws {
        let detail = try JSONDecoder().decode(MomentDetail.self, from: MomentFixtures.Captured.detailPhoto)
        #expect(detail.pebble == nil)
        #expect(detail.name == nil)
        #expect(detail.snaps.count == 1)
        #expect(detail.snaps.first?.sortOrder == 0)
    }

    @Test("resolve_pebble_glyph: a glyph, and null for an unknown id")
    func resolver() throws {
        let glyph = try #require(try MomentsService.decodeOptionalGlyph(MomentFixtures.Captured.resolvedGlyph))
        #expect(glyph.source == .collection)
        #expect(glyph.strokes.count == 5)
        #expect(try MomentsService.decodeOptionalGlyph(MomentFixtures.Captured.resolvedUnknown) == nil)
    }

    @Test("delete_moment returns the storage paths to clean up")
    func deleteMoment() throws {
        let result = try JSONDecoder().decode(DeleteMomentResult.self, from: MomentFixtures.Captured.deleteMoment)
        #expect(result.storagePaths.count == 1)
    }

    @Test("save_moment returns a bare JSON string uuid")
    func saveMomentReturn() throws {
        let id = try JSONDecoder().decode(UUID.self, from: MomentFixtures.Captured.saveMomentReturn)
        #expect(id == UUID(uuidString: "17873608-6d12-4f59-a2f2-5ebdc28a9be2"))
    }

    @Test("PostgREST error bodies carry the slug the client maps")
    func errorBodies() throws {
        let empty = try JSONDecoder().decode(ErrorBody.self, from: MomentFixtures.Captured.errMomentEmpty)
        #expect(MomentWriteError.slug(in: empty.message) == .momentEmpty)
        let domain = try JSONDecoder().decode(ErrorBody.self, from: MomentFixtures.Captured.errNeedsDomain)
        #expect(MomentWriteError.slug(in: domain.message) == .pebbleNeedsDomain)
    }
}
