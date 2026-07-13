import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// Marker class so `Bundle(for:)` resolves the test bundle (same pattern as
/// `LocalizationTests`' `CatalogProbe`).
private final class WobbleFixtureProbe: NSObject {}

/// Decoded `WobbleGolden.json` — reference values generated from the issue
/// #555 design playground's JS implementation of the SVG 1.1 §15.19 noise.
/// Regenerate with `node apps/ios/Scripts/generate-wobble-golden.mjs
/// apps/ios/PebblesTests/Wobble/WobbleGolden.json` — never edit by hand.
struct WobbleGolden: Decodable {
    struct GradientSample: Decodable {
        let channel: Int
        let index: Int
        let x: Double
        let y: Double
    }

    struct TurbulenceCase: Decodable {
        let channel: Int
        let x: Double
        let y: Double
        let frequency: Double
        let octaves: Int
        let value: Double
    }

    struct DisplacedCase: Decodable {
        let x: Double
        let y: Double
        let amplitude: Double
        let frequency: Double
        let octaves: Int
        let xOut: Double
        let yOut: Double
    }

    let seed: Int
    let lcg: [Int]
    let latticePrefix: [Int]
    let gradientSamples: [GradientSample]
    let turbulence: [TurbulenceCase]
    let displaced: [DisplacedCase]

    static func load() throws -> WobbleGolden {
        let bundle = Bundle(for: WobbleFixtureProbe.self)
        let url = try #require(
            bundle.url(forResource: "WobbleGolden", withExtension: "json"),
            "WobbleGolden.json not found in test bundle — re-run xcodegen so PebblesTests picks it up"
        )
        return try JSONDecoder().decode(WobbleGolden.self, from: Data(contentsOf: url))
    }
}

@Suite("SVGTurbulence — golden values vs the playground reference")
struct SVGTurbulenceTests {

    @Test("raw LCG sequence matches the reference exactly")
    func lcgSequence() throws {
        let golden = try WobbleGolden.load()
        #expect(SVGTurbulence.rawSequence(seed: golden.seed, count: golden.lcg.count) == golden.lcg)
    }

    @Test("lattice prefix and gradient samples match after init")
    func latticeAndGradients() throws {
        let golden = try WobbleGolden.load()
        let noise = SVGTurbulence(seed: golden.seed)
        #expect(noise.latticePrefix16 == golden.latticePrefix)
        for sample in golden.gradientSamples {
            let vector = noise.gradientSample(channel: sample.channel, index: sample.index)
            #expect(
                abs(vector.x - sample.x) < 1e-12 && abs(vector.y - sample.y) < 1e-12,
                "gradient[\(sample.channel)][\(sample.index)] diverged: got \(vector), want (\(sample.x), \(sample.y))"
            )
        }
    }

    @Test("turbulence values match within 1e-9")
    func turbulenceValues() throws {
        let golden = try WobbleGolden.load()
        let noise = SVGTurbulence(seed: golden.seed)
        for testCase in golden.turbulence {
            let value = noise.turbulence(
                channel: testCase.channel,
                x: testCase.x,
                y: testCase.y,
                baseFrequency: testCase.frequency,
                octaves: testCase.octaves
            )
            #expect(
                abs(value - testCase.value) < 1e-9,
                "channel \(testCase.channel) at (\(testCase.x), \(testCase.y)) f=\(testCase.frequency) o=\(testCase.octaves): got \(value), want \(testCase.value)"
            )
        }
    }

    @Test("displacement matches the playground bake within 1e-9")
    func displacement() throws {
        let golden = try WobbleGolden.load()
        let noise = SVGTurbulence(seed: golden.seed)
        for testCase in golden.displaced {
            let params = WobbleParams(
                amplitude: testCase.amplitude,
                frequency: testCase.frequency,
                octaves: testCase.octaves,
                flattenStep: 2
            )
            let out = params.displace(CGPoint(x: testCase.x, y: testCase.y), using: noise)
            #expect(
                abs(Double(out.x) - testCase.xOut) < 1e-9 && abs(Double(out.y) - testCase.yOut) < 1e-9,
                "displace(\(testCase.x), \(testCase.y)) diverged: got \(out), want (\(testCase.xOut), \(testCase.yOut))"
            )
        }
    }

    @Test("determinism: two instances produce identical values")
    func determinism() {
        let first = SVGTurbulence(seed: WobbleParams.seed)
        let second = SVGTurbulence(seed: WobbleParams.seed)
        for (x, y) in [(0.0, 0.0), (12.3, 45.6), (199.0, 3.0)] {
            let a = first.turbulence(channel: 0, x: x, y: y, baseFrequency: 0.024, octaves: 5)
            let b = second.turbulence(channel: 0, x: x, y: y, baseFrequency: 0.024, octaves: 5)
            #expect(a == b)
        }
    }
}
