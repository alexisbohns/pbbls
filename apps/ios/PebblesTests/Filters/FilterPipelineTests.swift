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

    @Test("a 512 px and a 1024 px render match once downscaled, and both differ from neutral")
    func resolutionIndependent() throws {
        var params = FilterParams.neutral
        params.exposure = 0.3
        params.sharpness = 0.5
        params.vignette = 0.5
        params.blurVignette = 1
        let source512 = T.stripes(size: 512)
        let small = try T.render(params, on: source512)
        let large = T.downscaled(try T.render(params, on: T.stripes(size: 1024)), to: 512)
        let difference = try T.meanAbsDifference(small, large)
        let effect = try T.meanAbsDifference(small, T.render(.neutral, on: source512))
        #expect(difference < 0.01) // measured 0.0046
        #expect(effect > 0.02)
    }

    // MARK: Masks

    @Test("refocus keeps the subject sharp and blurs the rest, with a one-channel Vision-style mask")
    func refocusPolarity() throws {
        let source = T.stripes(size: 256)
        var params = FilterParams.neutral; params.refocus = 1
        let mask = try T.subjectMask(width: 256, height: 256)
        let out = try T.render(params, on: source, masks: FilterMasks(subject: mask, autoEnhance: []))
        let kept = try T.lumaStdDev(out, x: 8..<112), lost = try T.lumaStdDev(out, x: 144..<248)
        let original = try T.lumaStdDev(source, x: 8..<112)
        #expect(kept > original * 0.8)
        #expect(lost < original * 0.5)
    }

    @Test("refocus lines up a mask built at another size")
    func refocusMaskAtOtherSize() throws {
        let source = T.stripes(size: 256)
        var params = FilterParams.neutral; params.refocus = 1
        let mask = try T.subjectMask(width: 128, height: 128)
        let out = try T.render(params, on: source, masks: FilterMasks(subject: mask, autoEnhance: []))
        let kept = try T.lumaStdDev(out, x: 8..<112), lost = try T.lumaStdDev(out, x: 144..<248)
        let original = try T.lumaStdDev(source, x: 8..<112)
        #expect(kept > original * 0.8)
        #expect(lost < original * 0.5)
    }

    @Test("blur vignette keeps the centre sharp and blurs the edges")
    func blurVignetteCentre() throws {
        let source = T.stripes(size: 256)
        var params = FilterParams.neutral; params.blurVignette = 1
        let out = try T.render(params, on: source)
        let original = try T.lumaStdDev(source, x: 8..<248, y: 118..<138)
        let centre = try T.lumaStdDev(out, x: 108..<148, y: 108..<148)
        let edge = try T.lumaStdDev(out, x: 0..<40, y: 0..<40)
        #expect(centre > original * 0.8)
        #expect(edge < original * 0.75) // radius 12 px at 1024 is soft against 32 px stripes
    }

    @Test("auto-enhance runs the photo's own chain, on a copy of each filter")
    func autoEnhanceRuns() throws {
        let grey = T.flat(red: 0.4, green: 0.4, blue: 0.4)
        let filter = CIFilter.exposureAdjust()
        filter.ev = 1
        var params = FilterParams.neutral; params.autoEnhance = true
        let masks = FilterMasks(subject: nil, autoEnhance: [filter])
        let base = T.luma(try T.means(T.render(.neutral, on: grey)))
        #expect(T.luma(try T.means(T.render(params, on: grey, masks: masks))) > base + 0.1)
        #expect(filter.value(forKey: kCIInputImageKey) == nil)
    }

    // MARK: Directions

    @Test("highlights down darkens a bright grey, up brightens it")
    func highlightsDirection() throws {
        let grey = T.flat(red: 0.85, green: 0.85, blue: 0.85)
        let base = T.luma(try T.means(T.render(.neutral, on: grey)))
        var down = FilterParams.neutral; down.highlights = -0.5
        var up = FilterParams.neutral; up.highlights = 0.5
        let d = T.luma(try T.means(T.render(down, on: grey))), u = T.luma(try T.means(T.render(up, on: grey)))
        #expect(d < base - 0.01)
        #expect(u > base + 0.01)
    }

    @Test("shadows up brightens a dark grey, down darkens it")
    func shadowsDirection() throws {
        let grey = T.flat(red: 0.15, green: 0.15, blue: 0.15)
        let base = T.luma(try T.means(T.render(.neutral, on: grey)))
        var up = FilterParams.neutral; up.shadows = 0.5
        var down = FilterParams.neutral; down.shadows = -0.5
        let u = T.luma(try T.means(T.render(up, on: grey))), d = T.luma(try T.means(T.render(down, on: grey)))
        #expect(u > base + 0.01)
        #expect(d < base - 0.01)
    }

    @Test("vibrance up raises chroma on a muted colour")
    func vibranceRaisesChroma() throws {
        let muted = T.flat(red: 0.55, green: 0.5, blue: 0.45)
        var params = FilterParams.neutral; params.vibrance = 1
        func chroma(_ rgb: [Double]) -> Double { (rgb.max() ?? 0) - (rgb.min() ?? 0) }
        let base = chroma(try T.means(T.render(.neutral, on: muted)))
        let out = chroma(try T.means(T.render(params, on: muted)))
        #expect(out > base + 0.01)
    }

    @Test("sharpness up raises local contrast on stripes")
    func sharpnessRaisesContrast() throws {
        let source = T.stripes(size: 256)
        var params = FilterParams.neutral; params.sharpness = 1
        let base = try T.lumaStdDev(T.render(.neutral, on: source))
        let out = try T.lumaStdDev(T.render(params, on: source))
        #expect(out > base + 0.005)
    }

    @Test("grain at full strength stays within a plausible band")
    func grainBand() throws {
        let grey = T.flat(red: 0.5, green: 0.5, blue: 0.5, size: 128)
        var params = FilterParams.neutral; params.grain = 1
        let deviation = try T.lumaStdDev(T.render(params, on: grey))
        #expect(deviation > 0.01 && deviation < 0.15)
    }
}
