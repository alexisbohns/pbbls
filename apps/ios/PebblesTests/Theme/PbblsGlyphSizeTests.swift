import SwiftUI
import Testing
@testable import Pebbles

@Suite("PbblsGlyphSize")
struct PbblsGlyphSizeTests {
    @Test("the five sizes, in order, from the 16pt floor (spec D6)")
    func rawValues() {
        #expect(PbblsGlyphSize.inline.rawValue == 16)
        #expect(PbblsGlyphSize.row.rawValue == 20)
        #expect(PbblsGlyphSize.toolbar.rawValue == 22)
        #expect(PbblsGlyphSize.tile.rawValue == 28)
        #expect(PbblsGlyphSize.hero.rawValue == 48)
        #expect(PbblsGlyphSize.allCases.map(\.rawValue) == [16, 20, 22, 28, 48])
        #expect(PbblsGlyphSize.allCases.allSatisfy { $0.rawValue >= 16 })
    }
}
