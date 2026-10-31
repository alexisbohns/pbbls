import Testing
@testable import Pebbles

/// #1145 D9: both of the cache's collection reads ask for the glyph, in the
/// souls query's shape. A single-table read with an embed, so no RPC.
@Suite("ReferenceDataService collections select")
struct ReferenceDataServiceTests {
    @Test("the select embeds the glyph's strokes and view box")
    func select() {
        #expect(ReferenceDataService.collectionsSelect == "id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    }

    /// The record flow's souls step renders each soul's count from the cache;
    /// without the aggregate every soul decoded as 0 (Android's #563).
    @Test("the souls select carries the pebble count aggregate")
    func soulsSelect() {
        #expect(ReferenceDataService.soulsSelect == SoulRoute.columns)
        #expect(ReferenceDataService.soulsSelect.contains("pebbles_count:pebble_souls(count)"))
    }
}
