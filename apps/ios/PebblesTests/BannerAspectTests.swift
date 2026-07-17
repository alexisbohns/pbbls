import Foundation
import Testing
@testable import Pebbles

/// `BannerAspect.nearest` — the #599 three-bucket snap (4:3 / 1:1 / 3:4),
/// mirroring Android `BannerAspectTest`.
@Suite("BannerAspect")
struct BannerAspectTests {

    @Test("4:3 source picks .fourThree")
    func fourThreeSource() {
        #expect(BannerAspect.nearest(to: 4.0 / 3.0) == .fourThree)
        #expect(BannerAspect.nearest(to: 1.4) == .fourThree)
    }

    @Test("Very wide sources still bucket to .fourThree")
    func extremeLandscape() {
        #expect(BannerAspect.nearest(to: 16.0 / 9.0) == .fourThree)
        #expect(BannerAspect.nearest(to: 2.4) == .fourThree)
    }

    @Test("Square source picks .square")
    func squareSource() {
        #expect(BannerAspect.nearest(to: 1.0) == .square)
    }

    @Test("Portrait 3:4 source picks .threeFour")
    func portraitSource() {
        #expect(BannerAspect.nearest(to: 3.0 / 4.0) == .threeFour)
        #expect(BannerAspect.nearest(to: 0.8) == .threeFour)
    }

    @Test("Very tall sources still bucket to .threeFour")
    func extremePortrait() {
        #expect(BannerAspect.nearest(to: 0.1) == .threeFour)
    }

    @Test("Boundaries split at the midpoints")
    func boundaries() {
        // Midpoint between 3/4 and 1.0 is 0.875; between 1.0 and 4/3 is ~1.1667.
        #expect(BannerAspect.nearest(to: 0.87) == .threeFour)
        #expect(BannerAspect.nearest(to: 0.88) == .square)
        #expect(BannerAspect.nearest(to: 1.16) == .square)
        #expect(BannerAspect.nearest(to: 1.17) == .fourThree)
    }

    @Test("cgRatio matches the bucket")
    func cgRatioValues() {
        #expect(BannerAspect.fourThree.cgRatio == 4.0 / 3.0)
        #expect(BannerAspect.square.cgRatio    == 1.0)
        #expect(BannerAspect.threeFour.cgRatio == 3.0 / 4.0)
    }
}
