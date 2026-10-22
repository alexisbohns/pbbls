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
}
