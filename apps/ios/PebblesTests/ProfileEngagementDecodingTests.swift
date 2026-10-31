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

    /// STAND-IN for `packages/supabase/scripts/oracles/home/payloads/profile-engagement.json`,
    /// which `verify-home.ts` writes with `HOME_DUMP=1` once #1400 is pushed: the
    /// harness's `JSON.stringify(data, null, 2)` of the RPC's one-row array, in the
    /// column order of `20261031120000_profile_engagement_glyph_counts.sql`. Swap it
    /// for the real file (read through `#filePath`, the `HomePayloads` pattern) after the push.
    static let glyphCountsPayload = #"""
    [
      {
        "days_practiced": 1,
        "assiduity": [
          false, false, false, false, false, false, false,
          false, false, false, false, false, false, false,
          false, false, false, false, false, false, false,
          false, false, false, false, false, false, true
        ],
        "glyphs_carved": 2,
        "glyphs_owned": 0
      }
    ]
    """#

    @Test("decodes the #1386 row with both glyph counts")
    func decodesGlyphCounts() throws {
        let rows = try JSONDecoder().decode([ProfileEngagement].self, from: Data(Self.glyphCountsPayload.utf8))
        let row = try #require(rows.first)
        #expect(row.daysPracticed == 1 && row.assiduity.count == 28 && row.assiduity.last == true)
        #expect(row.glyphsCarved == 2)
        #expect(row.glyphsOwned == 0)
    }

    @Test("today's live row, without the two keys, decodes with nil counts")
    func decodesWithoutGlyphCounts() throws {
        let json = Self.glyphCountsPayload
            .replacingOccurrences(of: ",\n    \"glyphs_carved\": 2,\n    \"glyphs_owned\": 0", with: "")
        #expect(!json.contains("glyphs_"))
        let row = try #require(try JSONDecoder().decode([ProfileEngagement].self, from: Data(json.utf8)).first)
        #expect(row.glyphsCarved == nil && row.glyphsOwned == nil)
        #expect(row.daysPracticed == 1)
    }

    @Test("explicit nulls decode as nil counts")
    func decodesNullGlyphCounts() throws {
        let json = Self.glyphCountsPayload
            .replacingOccurrences(of: "\"glyphs_carved\": 2", with: "\"glyphs_carved\": null")
            .replacingOccurrences(of: "\"glyphs_owned\": 0", with: "\"glyphs_owned\": null")
        let row = try #require(try JSONDecoder().decode([ProfileEngagement].self, from: Data(json.utf8)).first)
        #expect(row.glyphsCarved == nil && row.glyphsOwned == nil)
    }
}
