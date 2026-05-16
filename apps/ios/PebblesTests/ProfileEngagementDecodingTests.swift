import Foundation
import Testing
@testable import Pebbles

@Suite("ProfileEngagement decoding")
struct ProfileEngagementDecodingTests {

    @Test("decodes the canonical RPC row shape")
    func decodesCanonicalRow() throws {
        let json = #"""
        {
          "days_practiced": 42,
          "assiduity": [false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, false,
                        false, true, true, false, true, false, false,
                        true,  true, false, false, true, true, false]
        }
        """#.data(using: .utf8)!

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)

        #expect(row.daysPracticed == 42)
        #expect(row.assiduity.count == 28)
        #expect(row.assiduity.last == false)
    }

    @Test("decodes zero-state correctly")
    func decodesZeroState() throws {
        let json = #"""
        { "days_practiced": 0, "assiduity": [
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false,
          false,false,false,false,false,false,false
        ] }
        """#.data(using: .utf8)!

        let row = try JSONDecoder().decode(ProfileEngagement.self, from: json)
        #expect(row.daysPracticed == 0)
        #expect(row.assiduity.allSatisfy { $0 == false })
    }
}
