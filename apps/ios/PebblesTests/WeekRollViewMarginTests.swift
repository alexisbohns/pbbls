import CoreGraphics
import Testing
@testable import Pebbles

@Suite("WeekRollView.centeringMargin")
struct WeekRollViewMarginTests {

    @Test("centres a 72 pt cell inside the measured strip width")
    func centresCell() {
        // 393 pt is the iPhone 15/16/17 portrait width.
        #expect(WeekRollView.centeringMargin(containerWidth: 393) == 160.5)
    }

    @Test("an unmeasured strip gets no margin rather than a negative one")
    func clampsAtZero() {
        #expect(WeekRollView.centeringMargin(containerWidth: 0) == 0)
        #expect(WeekRollView.centeringMargin(containerWidth: 40) == 0)
    }
}
