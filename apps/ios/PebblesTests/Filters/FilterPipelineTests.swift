import CoreGraphics
import CoreImage
import Testing
@testable import Pebbles

@Suite("FilterPipeline")
struct FilterPipelineTests {
    typealias T = FilterTestImages

    @Test("neutral values are an exact identity")
    func neutralIsIdentity() throws {
        let source = T.ramp(size: 256)
        let plain = try #require(FilterPipeline.render(CIImage(cgImage: source),
                                                       context: FilterPipeline.sharedContext, colorSpace: T.sRGB))
        #expect(try T.bytes(T.render(.neutral, on: source)) == T.bytes(plain))
    }

    @Test("every control changes the picture at its far end")
    func everyControlActs() throws {
        let source = T.ramp(size: 256)
        let neutral = try T.bytes(T.render(.neutral, on: source))
        let controls: [(String, WritableKeyPath<FilterParams, Double>)] = [
            ("exposure", \.exposure), ("brilliance", \.brilliance), ("highlights", \.highlights),
            ("shadows", \.shadows), ("contrast", \.contrast), ("saturation", \.saturation),
            ("vibrance", \.vibrance), ("tone", \.tone), ("tint", \.tint), ("sharpness", \.sharpness),
            ("vignette", \.vignette), ("blurVignette", \.blurVignette), ("grain", \.grain)
        ]
        for (name, keyPath) in controls {
            var params = FilterParams.neutral
            params[keyPath: keyPath] = 1
            #expect(try T.bytes(T.render(params, on: source)) != neutral, "\(name) at 1 left the picture unchanged")
        }
    }

    @Test("exposure up brightens, down darkens")
    func exposureDirection() throws {
        let grey = T.flat(red: 0.5, green: 0.5, blue: 0.5)
        var up = FilterParams.neutral; up.exposure = 0.5
        var down = FilterParams.neutral; down.exposure = -0.5
        let base = T.luma(try T.means(T.render(.neutral, on: grey)))
        #expect(T.luma(try T.means(T.render(up, on: grey))) > base + 0.05)
        #expect(T.luma(try T.means(T.render(down, on: grey))) < base - 0.05)
    }

    @Test("tone up warms, tint up turns magenta")
    func toneAndTintDirection() throws {
        let grey = T.flat(red: 0.5, green: 0.5, blue: 0.5)
        var warm = FilterParams.neutral; warm.tone = 1
        var magenta = FilterParams.neutral; magenta.tint = 1
        let w = try T.means(T.render(warm, on: grey))
        let m = try T.means(T.render(magenta, on: grey))
        #expect(w[0] - w[2] > 0.02)
        #expect((m[0] + m[2]) / 2 - m[1] > 0.01)
    }

    @Test("saturation down to −1 removes colour")
    func desaturate() throws {
        let colour = T.flat(red: 0.7, green: 0.4, blue: 0.3)
        var params = FilterParams.neutral; params.saturation = -1
        let rgb = try T.means(T.render(params, on: colour))
        #expect(abs(rgb[0] - rgb[1]) < 0.02 && abs(rgb[1] - rgb[2]) < 0.02)
    }

    @Test("contrast up spreads the tones")
    func contrastSpreads() throws {
        let source = T.ramp(size: 256)
        var params = FilterParams.neutral; params.contrast = 1
        #expect(try T.lumaStdDev(T.render(params, on: source)) > T.lumaStdDev(T.render(.neutral, on: source)) + 0.01)
    }

    @Test("vignette darkens the corners against the centre")
    func vignetteCorners() throws {
        let grey = T.flat(red: 0.5, green: 0.5, blue: 0.5, size: 128)
        var params = FilterParams.neutral; params.vignette = 1
        let out = try T.render(params, on: grey)
        let corner = T.luma(try T.means(out, x: 0..<8, y: 0..<8))
        let centre = T.luma(try T.means(out, x: 60..<68, y: 60..<68))
        #expect(corner < centre - 0.05)
    }

    @Test("grain adds noise to a flat picture, the same noise every time")
    func grainIsDeterministic() throws {
        let grey = T.flat(red: 0.5, green: 0.5, blue: 0.5, size: 128)
        var params = FilterParams.neutral; params.grain = 1
        let first = try T.render(params, on: grey)
        #expect(try T.lumaStdDev(first) > 0.01)
        #expect(try T.bytes(first) == T.bytes(T.render(params, on: grey)))
    }

    @Test("refocus without a subject mask does nothing")
    func refocusNeedsAMask() throws {
        let source = T.ramp(size: 128)
        var params = FilterParams.neutral; params.refocus = 1
        #expect(try T.bytes(T.render(params, on: source)) == T.bytes(T.render(.neutral, on: source)))
    }

    @Test("auto-enhance with an empty chain does nothing")
    func emptyAutoEnhance() throws {
        let source = T.ramp(size: 128)
        var params = FilterParams.neutral; params.autoEnhance = true
        #expect(try T.bytes(T.render(params, on: source)) == T.bytes(T.render(.neutral, on: source)))
    }

    @Test("a 512 px and a 1024 px render match once downscaled")
    func resolutionIndependent() throws {
        var params = FilterParams.neutral
        params.exposure = 0.3
        params.sharpness = 0.5
        params.vignette = 0.5
        params.blurVignette = 0.5
        let small = try T.render(params, on: T.ramp(size: 512))
        let large = T.downscaled(try T.render(params, on: T.ramp(size: 1024)), to: 512)
        #expect(try T.meanAbsDifference(small, large) < 0.02)
    }
}
