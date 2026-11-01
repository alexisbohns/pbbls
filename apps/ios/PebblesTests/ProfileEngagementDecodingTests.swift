import Foundation
import Testing
@testable import Pebbles

@Suite("ProfileEngagement decoding")
struct ProfileEngagementDecodingTests {

    @Test("decodes the canonical RPC row shape")
    func decodesCanonicalRow() throws {
        let json = Data(#"""
        {
          "days_practiced": 42,
          "assiduity": [false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, false,
                        false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, true]
        }
        """#.utf8)

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)

        #expect(row.daysPracticed == 42)
        #expect(row.assiduity.count == 28)
        #expect(row.assiduity.last == true)
    }

    @Test("decodes zero-state correctly")
    func decodesZeroState() throws {
        let json = Data(#"""
        { "days_practiced": 0, "assiduity": [
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false
        ] }
        """#.utf8)

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)
        #expect(row.daysPracticed == 0)
        #expect(row.assiduity.allSatisfy { $0 == false })
    }

    // MARK: - The glyph counts (#1386)

    /// The harness's verbatim dump (`verify-home.ts`, `HOME_DUMP=1`): the RPC's one-row array.
    static func engagementPayload() throws -> Data {
        try HomePayloads.data("profile-engagement")
    }

    /// The payload's row with each key in `keys` set to `value` (nil removes it).
    static func edited(_ keys: [String], to value: Any?) throws -> Data {
        var rows = try #require(try JSONSerialization.jsonObject(with: engagementPayload()) as? [[String: Any]])
        for key in keys { rows[0][key] = value }
        return try JSONSerialization.data(withJSONObject: rows)
    }

    @Test("decodes the harness's row with both glyph counts")
    func decodesGlyphCounts() throws {
        let row = try #require(try JSONDecoder().decode([ProfileEngagement].self, from: Self.engagementPayload()).first)
        #expect(row.daysPracticed == 1 && row.assiduity.count == 28 && row.assiduity.last == true)
        #expect(row.glyphsCarved == 1)
        #expect(row.glyphsOwned == 0)
    }

    @Test("a row without the two keys (a build older than #1386's server) decodes with nil counts")
    func decodesWithoutGlyphCounts() throws {
        let data = try Self.edited(["glyphs_carved", "glyphs_owned"], to: nil)
        let row = try #require(try JSONDecoder().decode([ProfileEngagement].self, from: data).first)
        #expect(row.glyphsCarved == nil && row.glyphsOwned == nil)
        #expect(row.daysPracticed == 1)
    }

    @Test("explicit nulls decode as nil counts")
    func decodesNullGlyphCounts() throws {
        let data = try Self.edited(["glyphs_carved", "glyphs_owned"], to: NSNull())
        let row = try #require(try JSONDecoder().decode([ProfileEngagement].self, from: data).first)
        #expect(row.glyphsCarved == nil && row.glyphsOwned == nil)
    }
}
