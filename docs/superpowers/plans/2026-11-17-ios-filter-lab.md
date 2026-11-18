# iOS Filter Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A debug-only Filter Lab on iOS (Settings → Developer) where the maintainer imports photos through the crop editor, tunes and saves looks with thirteen Photos-style controls plus auto-enhance and refocus, and exports a zip for the preset analysis.

**Architecture:** A pure Core Image `FilterPipeline` (kept for the future Filters tab, compiles on macOS too) applies a `FilterParams` value with per-photo `FilterMasks`. `PhotoMeasures` records facts about each photo for the analysis. A `#if DEBUG` lab (store, screens, export) sits on top. Spec: `docs/superpowers/specs/2026-11-17-ios-filter-lab-design.md`. Issue #1675.

**Tech Stack:** SwiftUI (iOS 17), Core Image (`CIFilterBuiltins`), Vision, ImageIO, Swift Testing, XcodeGen.

---

## Before you start

- Work in `apps/ios`. Read `apps/ios/CLAUDE.md`. `project.yml` is the source of truth: after adding files, run `xcodegen generate` (the commands below do).
- Use a dedicated simulator and a private DerivedData so you never collide with another worktree:

```bash
xcrun simctl create "FilterLab iPhone 17" "iPhone 17"
```

- **Test command** (replace `<Suite>` with the test struct name):

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=FilterLab iPhone 17' -derivedDataPath build/dd-filterlab -only-testing:PebblesTests/<Suite> 2>&1 | tail -40
```

- If a build serves stale objects after `xcodegen`, delete `apps/ios/build/dd-filterlab/Build` and rerun.
- Debug strings use `Text(verbatim:)` so they never reach the string catalog. No colour literals and no `role: .destructive`: `PebblesTests/ThemeLiteralsTests.swift` scans `Features/` and fails on them. `CIColor.black`/`CIColor.white` are fine, but never write `CIColor(red:` (it matches the `Color(red` rule).
- Every error path logs with `os.Logger`. No empty `catch`.
- Commits: conventional, lowercase, ending with the `Co-Authored-By` line from the session.

## File map

**Part 1: pipeline (lasting code, all configurations)**
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterParams.swift`: the values of one look.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterMasks.swift`: per-photo subject mask and auto-enhance chain.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterPipeline.swift`: applies values to a `CIImage`, renders.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoMeasures.swift`: facts about one photo, plus `ToneStats`.
- Create tests in `apps/ios/PebblesTests/Filters/`: `FilterTestImages.swift`, `FilterParamsTests.swift`, `FilterPipelineTests.swift`, `PhotoMeasuresTests.swift`.

**Part 2: the lab (`#if DEBUG`)**
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabStore.swift`: document types and the on-disk session.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabExport.swift`: the zip.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterControl.swift`: slider descriptors.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookModel.swift`: editor state and coalesced preview.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookEditor.swift`: the tuning screen.
- Create `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabView.swift`: grid, import, export, share sheet.
- Modify `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`: Developer row + cover.
- Create tests `apps/ios/PebblesTests/Filters/FilterLabStoreTests.swift`, `FilterLabExportTests.swift`.

Part 1 ships as a PR from `feat/1675-ios-filter-lab` (this branch, which already holds the spec). Part 2 is stacked on it with `gh stack` as `feat/1675-ios-filter-lab-ui` (read the `gh-stack` skill first).

---

# Part 1 — the pipeline

### Task 1: FilterParams

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterParams.swift`
- Test: `apps/ios/PebblesTests/Filters/FilterParamsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("FilterParams")
struct FilterParamsTests {
    @Test("round-trips through JSON")
    func roundTrip() throws {
        var params = FilterParams()
        params.exposure = 0.4
        params.tint = -0.25
        params.grain = 0.2
        params.refocus = 0.5
        params.autoEnhance = true
        let data = try JSONEncoder().encode(params)
        #expect(try JSONDecoder().decode(FilterParams.self, from: data) == params)
    }

    @Test("a missing key decodes as neutral")
    func missingKeys() throws {
        let json = Data(#"{"exposure":0.3}"#.utf8)
        var expected = FilterParams.neutral
        expected.exposure = 0.3
        #expect(try JSONDecoder().decode(FilterParams.self, from: json) == expected)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run the test command with `<Suite>` = `FilterParamsTests`. Expected: build fails, `cannot find 'FilterParams' in scope`.

- [ ] **Step 3: Implement**

```swift
import Foundation

/// One look in the filter lab (spec 2026-11-17 "The controls"): thirteen
/// Photos-style controls plus two extras. Two-sided controls run −1…1,
/// one-sided ones (sharpness, blur vignette, grain, refocus) 0…1, and zero
/// everywhere is the identity.
struct FilterParams: Codable, Equatable, Sendable {
    var exposure: Double = 0
    var brilliance: Double = 0
    var highlights: Double = 0
    var shadows: Double = 0
    var contrast: Double = 0
    var saturation: Double = 0
    var vibrance: Double = 0
    var tone: Double = 0
    var tint: Double = 0
    var sharpness: Double = 0
    var vignette: Double = 0
    var blurVignette: Double = 0
    var grain: Double = 0
    var refocus: Double = 0
    var autoEnhance = false

    static let neutral = FilterParams()

    init() {}

    /// A missing key reads as neutral, so an export from an older lab still decodes.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func value(_ key: CodingKeys) throws -> Double { try c.decodeIfPresent(Double.self, forKey: key) ?? 0 }
        exposure = try value(.exposure)
        brilliance = try value(.brilliance)
        highlights = try value(.highlights)
        shadows = try value(.shadows)
        contrast = try value(.contrast)
        saturation = try value(.saturation)
        vibrance = try value(.vibrance)
        tone = try value(.tone)
        tint = try value(.tint)
        sharpness = try value(.sharpness)
        vignette = try value(.vignette)
        blurVignette = try value(.blurVignette)
        grain = try value(.grain)
        refocus = try value(.refocus)
        autoEnhance = try c.decodeIfPresent(Bool.self, forKey: .autoEnhance) ?? false
    }
}
```

- [ ] **Step 4: Run it and see it pass**

Same command. Expected: `FilterParams` suite passes, 2 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterParams.swift apps/ios/PebblesTests/Filters/FilterParamsTests.swift
git commit -m "feat(ios): add the filter lab's look values"
```

### Task 2: FilterMasks

No unit test: Vision masks are not reliable in the simulator. The pipeline tests (Task 3) cover the "no mask, no refocus" path, and the device pass (Task 12) covers real masks.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterMasks.swift`

- [ ] **Step 1: Implement**

```swift
import CoreImage
import Vision
import os

/// What one photo needs beyond its values (spec "The controls"), computed once per photo.
/// `@unchecked Sendable`: it crosses into detached renders, and `FilterPipeline`
/// copies each `CIFilter` before setting its input, so the shared instances are never mutated.
struct FilterMasks: @unchecked Sendable {
    /// White on the subject, black elsewhere, at the photo's extent. Nil when
    /// Vision found none, which disables Refocus for that photo.
    var subject: CIImage?
    /// Apple's suggested auto-enhance and red-eye chain for this photo, in order.
    var autoEnhance: [CIFilter]

    static let none = FilterMasks(subject: nil, autoEnhance: [])

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-masks")

    static func compute(for image: CGImage, faceCount: Int) -> FilterMasks {
        FilterMasks(subject: subjectMask(for: image, faceCount: faceCount),
                    autoEnhance: autoEnhanceFilters(for: image))
    }

    static func autoEnhanceFilters(for image: CGImage) -> [CIFilter] {
        CIImage(cgImage: image).autoAdjustmentFilters(options: [.enhance: true, .redEye: true])
    }

    /// Faces present → the person mask; otherwise the general foreground mask.
    static func subjectMask(for image: CGImage, faceCount: Int) -> CIImage? {
        let handler = VNImageRequestHandler(cgImage: image)
        let extent = CGRect(x: 0, y: 0, width: image.width, height: image.height)
        do {
            if faceCount > 0 {
                let request = VNGeneratePersonSegmentationRequest()
                request.qualityLevel = .accurate
                request.outputPixelFormat = kCVPixelFormatType_OneComponent8
                try handler.perform([request])
                guard let buffer = request.results?.first?.pixelBuffer else { return nil }
                return fitted(CIImage(cvPixelBuffer: buffer), to: extent)
            }
            let request = VNGenerateForegroundInstanceMaskRequest()
            try handler.perform([request])
            guard let result = request.results?.first else { return nil }
            let buffer = try result.generateScaledMaskForImage(forInstances: result.allInstances, from: handler)
            return fitted(CIImage(cvPixelBuffer: buffer), to: extent)
        } catch {
            logger.error("subject mask failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private static func fitted(_ mask: CIImage, to extent: CGRect) -> CIImage {
        let sx = extent.width / mask.extent.width
        let sy = extent.height / mask.extent.height
        return mask.transformed(by: CGAffineTransform(scaleX: sx, y: sy)).cropped(to: extent)
    }
}
```

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=FilterLab iPhone 17' -derivedDataPath build/dd-filterlab 2>&1 | tail -5
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterMasks.swift
git commit -m "feat(ios): compute a photo's subject mask and auto-enhance chain"
```

### Task 3: FilterPipeline

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterPipeline.swift`
- Create: `apps/ios/PebblesTests/Filters/FilterTestImages.swift`
- Test: `apps/ios/PebblesTests/Filters/FilterPipelineTests.swift`

- [ ] **Step 1: Write the test helpers**

```swift
import CoreGraphics
import CoreImage
import Testing
@testable import Pebbles

/// Synthetic pictures and pixel statistics for the filter tests.
enum FilterTestImages {
    static let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!

    static func context(width: Int, height: Int) -> CGContext {
        CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                  space: sRGB, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    }

    /// A diagonal colour ramp with a white square, the same picture at any size.
    static func ramp(size: Int) -> CGImage {
        let ctx = context(width: size, height: size)
        let colours = [CGColor(red: 0.15, green: 0.25, blue: 0.35, alpha: 1),
                       CGColor(red: 0.85, green: 0.75, blue: 0.65, alpha: 1)] as CFArray
        let gradient = CGGradient(colorsSpace: sRGB, colors: colours, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size, y: size), options: [])
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        let s = CGFloat(size)
        ctx.fill(CGRect(x: s * 0.4, y: s * 0.4, width: s * 0.2, height: s * 0.2))
        return ctx.makeImage()!
    }

    static func flat(red: CGFloat, green: CGFloat, blue: CGFloat, size: Int = 64) -> CGImage {
        let ctx = context(width: size, height: size)
        ctx.setFillColor(CGColor(red: red, green: green, blue: blue, alpha: 1))
        ctx.fill(CGRect(x: 0, y: 0, width: size, height: size))
        return ctx.makeImage()!
    }

    /// 256 × 4, column x is grey x/255.
    static func greyRamp() -> CGImage {
        let ctx = context(width: 256, height: 4)
        for x in 0..<256 {
            let v = CGFloat(x) / 255
            ctx.setFillColor(CGColor(red: v, green: v, blue: v, alpha: 1))
            ctx.fill(CGRect(x: x, y: 0, width: 1, height: 4))
        }
        return ctx.makeImage()!
    }

    static func render(_ params: FilterParams, on image: CGImage, masks: FilterMasks = .none) throws -> CGImage {
        let output = FilterPipeline.apply(params, to: CIImage(cgImage: image), masks: masks)
        return try #require(FilterPipeline.render(output, context: FilterPipeline.sharedContext, colorSpace: sRGB))
    }

    static func downscaled(_ image: CGImage, to size: Int) -> CGImage {
        let ctx = context(width: size, height: size)
        ctx.interpolationQuality = .high
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
        return ctx.makeImage()!
    }

    static func bytes(_ image: CGImage) throws -> [UInt8] {
        try #require(ReferenceImage.rgba(image))
    }

    /// Mean red, green, blue (0…1) over a region given y-down from the top-left.
    static func means(_ image: CGImage, x: Range<Int>? = nil, y: Range<Int>? = nil) throws -> [Double] {
        let rgba = try bytes(image)
        let xs = x ?? 0..<image.width, ys = y ?? 0..<image.height
        var sums = [0.0, 0.0, 0.0]
        for row in ys {
            for col in xs {
                let i = (row * image.width + col) * 4
                for c in 0..<3 { sums[c] += Double(rgba[i + c]) / 255 }
            }
        }
        let n = Double(xs.count * ys.count)
        return sums.map { $0 / n }
    }

    static func luma(_ rgb: [Double]) -> Double { 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2] }

    static func lumaStdDev(_ image: CGImage) throws -> Double {
        let rgba = try bytes(image)
        let lumas = stride(from: 0, to: rgba.count, by: 4).map { i in
            luma([Double(rgba[i]) / 255, Double(rgba[i + 1]) / 255, Double(rgba[i + 2]) / 255])
        }
        let mean = lumas.reduce(0, +) / Double(lumas.count)
        return (lumas.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(lumas.count)).squareRoot()
    }

    static func meanAbsDifference(_ a: CGImage, _ b: CGImage) throws -> Double {
        let x = try bytes(a), y = try bytes(b)
        try #require(x.count == y.count)
        let total = zip(x, y).reduce(0.0) { $0 + abs(Double($1.0) - Double($1.1)) }
        return total / Double(x.count) / 255
    }
}
```

- [ ] **Step 2: Write the failing tests**

```swift
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
```

- [ ] **Step 3: Run them and see them fail**

Run the test command with `<Suite>` = `FilterPipelineTests`. Expected: build fails, `cannot find 'FilterPipeline' in scope`.

- [ ] **Step 4: Implement**

```swift
import CoreImage
import CoreImage.CIFilterBuiltins

/// The filter lab's pipeline (spec 2026-11-17 "The controls"), kept for the
/// future Filters tab. Pure: no UIKit, no logging, so it also compiles on macOS
/// for the analysis. Each step returns its input untouched at zero, so neutral
/// values are an exact identity. The order is fixed.
enum FilterPipeline {
    /// Pixel radii are authored for a 1024 px long edge and scale with the photo.
    static let referenceEdge: CGFloat = 1024

    /// One context for the app's lifetime: creating one per render is the expensive part.
    static let sharedContext = CIContext()

    static func apply(_ p: FilterParams, to input: CIImage, masks: FilterMasks = .none) -> CIImage {
        let extent = input.extent
        let unit = max(extent.width, extent.height) / referenceEdge
        var image = input
        if p.autoEnhance { image = autoEnhanced(image, masks.autoEnhance) }
        image = exposure(image, p.exposure)
        image = brilliance(image, p.brilliance)
        image = highlights(image, p.highlights)
        image = shadows(image, p.shadows)
        image = contrastSaturation(image, contrast: p.contrast, saturation: p.saturation)
        image = vibrance(image, p.vibrance)
        image = toneTint(image, tone: p.tone, tint: p.tint)
        image = sharpness(image, p.sharpness, unit: unit)
        image = refocus(image, p.refocus, mask: masks.subject, unit: unit)
        image = vignette(image, p.vignette, extent: extent)
        image = blurVignette(image, p.blurVignette, extent: extent, unit: unit)
        image = grain(image, p.grain, extent: extent, unit: unit)
        return image.cropped(to: extent)
    }

    /// Renders in `colorSpace`: pass the photo's own, so a Display P3 photo stays P3.
    static func render(_ image: CIImage, context: CIContext, colorSpace: CGColorSpace) -> CGImage? {
        context.createCGImage(image, from: image.extent, format: .RGBA8, colorSpace: colorSpace)
    }

    static func autoEnhanced(_ image: CIImage, _ filters: [CIFilter]) -> CIImage {
        filters.reduce(image) { current, filter in
            // Copied: the same chain serves the preview and the export on different threads.
            guard let step = filter.copy() as? CIFilter else { return current }
            step.setValue(current, forKey: kCIInputImageKey)
            return step.outputImage ?? current
        }
    }

    static func exposure(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.exposureAdjust()
        f.inputImage = image
        f.ev = Float(2 * v)
        return f.outputImage ?? image
    }

    /// Photos' Brilliance is unpublished: this lifts the shadows, pulls the
    /// highlights and steepens the midtones.
    static func brilliance(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.highlightShadowAdjust()
        f.inputImage = image
        f.shadowAmount = Float(0.6 * v)
        f.highlightAmount = Float(1 - 0.4 * max(v, 0))
        return curve(f.outputImage ?? image, quarter: 0.25 - 0.03 * v, half: 0.5, threeQuarter: 0.75 + 0.03 * v)
    }

    static func highlights(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        guard v > 0 else {
            let f = CIFilter.highlightShadowAdjust()
            f.inputImage = image
            f.highlightAmount = Float(1 + v)
            return f.outputImage ?? image
        }
        return curve(image, quarter: 0.25, half: 0.5 + 0.02 * v, threeQuarter: 0.75 + 0.1 * v)
    }

    static func shadows(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.highlightShadowAdjust()
        f.inputImage = image
        f.shadowAmount = Float(v)
        return f.outputImage ?? image
    }

    static func contrastSaturation(_ image: CIImage, contrast: Double, saturation: Double) -> CIImage {
        guard contrast != 0 || saturation != 0 else { return image }
        let f = CIFilter.colorControls()
        f.inputImage = image
        f.contrast = Float(1 + 0.5 * contrast)
        f.saturation = Float(1 + saturation)
        f.brightness = 0
        return f.outputImage ?? image
    }

    static func vibrance(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.vibrance()
        f.inputImage = image
        f.amount = Float(v)
        return f.outputImage ?? image
    }

    /// Tone > 0 is warmer, tint > 0 is magenta. Checked on Core Image 2026-11-17:
    /// a lower target temperature warms the picture, a positive target tint greens it.
    static func toneTint(_ image: CIImage, tone: Double, tint: Double) -> CIImage {
        guard tone != 0 || tint != 0 else { return image }
        let f = CIFilter.temperatureAndTint()
        f.inputImage = image
        f.neutral = CIVector(x: 6500, y: 0)
        f.targetNeutral = CIVector(x: 6500 - 2000 * tone, y: -30 * tint)
        return f.outputImage ?? image
    }

    static func sharpness(_ image: CIImage, _ v: Double, unit: CGFloat) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.sharpenLuminance()
        f.inputImage = image.clampedToExtent()
        f.sharpness = Float(0.8 * v)
        f.radius = Float(1.5 * unit)
        return (f.outputImage ?? image).cropped(to: image.extent)
    }

    /// Blurs everything but the subject. No mask, no refocus.
    static func refocus(_ image: CIImage, _ v: Double, mask: CIImage?, unit: CGFloat) -> CIImage {
        guard v != 0, let mask else { return image }
        return maskedBlur(image, mask: mask.applyingFilter("CIColorInvert"), radius: 20 * v * unit)
    }

    static func vignette(_ image: CIImage, _ v: Double, extent: CGRect) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.vignetteEffect()
        f.inputImage = image
        f.center = CGPoint(x: extent.midX, y: extent.midY)
        f.radius = Float(hypot(extent.width, extent.height) / 2 * 0.7)
        f.intensity = Float(v)
        f.falloff = 0.5
        return f.outputImage ?? image
    }

    static func blurVignette(_ image: CIImage, _ v: Double, extent: CGRect, unit: CGFloat) -> CIImage {
        guard v != 0 else { return image }
        let g = CIFilter.radialGradient()
        g.center = CGPoint(x: extent.midX, y: extent.midY)
        g.radius0 = Float(min(extent.width, extent.height) * 0.35)
        g.radius1 = Float(hypot(extent.width, extent.height) / 2)
        g.color0 = CIColor.black
        g.color1 = CIColor.white
        guard let mask = g.outputImage?.cropped(to: extent) else { return image }
        return maskedBlur(image, mask: mask, radius: 12 * v * unit)
    }

    /// Luminance-only noise, soft-light blended: 0.5 grey is neutral in soft light,
    /// so the strength scales the noise around it. The generator is deterministic.
    static func grain(_ image: CIImage, _ v: Double, extent: CGRect, unit: CGFloat) -> CIImage {
        guard v != 0, let random = CIFilter.randomGenerator().outputImage else { return image }
        let k = CGFloat(0.25 * v), bias = 0.5 - 0.5 * k
        let grey = random.applyingFilter("CIColorMatrix", parameters: [
            "inputRVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputGVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputBVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 0),
            "inputBiasVector": CIVector(x: bias, y: bias, z: bias, w: 1)
        ])
        .transformed(by: CGAffineTransform(scaleX: unit, y: unit))
        .cropped(to: extent)
        let f = CIFilter.softLightBlendMode()
        f.inputImage = grey
        f.backgroundImage = image
        return f.outputImage ?? image
    }

    private static func curve(_ image: CIImage, quarter: Double, half: Double, threeQuarter: Double) -> CIImage {
        let f = CIFilter.toneCurve()
        f.inputImage = image
        f.point0 = .zero
        f.point1 = CGPoint(x: 0.25, y: quarter)
        f.point2 = CGPoint(x: 0.5, y: half)
        f.point3 = CGPoint(x: 0.75, y: threeQuarter)
        f.point4 = CGPoint(x: 1, y: 1)
        return f.outputImage ?? image
    }

    private static func maskedBlur(_ image: CIImage, mask: CIImage, radius: CGFloat) -> CIImage {
        let f = CIFilter.maskedVariableBlur()
        f.inputImage = image.clampedToExtent()
        f.mask = mask.clampedToExtent()
        f.radius = Float(radius)
        return (f.outputImage ?? image).cropped(to: image.extent)
    }
}
```

- [ ] **Step 5: Run them and see them pass**

Same command. Expected: 11 tests pass. If `resolutionIndependent` fails narrowly, print the difference and adjust the radius scaling. Never loosen the 0.02 bound without saying why in the test. If a direction test fails, the mapping is wrong: fix the mapping, not the test.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterPipeline.swift apps/ios/PebblesTests/Filters/FilterTestImages.swift apps/ios/PebblesTests/Filters/FilterPipelineTests.swift
git commit -m "feat(ios): add the core image pipeline behind the filter lab"
```

### Task 4: PhotoMeasures

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoMeasures.swift`
- Test: `apps/ios/PebblesTests/Filters/PhotoMeasuresTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
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
```

- [ ] **Step 2: Run them and see them fail**

Run the test command with `<Suite>` = `PhotoMeasuresTests`. Expected: build fails, `cannot find 'ToneStats' in scope`.

- [ ] **Step 3: Implement**

```swift
import CoreImage
import ImageIO
import Vision
import os

/// Facts about one photo for the preset analysis (spec "Architecture"),
/// computed once at import and exported with the looks.
struct PhotoMeasures: Codable, Equatable, Sendable {
    struct Scene: Codable, Equatable, Sendable {
        var identifier: String
        var confidence: Double
    }

    /// One filter of Apple's auto-enhance chain: its name and numeric inputs
    /// (a number as one value, a vector as its components).
    struct AutoEnhanceStep: Codable, Equatable, Sendable {
        var filter: String
        var values: [String: [Double]]
    }

    /// Luma at the 5th, 25th, 50th, 75th and 95th percentiles, 0…1, sRGB-encoded.
    var luminance: [Double]
    var meanSaturation: Double
    /// Mean red − green, and mean (red + green) / 2 − blue, −1…1.
    var castRedGreen: Double
    var castYellowBlue: Double
    var faceCount: Int
    /// Vision's top five scene tags.
    var scenes: [Scene]
    var hasDepth: Bool
    /// Vision's overall aesthetics score, −1…1; nil before iOS 18.
    var aesthetics: Double?
    var autoEnhance: [AutoEnhanceStep]
}

/// The tone half of the measures, pure so it can be tested on synthetic pictures.
struct ToneStats: Equatable {
    var luminance: [Double]
    var meanSaturation: Double
    var castRedGreen: Double
    var castYellowBlue: Double

    static let empty = ToneStats(luminance: [0, 0, 0, 0, 0], meanSaturation: 0, castRedGreen: 0, castYellowBlue: 0)
}

extension ToneStats {
    /// Samples the photo at no more than 256 px on its long edge, in sRGB.
    init?(_ image: CGImage) {
        let scale = min(1, 256 / CGFloat(max(image.width, image.height)))
        let w = max(1, Int((CGFloat(image.width) * scale).rounded()))
        let h = max(1, Int((CGFloat(image.height) * scale).rounded()))
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        ctx.interpolationQuality = .medium
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        guard let base = ctx.data else { return nil }
        let px = base.bindMemory(to: UInt8.self, capacity: w * h * 4)
        var lumas: [Double] = []
        lumas.reserveCapacity(w * h)
        var saturation = 0.0, redGreen = 0.0, yellowBlue = 0.0
        for i in 0..<(w * h) {
            let r = Double(px[i * 4]) / 255, g = Double(px[i * 4 + 1]) / 255, b = Double(px[i * 4 + 2]) / 255
            lumas.append(0.2126 * r + 0.7152 * g + 0.0722 * b)
            let high = max(r, g, b), low = min(r, g, b)
            saturation += high > 0 ? (high - low) / high : 0
            redGreen += r - g
            yellowBlue += (r + g) / 2 - b
        }
        lumas.sort()
        let n = Double(lumas.count)
        self.init(luminance: [0.05, 0.25, 0.5, 0.75, 0.95].map { lumas[min(lumas.count - 1, Int($0 * n))] },
                  meanSaturation: saturation / n, castRedGreen: redGreen / n, castYellowBlue: yellowBlue / n)
    }
}

extension PhotoMeasures {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-measures")

    /// Everything but the face count and depth flag, which the caller already has.
    /// Runs Vision: call it off the main actor.
    static func measure(_ image: CGImage, faceCount: Int, hasDepth: Bool, autoEnhance: [CIFilter]) -> PhotoMeasures {
        let tone: ToneStats
        if let stats = ToneStats(image) {
            tone = stats
        } else {
            logger.error("tone stats unavailable for a \(image.width)×\(image.height) photo")
            tone = .empty
        }
        return PhotoMeasures(luminance: tone.luminance, meanSaturation: tone.meanSaturation,
                             castRedGreen: tone.castRedGreen, castYellowBlue: tone.castYellowBlue,
                             faceCount: faceCount, scenes: scenes(in: image), hasDepth: hasDepth,
                             aesthetics: aesthetics(of: image), autoEnhance: autoEnhance.map(step))
    }

    static func faceCount(in image: CGImage) -> Int {
        let request = VNDetectFaceRectanglesRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("face detection failed: \(error.localizedDescription, privacy: .public)")
            return 0
        }
        return request.results?.count ?? 0
    }

    static func scenes(in image: CGImage) -> [Scene] {
        let request = VNClassifyImageRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("scene classification failed: \(error.localizedDescription, privacy: .public)")
            return []
        }
        return (request.results ?? [])
            .sorted { $0.confidence > $1.confidence }
            .prefix(5)
            .map { Scene(identifier: $0.identifier, confidence: Double($0.confidence)) }
    }

    static func aesthetics(of image: CGImage) -> Double? {
        guard #available(iOS 18.0, macOS 15.0, *) else { return nil }
        let request = VNCalculateImageAestheticsScoresRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("aesthetics score failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
        return request.results?.first.map { Double($0.overallScore) }
    }

    /// Read from the original bytes: the crop editor's working image carries no depth.
    static func hasDepth(_ data: Data) -> Bool {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return false }
        return [kCGImageAuxiliaryDataTypeDisparity, kCGImageAuxiliaryDataTypeDepth].contains { type in
            CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, type) != nil
        }
    }

    static func step(_ filter: CIFilter) -> AutoEnhanceStep {
        var values: [String: [Double]] = [:]
        for key in filter.inputKeys where key != kCIInputImageKey {
            switch filter.value(forKey: key) {
            case let number as NSNumber: values[key] = [number.doubleValue]
            case let vector as CIVector: values[key] = (0..<vector.count).map { Double(vector.value(at: $0)) }
            default: break
            }
        }
        return AutoEnhanceStep(filter: filter.name, values: values)
    }
}
```

- [ ] **Step 4: Run them and see them pass**

Same command. Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoMeasures.swift apps/ios/PebblesTests/Filters/PhotoMeasuresTests.swift
git commit -m "feat(ios): measure a photo's tone, faces, scenes and auto-enhance"
```

### Task 5: Part 1 gate and PR

- [ ] **Step 1: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: no errors, and no new warnings in `Features/PebbleMedia/Filters/` or `PebblesTests/Filters/`.

- [ ] **Step 2: Full test suite**

Run the test command without `-only-testing`. On this Mac about 27 reference-image tests already fail on an untouched `main`. Compare the list of failing tests with a run on `main` (same simulator, separate `-derivedDataPath build/dd-main`). The gate is: no failure that `main` does not also have. `ThemeLiteralsTests` must pass.

- [ ] **Step 3: Push and open the PR** (ask the maintainer for the milestone first)

Title: `feat(ios): add the filter pipeline and photo measures for the filter lab`. Body starts with `Part of #1675` (Part 2 closes it), lists the four files and the spec, and says `Data safety: no change`. Labels `feat`, `ios`.

---

# Part 2 — the lab

Create the Part 2 branch on top of Part 1 with `gh stack` (read the `gh-stack` skill): `feat/1675-ios-filter-lab-ui`. Every file in `Filters/Lab/` is wrapped in `#if DEBUG` … `#endif`.

### Task 6: FilterLabStore

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabStore.swift`
- Test: `apps/ios/PebblesTests/Filters/FilterLabStoreTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
#if DEBUG
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("FilterLabStore")
@MainActor
struct FilterLabStoreTests {
    static func tempRoot() -> URL {
        FileManager.default.temporaryDirectory.appending(path: "filter-lab-tests/\(UUID().uuidString)")
    }

    static let measures = PhotoMeasures(
        luminance: [0.1, 0.3, 0.5, 0.7, 0.9], meanSaturation: 0.2, castRedGreen: 0, castYellowBlue: 0,
        faceCount: 1, scenes: [.init(identifier: "beach", confidence: 0.8)], hasDepth: false,
        aesthetics: nil, autoEnhance: [])

    @Test("a photo and a look survive a reload")
    func reload() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(FilterTestImages.ramp(size: 64), measures: Self.measures)
        var params = FilterParams.neutral
        params.exposure = 0.2
        let look = LabLook(id: UUID(), name: "warm", createdAt: LabLook.timestamp(), params: params, aesthetics: 0.1)
        try store.save(look, in: photo.id)

        let reloaded = FilterLabStore(root: root)
        #expect(reloaded.document == store.document)
        #expect(reloaded.measures.measures[photo.id.uuidString] == Self.measures)
        #expect(reloaded.image(for: photo)?.width == 64)
    }

    @Test("saving a look with a known id replaces it")
    func update() throws {
        let store = FilterLabStore(root: Self.tempRoot())
        let photo = try store.add(FilterTestImages.ramp(size: 32), measures: Self.measures)
        var look = LabLook(id: UUID(), name: "a", createdAt: LabLook.timestamp(), params: .neutral, aesthetics: nil)
        try store.save(look, in: photo.id)
        look.name = "b"
        try store.save(look, in: photo.id)
        #expect(store.document.photos[0].looks.map(\.name) == ["b"])
    }

    @Test("deleting a photo removes its file and measures")
    func deletePhoto() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(FilterTestImages.ramp(size: 32), measures: Self.measures)
        try store.deletePhoto(photo.id)
        #expect(store.document.photos.isEmpty)
        #expect(store.measures.measures.isEmpty)
        #expect(!FileManager.default.fileExists(atPath: root.appending(path: photo.file).path()))
    }
}
#endif
```

- [ ] **Step 2: Run them and see them fail**

Run the test command with `<Suite>` = `FilterLabStoreTests`. Expected: build fails, `cannot find 'FilterLabStore' in scope`.

- [ ] **Step 3: Implement**

```swift
#if DEBUG
import CoreGraphics
import Foundation
import ImageIO
import Observation
import UniformTypeIdentifiers
import os

struct LabLook: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    var name: String
    var createdAt: Date
    var params: FilterParams
    /// Vision's aesthetics score of this look's render; nil before iOS 18.
    var aesthetics: Double?

    /// Whole seconds: the narrowest precision every reader accepts, so a reload compares equal.
    static func timestamp(_ date: Date = .now) -> Date {
        Date(timeIntervalSince1970: date.timeIntervalSince1970.rounded(.down))
    }
}

struct LabPhoto: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    var file: String
    var width: Int
    var height: Int
    var looks: [LabLook]
}

/// `looks.json` (spec "The export").
struct LabDocument: Codable, Equatable, Sendable {
    var version = 1
    var photos: [LabPhoto] = []
}

/// `measures.json`, keyed by photo id.
struct LabMeasuresDocument: Codable, Equatable, Sendable {
    var version = 1
    var measures: [String: PhotoMeasures] = [:]
}

enum FilterLabError: Error {
    case writeFailed(String)
    case renderFailed(String)
    case zipFailed
}

/// The lab's one session on disk: `photos/<id>.jpg`, `looks.json`, `measures.json`.
/// Never leaves the device except through the export.
@MainActor
@Observable
final class FilterLabStore {
    private(set) var document = LabDocument()
    private(set) var measures = LabMeasuresDocument()
    let root: URL

    nonisolated private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-store")

    nonisolated static var defaultRoot: URL {
        URL.documentsDirectory.appending(path: "FilterLab/current", directoryHint: .isDirectory)
    }

    init(root: URL = FilterLabStore.defaultRoot) {
        self.root = root
        document = Self.read(LabDocument.self, at: root.appending(path: "looks.json")) ?? LabDocument()
        measures = Self.read(LabMeasuresDocument.self, at: root.appending(path: "measures.json")) ?? LabMeasuresDocument()
    }

    func add(_ image: CGImage, measures photoMeasures: PhotoMeasures) throws -> LabPhoto {
        let id = UUID()
        let file = "photos/\(id.uuidString).jpg"
        try FileManager.default.createDirectory(at: root.appending(path: "photos"), withIntermediateDirectories: true)
        try Self.writeJPEG(image, to: root.appending(path: file))
        let photo = LabPhoto(id: id, file: file, width: image.width, height: image.height, looks: [])
        document.photos.append(photo)
        measures.measures[id.uuidString] = photoMeasures
        try persist()
        return photo
    }

    func image(for photo: LabPhoto) -> CGImage? {
        Self.loadImage(at: root.appending(path: photo.file))
    }

    func looks(of photoID: UUID) -> [LabLook] {
        document.photos.first { $0.id == photoID }?.looks ?? []
    }

    /// Inserts the look, or replaces the one with the same id.
    func save(_ look: LabLook, in photoID: UUID) throws {
        guard let p = document.photos.firstIndex(where: { $0.id == photoID }) else { return }
        if let l = document.photos[p].looks.firstIndex(where: { $0.id == look.id }) {
            document.photos[p].looks[l] = look
        } else {
            document.photos[p].looks.append(look)
        }
        try persist()
    }

    func deleteLook(_ lookID: UUID, in photoID: UUID) throws {
        guard let p = document.photos.firstIndex(where: { $0.id == photoID }) else { return }
        document.photos[p].looks.removeAll { $0.id == lookID }
        try persist()
    }

    func deletePhoto(_ photoID: UUID) throws {
        guard let photo = document.photos.first(where: { $0.id == photoID }) else { return }
        try FileManager.default.removeItem(at: root.appending(path: photo.file))
        document.photos.removeAll { $0.id == photoID }
        measures.measures[photoID.uuidString] = nil
        try persist()
    }

    /// Deletes the whole session.
    func reset() throws {
        if FileManager.default.fileExists(atPath: root.path()) {
            try FileManager.default.removeItem(at: root)
        }
        document = LabDocument()
        measures = LabMeasuresDocument()
    }

    private func persist() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let encoder = Self.makeEncoder()
        try encoder.encode(document).write(to: root.appending(path: "looks.json"), options: .atomic)
        try encoder.encode(measures).write(to: root.appending(path: "measures.json"), options: .atomic)
    }

    nonisolated static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    nonisolated static func read<T: Decodable>(_ type: T.Type, at url: URL) -> T? {
        guard FileManager.default.fileExists(atPath: url.path()) else { return nil }
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(type, from: Data(contentsOf: url))
        } catch {
            logger.error("lab file unreadable \(url.lastPathComponent, privacy: .public): \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    nonisolated static func loadImage(at url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            logger.error("lab photo unreadable: \(url.lastPathComponent, privacy: .public)")
            return nil
        }
        return image
    }

    nonisolated static func writeJPEG(_ image: CGImage, to url: URL, quality: CGFloat = 0.92) throws {
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString,
                                                                1, nil) else {
            throw FilterLabError.writeFailed(url.lastPathComponent)
        }
        CGImageDestinationAddImage(destination, image,
                                   [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else { throw FilterLabError.writeFailed(url.lastPathComponent) }
    }
}
#endif
```

- [ ] **Step 4: Run them and see them pass**

Same command. Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabStore.swift apps/ios/PebblesTests/Filters/FilterLabStoreTests.swift
git commit -m "feat(ios): keep the filter lab's session on the device"
```

### Task 7: FilterLabExport

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabExport.swift`
- Test: `apps/ios/PebblesTests/Filters/FilterLabExportTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
#if DEBUG
import Foundation
import Testing
@testable import Pebbles

@Suite("FilterLabExport")
@MainActor
struct FilterLabExportTests {
    @Test("the zip holds the inputs, both documents and one render per look")
    func bundle() throws {
        let store = FilterLabStore(root: FilterLabStoreTests.tempRoot())
        let photo = try store.add(FilterTestImages.ramp(size: 64), measures: FilterLabStoreTests.measures)
        var params = FilterParams.neutral
        params.grain = 0.5
        let look = LabLook(id: UUID(), name: "grainy", createdAt: LabLook.timestamp(), params: params, aesthetics: nil)
        try store.save(look, in: photo.id)

        let date = try #require(ISO8601DateFormatter().date(from: "2026-11-17T12:00:00Z"))
        let zip = try FilterLabExport.makeZip(document: store.document, measures: store.measures,
                                              root: store.root, date: date)
        #expect(zip.lastPathComponent == "filter-lab-2026-11-17.zip")
        let size = try #require(try FileManager.default.attributesOfItem(atPath: zip.path())[.size] as? Int)
        #expect(size > 0)

        let staging = zip.deletingPathExtension()
        for path in ["looks.json", "measures.json", photo.file,
                     "renders/\(photo.id.uuidString)/\(look.id.uuidString).jpg"] {
            #expect(FileManager.default.fileExists(atPath: staging.appending(path: path).path()), "missing \(path)")
        }
    }
}
#endif
```

- [ ] **Step 2: Run it and see it fail**

Run the test command with `<Suite>` = `FilterLabExportTests`. Expected: build fails, `cannot find 'FilterLabExport' in scope`.

- [ ] **Step 3: Implement**

```swift
#if DEBUG
import CoreImage
import Foundation

/// Builds `filter-lab-<yyyy-MM-dd>.zip` (spec "The export"): the inputs,
/// `looks.json`, `measures.json`, and each look as this phone renders it.
/// It renders every look: call it off the main actor.
enum FilterLabExport {
    static func makeZip(document: LabDocument, measures: LabMeasuresDocument, root: URL,
                        date: Date = .now) throws -> URL {
        let fm = FileManager.default
        let staging = fm.temporaryDirectory
            .appending(path: UUID().uuidString)
            .appending(path: "filter-lab-\(dayString(date))")
        try fm.createDirectory(at: staging.appending(path: "photos"), withIntermediateDirectories: true)
        let encoder = FilterLabStore.makeEncoder()
        try encoder.encode(document).write(to: staging.appending(path: "looks.json"))
        try encoder.encode(measures).write(to: staging.appending(path: "measures.json"))

        for photo in document.photos {
            let source = root.appending(path: photo.file)
            try fm.copyItem(at: source, to: staging.appending(path: photo.file))
            try renderLooks(of: photo, source: source, faceCount: measures.measures[photo.id.uuidString]?.faceCount ?? 0,
                            into: staging.appending(path: "renders/\(photo.id.uuidString)"))
        }
        return try zip(staging)
    }

    private static func renderLooks(of photo: LabPhoto, source: URL, faceCount: Int, into directory: URL) throws {
        guard !photo.looks.isEmpty else { return }
        guard let image = FilterLabStore.loadImage(at: source),
              let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) else {
            throw FilterLabError.renderFailed(photo.file)
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        // Vision only when a look needs it.
        let needsMasks = photo.looks.contains { $0.params.refocus != 0 || $0.params.autoEnhance }
        let masks = needsMasks ? FilterMasks.compute(for: image, faceCount: faceCount) : .none
        let input = CIImage(cgImage: image)
        for look in photo.looks {
            let output = FilterPipeline.apply(look.params, to: input, masks: masks)
            guard let rendered = FilterPipeline.render(output, context: FilterPipeline.sharedContext,
                                                       colorSpace: space) else {
                throw FilterLabError.renderFailed("\(photo.file) / \(look.name)")
            }
            try FilterLabStore.writeJPEG(rendered, to: directory.appending(path: "\(look.id.uuidString).jpg"))
        }
    }

    /// A coordinated read with `.forUploading` hands back a zip of a directory,
    /// with no dependency. The system deletes its copy on return, so it is copied out.
    static func zip(_ directory: URL) throws -> URL {
        let destination = directory.deletingLastPathComponent()
            .appending(path: directory.lastPathComponent + ".zip")
        var result: Result<URL, Error> = .failure(FilterLabError.zipFailed)
        var coordinationError: NSError?
        NSFileCoordinator().coordinate(readingItemAt: directory, options: .forUploading,
                                       error: &coordinationError) { zipped in
            do {
                try FileManager.default.copyItem(at: zipped, to: destination)
                result = .success(destination)
            } catch {
                result = .failure(error)
            }
        }
        if let coordinationError { throw coordinationError }
        return try result.get()
    }

    static func dayString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}
#endif
```

- [ ] **Step 4: Run it and see it pass**

Same command. Expected: 1 test passes.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabExport.swift apps/ios/PebblesTests/Filters/FilterLabExportTests.swift
git commit -m "feat(ios): export the filter lab session as a zip"
```

### Task 8: FilterControl

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterControl.swift`

- [ ] **Step 1: Implement**

```swift
#if DEBUG
import Foundation

/// The lab's sliders, in the pipeline's order. Debug labels, never localized.
enum FilterControl: String, CaseIterable, Identifiable {
    case exposure, brilliance, highlights, shadows, contrast, saturation, vibrance
    case tone, tint, sharpness, refocus, vignette, blurVignette, grain

    var id: String { rawValue }

    var label: String {
        switch self {
        case .blurVignette: "Blur vignette"
        case .shadows: "Shadows (lowlight)"
        case .tone: "Tone (warmth)"
        default: rawValue.prefix(1).uppercased() + rawValue.dropFirst()
        }
    }

    var keyPath: WritableKeyPath<FilterParams, Double> {
        switch self {
        case .exposure: \.exposure
        case .brilliance: \.brilliance
        case .highlights: \.highlights
        case .shadows: \.shadows
        case .contrast: \.contrast
        case .saturation: \.saturation
        case .vibrance: \.vibrance
        case .tone: \.tone
        case .tint: \.tint
        case .sharpness: \.sharpness
        case .refocus: \.refocus
        case .vignette: \.vignette
        case .blurVignette: \.blurVignette
        case .grain: \.grain
        }
    }

    var range: ClosedRange<Double> {
        switch self {
        case .sharpness, .refocus, .blurVignette, .grain: 0...1
        default: -1...1
        }
    }
}
#endif
```

- [ ] **Step 2: Build** (the build command from Task 2). Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterControl.swift
git commit -m "feat(ios): describe the filter lab's sliders"
```

### Task 9: FilterLookModel

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookModel.swift`

- [ ] **Step 1: Implement**

```swift
#if DEBUG
import CoreImage
import Foundation
import Observation
import os

/// The tuning screen's state. Slider changes coalesce: at most one render runs,
/// and one more follows if the values moved meanwhile.
@MainActor
@Observable
final class FilterLookModel {
    let photo: LabPhoto
    let source: CGImage
    let faceCount: Int
    let aestheticsBefore: Double?

    var params = FilterParams.neutral {
        didSet { if params != oldValue { scheduleRender() } }
    }
    var showsOriginal = false
    var lookName = ""
    private(set) var editingLookID: UUID?
    private(set) var preview: CGImage?
    private(set) var masks = FilterMasks.none
    private(set) var masksReady = false
    private(set) var lastAesthetics: Double?
    private(set) var isSaving = false
    private(set) var errorMessage: String?

    private var rendering = false
    private var dirty = false
    private let colorSpace: CGColorSpace?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-editor")

    init(photo: LabPhoto, source: CGImage, faceCount: Int, aestheticsBefore: Double?) {
        self.photo = photo
        self.source = source
        self.faceCount = faceCount
        self.aestheticsBefore = aestheticsBefore
        colorSpace = source.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB)
        preview = source
    }

    var subjectFound: Bool { masks.subject != nil }

    func loadMasks() async {
        guard !masksReady else { return }
        let source = self.source, faces = faceCount
        masks = await Task.detached(priority: .userInitiated) {
            FilterMasks.compute(for: source, faceCount: faces)
        }.value
        masksReady = true
        if params.refocus != 0 || params.autoEnhance { scheduleRender() }
    }

    func load(_ look: LabLook) {
        params = look.params
        lookName = look.name
        editingLookID = look.id
        lastAesthetics = look.aesthetics
    }

    func duplicate(_ look: LabLook) {
        load(look)
        lookName = look.name + " copy"
        editingLookID = nil
    }

    func startNewLook() {
        params = .neutral
        lookName = ""
        editingLookID = nil
        lastAesthetics = nil
    }

    /// Saves as a new look, or updates the one being edited. Scores the render first.
    func save(into store: FilterLabStore, asNew: Bool) async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        let score = await renderOffMain().flatMap { PhotoMeasures.aesthetics(of: $0) }
        let existing = asNew ? nil : store.looks(of: photo.id).first { $0.id == editingLookID }
        let trimmed = lookName.trimmingCharacters(in: .whitespaces)
        let name = trimmed.isEmpty ? "Look \(store.looks(of: photo.id).count + 1)" : trimmed
        let look = LabLook(id: existing?.id ?? UUID(), name: name,
                           createdAt: existing?.createdAt ?? LabLook.timestamp(), params: params, aesthetics: score)
        do {
            try store.save(look, in: photo.id)
            editingLookID = look.id
            lookName = name
            lastAesthetics = score
            errorMessage = nil
        } catch {
            logger.error("look save failed: \(error.localizedDescription, privacy: .public)")
            errorMessage = "Could not save the look."
        }
    }

    func delete(_ look: LabLook, from store: FilterLabStore) {
        do {
            try store.deleteLook(look.id, in: photo.id)
            if editingLookID == look.id { editingLookID = nil }
        } catch {
            logger.error("look delete failed: \(error.localizedDescription, privacy: .public)")
            errorMessage = "Could not delete the look."
        }
    }

    private func scheduleRender() {
        guard !rendering else { dirty = true; return }
        rendering = true
        Task { await renderLoop() }
    }

    private func renderLoop() async {
        repeat {
            dirty = false
            if let image = await renderOffMain() {
                preview = image
            } else {
                logger.error("preview render failed")
            }
        } while dirty
        rendering = false
    }

    private func renderOffMain() async -> CGImage? {
        guard let space = colorSpace else { return nil }
        let params = self.params, masks = self.masks, source = self.source
        return await Task.detached(priority: .userInitiated) {
            FilterPipeline.render(FilterPipeline.apply(params, to: CIImage(cgImage: source), masks: masks),
                                  context: FilterPipeline.sharedContext, colorSpace: space)
        }.value
    }
}
#endif
```

- [ ] **Step 2: Build** (the build command from Task 2). Expected: `** BUILD SUCCEEDED **`. Concurrency warnings about `FilterMasks` crossing into `Task.detached` are acceptable in this Swift 5 target. Errors are not.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookModel.swift
git commit -m "feat(ios): add the filter lab editor's state and coalesced preview"
```

### Task 10: FilterLookEditor

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookEditor.swift`

- [ ] **Step 1: Implement**

```swift
#if DEBUG
import SwiftUI

/// Tune one photo: live preview (hold for the original), sliders, extras, saved looks.
struct FilterLookEditor: View {
    @State private var model: FilterLookModel
    let store: FilterLabStore

    init(photo: LabPhoto, source: CGImage, measures: PhotoMeasures?, store: FilterLabStore) {
        _model = State(initialValue: FilterLookModel(photo: photo, source: source,
                                                     faceCount: measures?.faceCount ?? 0,
                                                     aestheticsBefore: measures?.aesthetics))
        self.store = store
    }

    var body: some View {
        @Bindable var model = model
        VStack(spacing: 0) {
            preview
            List {
                Section { scores } header: { Text(verbatim: "Aesthetics") }
                Section {
                    ForEach(FilterControl.allCases) { slider(for: $0) }
                    Toggle(isOn: $model.params.autoEnhance) { Text(verbatim: "Auto-enhance + red-eye") }
                } header: {
                    Text(verbatim: "Controls (double-tap a name to reset it)")
                }
                Section { looksList } header: { Text(verbatim: "Looks") }
                Section { lookActions(name: $model.lookName) }
            }
        }
        .navigationTitle(Text(verbatim: "Tune"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.loadMasks() }
    }

    private var preview: some View {
        Image(decorative: model.showsOriginal ? model.source : (model.preview ?? model.source), scale: 1)
            .resizable()
            .scaledToFit()
            .frame(maxHeight: 340)
            .padding(.vertical, 8)
            .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, perform: {}, onPressingChanged: { pressing in
                model.showsOriginal = pressing
            })
            .accessibilityLabel(Text(verbatim: "Preview. Touch and hold to see the original."))
    }

    @ViewBuilder
    private var scores: some View {
        if model.aestheticsBefore == nil && model.lastAesthetics == nil {
            Text(verbatim: "Needs iOS 18.")
        } else {
            LabeledContent { Text(verbatim: format(model.aestheticsBefore)) } label: { Text(verbatim: "Original") }
            LabeledContent { Text(verbatim: format(model.lastAesthetics)) } label: { Text(verbatim: "This look (on save)") }
        }
    }

    private func slider(for control: FilterControl) -> some View {
        let value = Binding(get: { model.params[keyPath: control.keyPath] },
                            set: { model.params[keyPath: control.keyPath] = $0 })
        let blocked = control == .refocus && !model.subjectFound
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(verbatim: control.label)
                Spacer()
                Text(verbatim: String(format: "%+.2f", value.wrappedValue))
                    .monospacedDigit()
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .contentShape(Rectangle())
            .onTapGesture(count: 2) { value.wrappedValue = 0 }
            Slider(value: value, in: control.range)
                .disabled(blocked)
                .accessibilityLabel(Text(verbatim: control.label))
            if blocked {
                Text(verbatim: model.masksReady ? "No subject found in this photo." : "Finding the subject…")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }

    @ViewBuilder
    private var looksList: some View {
        let looks = store.looks(of: model.photo.id)
        if looks.isEmpty {
            Text(verbatim: "No looks saved yet.")
        }
        ForEach(looks) { look in
            Button {
                model.load(look)
            } label: {
                LabeledContent {
                    if look.id == model.editingLookID { Text(verbatim: "editing") }
                } label: {
                    Text(verbatim: "\(look.name) · \(format(look.aesthetics))")
                }
            }
            .swipeActions {
                Button { model.delete(look, from: store) } label: { Text(verbatim: "Delete") }
                Button { model.duplicate(look) } label: { Text(verbatim: "Duplicate") }
            }
        }
    }

    @ViewBuilder
    private func lookActions(name: Binding<String>) -> some View {
        TextField(text: name, prompt: Text(verbatim: "Look name")) { Text(verbatim: "Look name") }
        Button { Task { await model.save(into: store, asNew: true) } } label: { Text(verbatim: "Save as new look") }
            .disabled(model.isSaving)
        if model.editingLookID != nil {
            Button { Task { await model.save(into: store, asNew: false) } } label: { Text(verbatim: "Update this look") }
                .disabled(model.isSaving)
        }
        Button { model.startNewLook() } label: { Text(verbatim: "Start from neutral") }
        if let error = model.errorMessage {
            Text(verbatim: error).foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
    }

    private func format(_ score: Double?) -> String {
        score.map { String(format: "%+.2f", $0) } ?? "–"
    }
}
#endif
```

- [ ] **Step 2: Build** (the build command from Task 2). Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLookEditor.swift
git commit -m "feat(ios): add the filter lab's tuning screen"
```

### Task 11: FilterLabView and the Developer row

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` (state block around line 41, covers around line 192, `developerSection` around line 492)

- [ ] **Step 1: Implement the lab screen**

```swift
#if DEBUG
import CoreImage
import ImageIO
import PhotosUI
import SwiftUI
import UIKit
import os

/// The filter lab (#1675), Settings → Developer: import through the crop editor,
/// tune looks per photo, export the session for the preset analysis.
struct FilterLabView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var store = FilterLabStore()
    @State private var pickedItem: PhotosPickerItem?
    @State private var cropping: PendingCrop?
    @State private var exportFile: ExportFile?
    @State private var isWorking = false
    @State private var status: String?
    @State private var confirmsReset = false
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab")

    struct PendingCrop: Identifiable {
        let id = UUID()
        let source: CropSource
        let hasDepth: Bool
    }

    struct ExportFile: Identifiable {
        let id = UUID()
        let url: URL
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let status {
                    Text(verbatim: status).padding(.horizontal, 16)
                }
                if store.document.photos.isEmpty {
                    Text(verbatim: "Add photos with +. Each one opens the crop editor first.")
                        .padding(16)
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 8)], spacing: 8) {
                    ForEach(store.document.photos) { photo in
                        NavigationLink(value: photo.id) {
                            FilterLabThumbnail(url: store.root.appending(path: photo.file), looks: photo.looks.count)
                        }
                        .contextMenu {
                            Button { deletePhoto(photo) } label: { Text(verbatim: "Remove photo") }
                        }
                    }
                }
                .padding(16)
            }
            .navigationTitle(Text(verbatim: "Filter lab"))
            .navigationDestination(for: UUID.self) { destination($0) }
            .toolbar { toolbar }
            .overlay { if isWorking { ProgressView() } }
        }
        .onChange(of: pickedItem) { _, item in
            guard let item else { return }
            pickedItem = nil
            Task { await beginImport(item) }
        }
        .fullScreenCover(item: $cropping) { pending in
            PhotoCropEditor(source: pending.source,
                            onCancel: { cropping = nil },
                            onDone: { image, _ in
                                cropping = nil
                                Task { await finishImport(image, hasDepth: pending.hasDepth) }
                            })
        }
        .sheet(item: $exportFile) { FilterLabShareSheet(url: $0.url) }
        .confirmationDialog(Text(verbatim: "Delete every photo and look in the lab?"),
                            isPresented: $confirmsReset, titleVisibility: .visible) {
            Button { resetSession() } label: { Text(verbatim: "Start over") }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button { dismiss() } label: { Text(verbatim: "Close") }
        }
        ToolbarItemGroup(placement: .primaryAction) {
            PhotosPicker(selection: $pickedItem, matching: .images) {
                Image(systemName: "plus").accessibilityLabel(Text(verbatim: "Add a photo"))
            }
            Menu {
                Button { Task { await export() } } label: { Text(verbatim: "Export session") }
                    .disabled(store.document.photos.isEmpty)
                Button { confirmsReset = true } label: { Text(verbatim: "Start over…") }
            } label: {
                Image(systemName: "ellipsis.circle").accessibilityLabel(Text(verbatim: "More"))
            }
        }
    }

    @ViewBuilder
    private func destination(_ id: UUID) -> some View {
        if let photo = store.document.photos.first(where: { $0.id == id }), let image = store.image(for: photo) {
            FilterLookEditor(photo: photo, source: image, measures: store.measures.measures[id.uuidString], store: store)
        } else {
            Text(verbatim: "This photo is missing.")
        }
    }

    private func beginImport(_ item: PhotosPickerItem) async {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                status = "That photo carried no data."
                return
            }
            guard let source = await CropSource.make(from: data) else {
                status = "That photo's format is not supported."
                return
            }
            status = nil
            cropping = PendingCrop(source: source, hasDepth: PhotoMeasures.hasDepth(data))
        } catch {
            logger.error("lab import failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not load that photo."
        }
    }

    private func finishImport(_ image: CGImage, hasDepth: Bool) async {
        isWorking = true
        defer { isWorking = false }
        let measures = await Task.detached(priority: .userInitiated) {
            PhotoMeasures.measure(image, faceCount: PhotoMeasures.faceCount(in: image), hasDepth: hasDepth,
                                  autoEnhance: FilterMasks.autoEnhanceFilters(for: image))
        }.value
        do {
            _ = try store.add(image, measures: measures)
        } catch {
            logger.error("lab photo save failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not save that photo."
        }
    }

    private func export() async {
        isWorking = true
        defer { isWorking = false }
        let document = store.document, measures = store.measures, root = store.root
        do {
            let url = try await Task.detached(priority: .userInitiated) {
                try FilterLabExport.makeZip(document: document, measures: measures, root: root)
            }.value
            exportFile = ExportFile(url: url)
        } catch {
            logger.error("lab export failed: \(error.localizedDescription, privacy: .public)")
            status = "Export failed."
        }
    }

    private func deletePhoto(_ photo: LabPhoto) {
        do {
            try store.deletePhoto(photo.id)
        } catch {
            logger.error("lab photo delete failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not remove that photo."
        }
    }

    private func resetSession() {
        do {
            try store.reset()
        } catch {
            logger.error("lab reset failed: \(error.localizedDescription, privacy: .public)")
            status = "Could not start over."
        }
    }
}

/// A grid cell: a small decode of the photo and its look count.
private struct FilterLabThumbnail: View {
    let url: URL
    let looks: Int
    @State private var image: CGImage?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if let image {
                    Image(decorative: image, scale: 1).resizable().scaledToFill()
                } else {
                    ProgressView()
                }
            }
            .frame(width: 104, height: 104)
            .clipped()
            Text(verbatim: "\(looks)")
                .font(.caption.monospacedDigit())
                .padding(4)
                .background(.thinMaterial)
                .padding(4)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "Photo with \(looks) looks"))
        .task(id: url) {
            let url = self.url
            image = await Task.detached(priority: .utility) { () -> CGImage? in
                guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
                let options = [kCGImageSourceCreateThumbnailFromImageAlways: true,
                               kCGImageSourceThumbnailMaxPixelSize: 312] as CFDictionary
                return CGImageSourceCreateThumbnailAtIndex(source, 0, options)
            }.value
        }
    }
}

/// The system share sheet for the export zip (AirDrop to the Mac).
private struct FilterLabShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
#endif
```

- [ ] **Step 2: Wire the Developer row in `SettingsSheet.swift`**

Add the state, after `@State private var isPresentingIconLab = false`:

```swift
    @State private var isPresentingFilterLab = false
```

Add the cover, after the `IconLabView()` cover's closing brace (inside the same `#if DEBUG`):

```swift
            .fullScreenCover(isPresented: $isPresentingFilterLab) {
                FilterLabView()
            }
```

In `developerSection`, insert this row between the "Icon lab" row and the "Replay onboarding" row:

```swift
            Button {
                isPresentingFilterLab = true
            } label: {
                Label {
                    Text(verbatim: "Filter lab")
                } icon: {
                    PbblsGlyphView(.filterBox, .row)
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .middle)
```

- [ ] **Step 3: Build and run the guard tests**

Run the build command from Task 2, then the test command with `<Suite>` = `ThemeLiteralsTests`. Expected: build succeeds and `ThemeLiteralsTests` passes. If it flags a line, use a `Color.pebbles` role and no destructive role.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/Lab/FilterLabView.swift apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift
git commit -m "feat(ios): open the filter lab from the developer settings"
```

### Task 12: Simulator check, Part 2 gate and PR

- [ ] **Step 1: Simulator walk-through**

Boot "FilterLab iPhone 17", install the Debug build and open Settings → Developer → Filter lab. Drag a few photos into the simulator first (`xcrun simctl addmedia "FilterLab iPhone 17" <file>`), using test pictures, never the maintainer's. Check:
1. Adding a photo opens the crop editor, then a cell appears with "0".
2. The editor's sliders move the preview, holding the preview shows the original, and double-tapping a name resets it.
3. "Save as new look" adds a look, and leaving and coming back keeps it (the session survives a relaunch).
4. Export shows the share sheet with `filter-lab-<date>.zip`.

Refocus may show "No subject found" in the simulator. The device pass covers real masks.

- [ ] **Step 2: Lint and the full suite**

Same as Task 5, steps 1 and 2.

- [ ] **Step 3: Push the stack and open the Part 2 PR**

Title: `feat(ios): add the filter lab to the developer settings`. Body starts with `Closes #1675`, lists the files and says `Data safety: no change`. Labels `feat`, `ios`, same milestone as Part 1.

- [ ] **Step 4: Hand the device pass to the maintainer**

Install the Debug build on the maintainer's iPhone (the iOS device deploy recipe), then ask them to tune and export a first session. Once they have AirDropped the zip and named the folder, the analysis runs in the scratchpad as the spec describes. It is not part of this plan.

---

## After shipping

- Drop one build-journal pebble for the task (`node scripts/journal/drop-pebble.mjs`, collection `iOS`, valence `highlight`).
- Record lessons learned at the bottom of this plan.
