import CoreImage
import CoreImage.CIFilterBuiltins
import Testing
@testable import Pebbles

@Suite("PhotoMeasures")
struct PhotoMeasuresTests {
    typealias T = FilterTestImages

    @Test("luminance percentiles of a grey ramp")
    func greyRampPercentiles() throws {
        let stats = try #require(ToneStats(T.greyRamp()))
        let expected = [0.05, 0.25, 0.5, 0.75, 0.95]
        for (got, want) in zip(stats.luminance, expected) {
            #expect(abs(got - want) < 0.01, "got \(got), wanted about \(want)")
        }
        #expect(stats.meanSaturation < 0.001)
        #expect(abs(stats.castRedGreen) < 0.001 && abs(stats.castYellowBlue) < 0.001)
    }

    @Test("a pure red picture reads as a red cast")
    func redCast() throws {
        let stats = try #require(ToneStats(T.flat(red: 1, green: 0, blue: 0)))
        #expect(abs(stats.castRedGreen - 1) < 0.01)
        #expect(abs(stats.castYellowBlue - 0.5) < 0.01)
        #expect(abs(stats.meanSaturation - 1) < 0.01)
    }

    @Test("an auto-enhance filter is recorded with its numeric inputs")
    func autoEnhanceStep() {
        let filter = CIFilter.exposureAdjust()
        filter.ev = 0.5
        let step = PhotoMeasures.step(filter)
        #expect(step.filter == "CIExposureAdjust")
        #expect(step.values["inputEV"] == [0.5])
        #expect(step.values[kCIInputImageKey] == nil)
    }
}
