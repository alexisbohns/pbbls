import Foundation
import Testing
@testable import Pebbles

@Suite("MomentsService pure helpers")
struct MomentsServiceTests {

    @Test("soul/collection lists keep only moments whose pebble is linked")
    func filtersByPebbleIds() throws {
        let all = try JSONDecoder().decode([MomentSummary].self, from: MomentFixtures.pathMoments)
        let legacyPebble = MomentFixtures.uuid("3eaf6c81-5074-4db1-82e9-4f5a6b7c8d9e")
        let kept = MomentsService.moments(all, carrying: [legacyPebble])
        #expect(kept.map(\.id) == [legacyPebble])
        // A moment id that is not a pebble id never matches.
        let byMomentId = MomentsService.moments(all, carrying: [all[0].id])
        #expect(byMomentId.isEmpty)
    }

    @Test("a JSON null resolver answer (or an empty body) is no glyph")
    func nullGlyph() throws {
        #expect(try MomentsService.decodeOptionalGlyph(Data("null".utf8)) == nil)
        #expect(try MomentsService.decodeOptionalGlyph(Data()) == nil)
        let glyph = Data(#"{"id":"4759c37c-68a6-46a6-b4fc-046bd0316752","name":null,"strokes":[],"view_box":"0 0 200 200","source":"domain"}"#.utf8)
        #expect(try MomentsService.decodeOptionalGlyph(glyph)?.source == .domain)
    }

    @Test("delete_moment's answer lists the storage prefixes to remove")
    func deleteMomentResult() throws {
        let json = Data(#"{"storage_paths": ["u/a", "u/b"]}"#.utf8)
        #expect(try JSONDecoder().decode(DeleteMomentResult.self, from: json).storagePaths == ["u/a", "u/b"])
        let none = Data(#"{"storage_paths": []}"#.utf8)
        #expect(try JSONDecoder().decode(DeleteMomentResult.self, from: none).storagePaths.isEmpty)
    }
}
