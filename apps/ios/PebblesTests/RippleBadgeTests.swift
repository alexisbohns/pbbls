import CoreGraphics
import Testing
@testable import Pebbles

/// #1382 D17: the badge draws at any side; its digit goes below 32 pt and its
/// stroke scales with the side, never under 1.25.
@Suite("RippleBadge")
struct RippleBadgeTests {
    @Test("the digit shows at 44 and 32, not at 22")
    func digit() {
        #expect(RippleBadge.showsDigit(side: 44))
        #expect(RippleBadge.showsDigit(side: 32))
        #expect(!RippleBadge.showsDigit(side: 22))
    }

    @Test("the stroke is 2 at 44 and 1.25 at 22")
    func stroke() {
        #expect(RippleBadge.strokeWidth(side: 44) == 2)
        #expect(RippleBadge.strokeWidth(side: 22) == 1.25)
    }
}
