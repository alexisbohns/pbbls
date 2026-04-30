import Foundation
import Testing
@testable import Pebbles

@Suite("BannerAspect")
struct BannerAspectTests {

    @Test("16:9 source picks .sixteenNine")
    func sixteenNineSource() {
        #expect(BannerAspect.nearest(to: 16.0 / 9.0) == .sixteenNine)
    }

    @Test("3:2 source picks .fourThree (closer than 16:9)")
    func threeTwoSource() {
        // r = 1.5; |1.5 - 1.333| = 0.167; |1.5 - 1.778| = 0.278 → 4:3 wins.
        #expect(BannerAspect.nearest(to: 3.0 / 2.0) == .fourThree)
    }

    @Test("Near-midpoint ~1.6 picks .sixteenNine (closer than 4:3)")
    func nearMidpointPicksSixteenNine() {
        // r = 1.6; |1.6 - 1.778| = 0.178; |1.6 - 1.333| = 0.267 → 16:9 wins.
        #expect(BannerAspect.nearest(to: 1.6) == .sixteenNine)
    }

    @Test("4:3 source picks .fourThree")
    func fourThreeSource() {
        #expect(BannerAspect.nearest(to: 4.0 / 3.0) == .fourThree)
    }

    @Test("Square source picks .square")
    func squareSource() {
        #expect(BannerAspect.nearest(to: 1.0) == .square)
    }

    @Test("Portrait 9:16 source picks .square (no portrait bucket)")
    func portraitSource() {
        // r ≈ 0.5625; closer to 1.0 than to 1.333 or 1.778.
        #expect(BannerAspect.nearest(to: 9.0 / 16.0) == .square)
    }

    @Test("Extreme landscape 21:9 source picks .sixteenNine")
    func extremeLandscape() {
        #expect(BannerAspect.nearest(to: 21.0 / 9.0) == .sixteenNine)
    }

    @Test("cgRatio matches the bucket")
    func cgRatioValues() {
        #expect(BannerAspect.sixteenNine.cgRatio == 16.0 / 9.0)
        #expect(BannerAspect.fourThree.cgRatio   == 4.0 / 3.0)
        #expect(BannerAspect.square.cgRatio      == 1.0)
    }
}
