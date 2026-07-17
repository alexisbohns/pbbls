import Foundation
import Testing
@testable import Pebbles

@Suite("BannerAspect")
struct BannerAspectTests {

    @Test("16:9 source picks .sixteenNine")
    func sixteenNineSource() {
        #expect(BannerAspect.nearest(to: 16.0 / 9.0) == .sixteenNine)
    }

    @Test("4:3 source picks .fourThree")
    func fourThreeSource() {
        #expect(BannerAspect.nearest(to: 4.0 / 3.0) == .fourThree)
    }

    @Test("Square source picks .square")
    func squareSource() {
        #expect(BannerAspect.nearest(to: 1.0) == .square)
    }

    @Test("3:4 portrait source picks .threeFour")
    func threeFourSource() {
        #expect(BannerAspect.nearest(to: 3.0 / 4.0) == .threeFour)
    }

    @Test("Portrait 9:16 source picks .threeFour (nearest is 0.75, not 1.0)")
    func portraitSource() {
        // r ≈ 0.5625; |0.5625 - 0.75| = 0.1875 < |0.5625 - 1.0| = 0.4375 → 3:4 wins.
        #expect(BannerAspect.nearest(to: 9.0 / 16.0) == .threeFour)
    }

    @Test("3:2 source picks .fourThree (closer than 16:9)")
    func threeTwoSource() {
        // r = 1.5; |1.5 - 1.333| = 0.167; |1.5 - 1.778| = 0.278 → 4:3 wins.
        #expect(BannerAspect.nearest(to: 3.0 / 2.0) == .fourThree)
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
        #expect(BannerAspect.threeFour.cgRatio   == 3.0 / 4.0)
    }
}
