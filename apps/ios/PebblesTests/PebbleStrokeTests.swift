import Testing
import CoreGraphics
@testable import Pebbles

@Suite
struct PebbleStrokeTests {

    @Test func outlineWidthIsSix() {
        #expect(PebbleStroke.outlineWidth == 6)
    }

    @Test func lineWidthEqualsOutlineWidthAtOneToOne() {
        let viewBox = CGRect(x: 0, y: 0, width: 260, height: 260)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 260, height: 260))
        #expect(width == 6)
    }

    @Test func lineWidthScalesDownWithFrame() {
        let viewBox = CGRect(x: 0, y: 0, width: 260, height: 260)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 52, height: 52))
        // scale = 52/260 = 0.2 → 6 * 0.2 = 1.2
        #expect(abs(width - 1.2) < 0.0001)
    }

    @Test func lineWidthUsesMinAxisForNonSquareViewBox() {
        // Small pebble canvas is 250×200; fit uses the smaller per-axis scale.
        let viewBox = CGRect(x: 0, y: 0, width: 250, height: 200)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 125, height: 125))
        // scaleX = 125/250 = 0.5, scaleY = 125/200 = 0.625 → min = 0.5 → 6 * 0.5 = 3
        #expect(abs(width - 3) < 0.0001)
    }
}
