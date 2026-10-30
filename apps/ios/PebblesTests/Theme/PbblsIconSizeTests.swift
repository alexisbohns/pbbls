import SwiftUI
import Testing
@testable import Pebbles

@Suite("PbblsIconSize")
struct PbblsIconSizeTests {
    @Test("the five sizes, in order, from the 16pt floor (spec D6)")
    func rawValues() {
        #expect(PbblsIconSize.inline.rawValue == 16)
        #expect(PbblsIconSize.row.rawValue == 20)
        #expect(PbblsIconSize.toolbar.rawValue == 22)
        #expect(PbblsIconSize.tile.rawValue == 28)
        #expect(PbblsIconSize.hero.rawValue == 48)
        #expect(PbblsIconSize.allCases.map(\.rawValue) == [16, 20, 22, 28, 48])
        #expect(PbblsIconSize.allCases.allSatisfy { $0.rawValue >= 16 })
    }

    @Test("the sized initializer builds")
    @MainActor func sizedInitializer() {
        _ = PbblsIconImage(.home, .tile)
        _ = PbblsIconImage(.home, .inline, relativeTo: .caption2,
                           style: AnyShapeStyle(Color.pebbles.primary), label: "Home")
    }
}
