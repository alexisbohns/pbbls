import CoreGraphics
import Testing
@testable import Pebbles

@Suite("CropRatio")
struct CropRatioTests {
    @Test("the chips run 4:5, 3:4, 1:1, 4:3, 5:4")
    func order() {
        #expect(CropRatio.allCases == [.fourFive, .threeFour, .square, .fourThree, .fiveFour])
    }

    @Test("value is width over height")
    func value() {
        #expect(CropRatio.fourFive.value == 0.8)
        #expect(CropRatio.fiveFour.value == 1.25)
        #expect(CropRatio.square.value == 1)
    }

    @Test("a quarter turn swaps each format for its pair", arguments: [
        (CropRatio.fourFive, CropRatio.fiveFour),
        (.threeFour, .fourThree),
        (.square, .square),
        (.fourThree, .threeFour),
        (.fiveFour, .fourFive)
    ])
    func paired(ratio: CropRatio, expected: CropRatio) {
        #expect(ratio.paired == expected)
    }

    @Test("the editor opens on the format closest to the photo", arguments: [
        (CGFloat(3) / 4, CropRatio.threeFour),   // iPhone portrait
        (CGFloat(4) / 3, .fourThree),            // iPhone landscape
        (CGFloat(9) / 16, .threeFour),           // portrait screenshot
        (CGFloat(16) / 9, .fourThree),           // landscape video still
        (CGFloat(2) / 3, .threeFour),            // DSLR portrait
        (CGFloat(1), .square),
        (CGFloat(1.1), .square),
        (CGFloat(0.8), .fourFive)
    ])
    func closest(ratio: CGFloat, expected: CropRatio) {
        #expect(CropRatio.closest(to: ratio) == expected)
    }

    @Test("accessibility label reads the two numbers")
    func label() {
        #expect(CropRatio.fourFive.width == 4)
        #expect(CropRatio.fourFive.height == 5)
    }
}
