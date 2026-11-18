import CoreImage
import Foundation
import CoreImage.CIFilterBuiltins
import Testing
@testable import Pebbles

@Suite("PhotoMeasures")
struct PhotoMeasuresTests {
    typealias Img = FilterTestImages

    @Test("luminance percentiles of a grey ramp")
    func greyRampPercentiles() throws {
        let stats = try #require(ToneStats(Img.greyRamp()))
        let expected = [0.05, 0.25, 0.5, 0.75, 0.95]
        for (got, want) in zip(stats.luminance, expected) {
            #expect(abs(got - want) < 0.01, "got \(got), wanted about \(want)")
        }
        #expect(stats.meanSaturation < 0.001)
        #expect(abs(stats.castRedGreen) < 0.001 && abs(stats.castYellowBlue) < 0.001)
    }

    @Test("a pure red picture reads as a red cast")
    func redCast() throws {
        let stats = try #require(ToneStats(Img.flat(red: 1, green: 0, blue: 0)))
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

    @Test("a picture over 256 px is downscaled and still reads the same tone")
    func largePicture() throws {
        let stats = try #require(ToneStats(Img.flat(red: 0.5, green: 0.5, blue: 0.5, size: 1024)))
        for value in stats.luminance { #expect(abs(value - 0.5) < 0.01, "got \(value)") }
        #expect(abs(stats.castRedGreen) < 0.001 && abs(stats.castYellowBlue) < 0.001)
    }

    @Test("a pure blue picture reads as a blue cast")
    func blueCast() throws {
        let stats = try #require(ToneStats(Img.flat(red: 0, green: 0, blue: 1)))
        #expect(abs(stats.castYellowBlue + 1) < 0.01)
        #expect(abs(stats.castRedGreen) < 0.01)
    }

    @Test("a vector input is recorded as its components")
    func vectorStep() {
        let filter = CIFilter.toneCurve()
        filter.point1 = CGPoint(x: 0.3, y: 0.4)
        let step = PhotoMeasures.step(filter)
        #expect(step.filter == "CIToneCurve")
        #expect(step.values["inputPoint1"] == [0.3, 0.4])
    }

    @Test("the image input is left out even when it is set")
    func imageInputIsLeftOut() {
        let filter = CIFilter.exposureAdjust()
        filter.inputImage = CIImage(color: .black)
        filter.ev = 1.5
        let step = PhotoMeasures.step(filter)
        #expect(step.values[kCIInputImageKey] == nil)
        #expect(step.values["inputEV"] == [1.5])
    }

    @Test("recorded numbers are rounded to four decimals")
    func stepRounding() {
        let filter = CIFilter.exposureAdjust()
        filter.ev = 0.123456
        #expect(PhotoMeasures.step(filter).values["inputEV"] == [0.1235])
    }

    private var sample: PhotoMeasures {
        PhotoMeasures(luminance: [0.1, 0.2, 0.3, 0.4, 0.5], meanSaturation: 0.4, castRedGreen: 0.05,
                      castYellowBlue: -0.1, faceCount: 2,
                      scenes: [.init(identifier: "sky", confidence: 0.8)], hasDepth: true, aesthetics: nil,
                      autoEnhance: [.init(filter: "CIExposureAdjust", values: ["inputEV": [0.5]])])
    }

    @Test("a missing aesthetics score is exported as an explicit null")
    func aestheticsNull() throws {
        let data = try JSONEncoder().encode(sample)
        let object = try #require(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let value = try #require(object["aesthetics"])
        #expect(value is NSNull)
    }

    @Test("measures round-trip through JSON, with and without aesthetics")
    func roundTrip() throws {
        for aesthetics in [nil, 0.25] as [Double?] {
            var measures = sample
            measures.aesthetics = aesthetics
            let decoded = try JSONDecoder().decode(PhotoMeasures.self, from: JSONEncoder().encode(measures))
            #expect(decoded == measures)
        }
    }
}
