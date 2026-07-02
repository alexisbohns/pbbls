import Foundation
import Testing
@testable import Pebbles

@Suite("buy_glyph decoding")
struct GlyphSwapDecodingTests {

    @Test("decodes { entitlement_id, balance } from the RPC")
    func decodeResult() throws {
        let id = UUID()
        let json = Data("""
        { "entitlement_id": "\(id.uuidString)", "balance": 33 }
        """.utf8)
        let result = try JSONDecoder().decode(BuyGlyphResult.self, from: json)
        #expect(result.entitlementId == id)
        #expect(result.balance == 33)
    }
}
