# Photo crop and rotate on iOS — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every new photo in Capture and Edit moment opens a crop editor (five fixed formats, pan/zoom, ±45° straighten, 90° turn, no empty space) before it is processed and uploaded.

**Architecture:** A pure `CropGeometry` value holds the crop in image-pixel terms and enforces coverage; a `PhotoCropRenderer` draws the crop from a 2048 px working image; `ImagePipeline` gains a `CGImage` entry point so the cropped image takes the existing encode and upload path. A SwiftUI `PhotoCropEditor` drives the geometry through an `@Observable` model, with hand-drawn ratio chips and an angle ruler. The flows load a `CropSource` on pick, present the editor, and call a new `SnapUploadCoordinator.ingest(cropped:…)` on Done.

**Tech Stack:** SwiftUI (iOS 17), CoreGraphics, ImageIO, Swift Testing, the `ReferenceImage` harness.

Spec: `docs/superpowers/specs/2026-11-15-ios-photo-crop-design.md` · Issue #1654 · Branch prefix `feat/1654-…-ios` (the `-ios` suffix keeps Vercel quiet).

---

## Ground rules for every task

- Work in `apps/ios`. New files under `Pebbles/Features/PebbleMedia/Crop/` and `PebblesTests/Crop/` are picked up by `project.yml`'s folder sources; run `xcodegen generate` after adding files.
- **Dedicated simulator** (parallel worktrees kill each other's test hosts on the shared one). Once per worktree:

  ```bash
  xcrun simctl create "iPhone 17 crop" com.apple.CoreSimulator.SimDeviceType.iPhone-17
  ```

- **Test command** (filter at suite level; a single Swift Testing test id matches nothing and passes):

  ```bash
  cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
    -destination 'platform=iOS Simulator,name=iPhone 17 crop' \
    -derivedDataPath build/dd-crop -only-testing:PebblesTests/<SuiteStruct> -quiet
  ```

  If a build looks stale after `xcodegen`, delete `build/dd-crop` (never the global DerivedData).
- Lint: `npm run lint --workspace=@pbbls/ios` (swiftlint). Colours only through `Color.pebbles.<role>` (`ThemeLiteralsTests` enforces it).
- Commits: conventional, lowercase, end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Coordinates: everything in this feature is **y-down, centred**: the crop window's centre is the origin, +x right, +y down, positive angles turn clockwise (as SwiftUI's `rotationEffect`).

## File map

| File | Responsibility |
|---|---|
| `Pebbles/Features/PebbleMedia/Crop/CropRatio.swift` | The five formats, pairing, closest-to-ratio |
| `Pebbles/Features/PebbleMedia/Crop/CropGeometry.swift` | Pure crop state + coverage maths + gesture helpers |
| `Pebbles/Features/PebbleMedia/Crop/PhotoCropRenderer.swift` | Geometry + working image → cropped `CGImage` |
| `Pebbles/Features/PebbleMedia/Crop/CropSource.swift` | Loads picked/captured bytes → working image + capture date |
| `Pebbles/Features/PebbleMedia/Crop/SeededGenerator.swift` | Deterministic RNG for the hand-drawn parts |
| `Pebbles/Features/PebbleMedia/Crop/PhotoCropModel.swift` | `@Observable` editor state |
| `Pebbles/Features/PebbleMedia/Crop/WobblyRectangle.swift` | Slightly irregular rectangle `Shape` |
| `Pebbles/Features/PebbleMedia/Crop/CropRatioChip.swift` | One ratio chip |
| `Pebbles/Features/PebbleMedia/Crop/AngleRuler.swift` | The ±45° ruler with hand-drawn ticks |
| `Pebbles/Features/PebbleMedia/Crop/CropCanvas.swift` | Photo under the window, scrim, grids, gestures |
| `Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift` | The screen: top bar, canvas, tool panel, tabs |
| `Pebbles/Features/PebbleMedia/ImagePipeline.swift` | + `process(_ image: CGImage)`, + `decodeOriented` |
| `Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift` | + `ingest(cropped:captureDate:userId:)`; loaders move to `CropSource` |
| `Pebbles/Features/Capture/CaptureFlowView.swift` | Pick/camera → editor → ingest |
| `Pebbles/Features/Path/EditMomentSheet.swift` | Pick → editor → ingest |
| `Pebbles/Features/Record/RecordStepScaffold.swift` | `fixedSlot` drawn below the content |
| `Pebbles/Resources/Localizable.xcstrings` | New strings, en + fr |

Out of this plan: Home's camera shortcut (`CameraMomentCoordinator`) — it records a moment in one shot with no form, so it keeps uploading the photo as taken. Filters, saved-photo editing, Android.

---

# Part 1 — geometry, renderer, pipeline (`feat/1654-crop-geometry-ios`)

### Task 1: `CropRatio`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropRatio.swift`
- Test: `apps/ios/PebblesTests/Crop/CropRatioTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
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
```

- [ ] **Step 2: Run it to verify it fails**

Run the test command with `-only-testing:PebblesTests/CropRatioTests`. Expected: build failure, `cannot find 'CropRatio' in scope`.

- [ ] **Step 3: Implement**

```swift
import CoreGraphics
import Foundation

/// The five crop formats (spec D3). No free crop: a moment's photo is always
/// one of these shapes. Raw values are the labels the chips never show (the
/// chip draws the shape) but logs and references use.
enum CropRatio: String, CaseIterable, Identifiable, Equatable {
    case fourFive = "4:5"
    case threeFour = "3:4"
    case square = "1:1"
    case fourThree = "4:3"
    case fiveFour = "5:4"

    var id: String { rawValue }

    var width: Int {
        switch self {
        case .fourFive: 4
        case .threeFour: 3
        case .square: 1
        case .fourThree: 4
        case .fiveFour: 5
        }
    }

    var height: Int {
        switch self {
        case .fourFive: 5
        case .threeFour: 4
        case .square: 1
        case .fourThree: 3
        case .fiveFour: 4
        }
    }

    /// Width over height.
    var value: CGFloat { CGFloat(width) / CGFloat(height) }

    /// The same format turned a quarter (spec D5).
    var paired: CropRatio {
        switch self {
        case .fourFive: .fiveFour
        case .threeFour: .fourThree
        case .square: .square
        case .fourThree: .threeFour
        case .fiveFour: .fourFive
        }
    }

    /// The format nearest `ratio` (width over height), compared in log space so
    /// a portrait and its landscape pair sit equally far from square. On a tie
    /// the earlier case wins, and the portrait ones come first.
    static func closest(to ratio: CGFloat) -> CropRatio {
        guard ratio > 0, ratio.isFinite else { return .square }
        let target = log(ratio)
        var best = CropRatio.square
        var bestDistance = CGFloat.infinity
        for candidate in allCases {
            let distance = abs(log(candidate.value) - target)
            if distance < bestDistance {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }
}
```

- [ ] **Step 4: Run it to verify it passes** — same command. Expected: `CropRatio` suite passes.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Crop/CropRatio.swift apps/ios/PebblesTests/Crop/CropRatioTests.swift
git commit -m "feat(ios): add the five crop formats"
```

### Task 2: `CropGeometry`

The state is kept in **image** terms so a change of format or angle keeps the same part of the photo centred (spec D6):

- `imageSize` — working image pixels, upright.
- `focus` — the image point (pixels from the image centre, y-down) under the window's centre.
- `scale` — window units per image pixel. The window is `ratio.value` wide and `1` tall.
- `quarterTurns` (0…3) and `angle` (degrees, −45…45): total turn θ = `quarterTurns·90° + angle`, clockwise.

An image point `p` (from the image centre) lands in the window at `R(θ)·(p − focus)·scale`. A window corner `c` comes from the image point `focus + R(−θ)·c / scale`. Coverage: for every corner, that point lies inside `±W/2, ±H/2`. With `ex = (a|cosθ| + b|sinθ|)/scale` and `ey = (a|sinθ| + b|cosθ|)/scale` (`a = w/2`, `b = h/2`), the corners are inside exactly when `|focus.x| ≤ W/2 − ex` and `|focus.y| ≤ H/2 − ey`. That gives the min scale and an exact focus clamp.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropGeometry.swift`
- Test: `apps/ios/PebblesTests/Crop/CropGeometryTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("CropGeometry")
struct CropGeometryTests {
    private static let portrait = CGSize(width: 1536, height: 2048)   // 3:4
    private static let landscape = CGSize(width: 2048, height: 1152)  // 16:9

    /// Every window corner, taken back into the image, lies inside it.
    private func covers(_ g: CropGeometry, tolerance: CGFloat = 0.01) -> Bool {
        g.windowCornersInImage().allSatisfy { p in
            abs(p.x) <= g.imageSize.width / 2 + tolerance && abs(p.y) <= g.imageSize.height / 2 + tolerance
        }
    }

    @Test("opens on the closest format, centred, at the smallest covering scale")
    func initial() {
        let g = CropGeometry.initial(imageSize: Self.portrait)
        #expect(g.ratio == .threeFour)
        #expect(g.focus == .zero)
        #expect(g.angle == 0 && g.quarterTurns == 0)
        #expect(abs(g.scale - g.minScale) < 1e-9)
        #expect(abs(g.scale - 1.0 / 2048) < 1e-9)   // window height 1 == image height
        #expect(covers(g))
    }

    @Test("min scale covers exactly: a hair less leaves a corner out", arguments: [-45.0, -17.0, 0.0, 9.5, 33.0, 45.0])
    func minScaleIsExact(angle: Double) {
        var g = CropGeometry.initial(imageSize: Self.landscape)
        g.ratio = .fourFive
        g.angle = angle
        g.scale = g.minScale
        #expect(covers(g))
        g.scale = g.minScale * 0.999
        #expect(!covers(g))
    }

    @Test("normalising always covers, whatever the pan, zoom, turn and format")
    func normalisedAlwaysCovers() {
        let angles: [Double] = [-45, -30, -7.5, 0, 3, 22, 45]
        let focuses: [CGPoint] = [.zero, CGPoint(x: 5000, y: 0), CGPoint(x: -900, y: 1400), CGPoint(x: 300, y: -300)]
        let scales: [CGFloat] = [0.00001, 0.0004, 0.002, 1]
        for size in [Self.portrait, Self.landscape] {
            for ratio in CropRatio.allCases {
                for turns in 0..<4 {
                    for angle in angles {
                        for focus in focuses {
                            for scale in scales {
                                let g = CropGeometry(imageSize: size, ratio: ratio, quarterTurns: turns,
                                                     angle: angle, scale: scale, focus: focus).normalized()
                                #expect(covers(g), "\(size) \(ratio) \(turns) \(angle) \(focus) \(scale)")
                                #expect(g.scale <= g.maxScale + 1e-12)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test("a focus already inside the bounds is left where it is")
    func clampKeepsValidFocus() {
        var g = CropGeometry.initial(imageSize: Self.landscape)
        g.ratio = .square
        g.scale = g.minScale
        g.focus = CGPoint(x: 120, y: 0)
        #expect(g.normalized().focus == CGPoint(x: 120, y: 0))
    }

    @Test("the angle is clamped to ±45 and the turns wrap")
    func clampsAngleAndTurns() {
        let g = CropGeometry(imageSize: Self.portrait, ratio: .square, quarterTurns: 5,
                             angle: 80, scale: 1, focus: .zero).normalized()
        #expect(g.angle == 45)
        #expect(g.quarterTurns == 1)
    }

    @Test("turning a quarter flips the format to its pair and keeps the focus")
    func quarterTurn() {
        var g = CropGeometry.initial(imageSize: Self.portrait)
        g.focus = CGPoint(x: 0, y: 100)
        let turned = g.turnedQuarter()
        #expect(turned.quarterTurns == 1)
        #expect(turned.ratio == .fourThree)
        // Here the turned window spans the whole photo, so the focus is pulled to the centre.
        #expect(turned == turned.normalized())
    }

    @Test("changing the angle raises the scale only as far as coverage needs")
    func angleRaisesScaleMinimally() {
        var g = CropGeometry.initial(imageSize: Self.portrait)
        g.scale = g.minScale * 3
        let tilted = g.withAngle(10)
        #expect(tilted.scale == g.scale)          // already covering: untouched
        let fromMin = CropGeometry.initial(imageSize: Self.portrait).withAngle(10)
        #expect(abs(fromMin.scale - fromMin.minScale) < 1e-12)
    }

    @Test("dragging right moves the photo right: the focus moves left in the image")
    func panDirection() {
        let g = CropGeometry.initial(imageSize: Self.landscape)   // 16:9 → 4:3, room to pan sideways
        let moved = g.panned(by: CGSize(width: 0.1, height: 0))
        #expect(moved.focus.x < 0)
        #expect(abs(moved.focus.y) < 1e-9)
    }

    @Test("the rubber band shows part of an overshoot, never all of it")
    func rubberBand() {
        let g = CropGeometry.initial(imageSize: Self.landscape)
        let far = g.panned(by: CGSize(width: 10, height: 0))
        let banded = far.rubberBanded()
        let clamped = far.normalized()
        #expect(abs(banded.focus.x) > abs(clamped.focus.x))
        #expect(abs(banded.focus.x) < abs(far.focus.x))
    }

    @Test("the window offset puts the focus at the window centre")
    func offsetMatchesFocus() {
        var g = CropGeometry.initial(imageSize: Self.landscape)
        g.angle = 20
        g.scale = g.minScale * 2
        g.focus = CGPoint(x: 200, y: -50)
        // The focus point, drawn: R(θ)·(focus·scale) + offset == 0.
        let t = CGAffineTransform(rotationAngle: g.theta)
        let drawn = CGPoint(x: g.focus.x * g.scale, y: g.focus.y * g.scale).applying(t)
        #expect(abs(drawn.x + g.windowOffset.width) < 1e-9)
        #expect(abs(drawn.y + g.windowOffset.height) < 1e-9)
    }
}
```

- [ ] **Step 2: Run it to verify it fails** — `-only-testing:PebblesTests/CropGeometryTests`. Expected: `cannot find 'CropGeometry' in scope`.

- [ ] **Step 3: Implement**

```swift
import CoreGraphics
import Foundation

/// Where the crop window sits on the photo (spec "Geometry"). Pure value: no
/// view sizes, no SwiftUI. Window units: the window is `ratio.value` wide and
/// 1 tall, centred on the origin, y down; positive turns are clockwise.
struct CropGeometry: Equatable {
    /// The working image's pixel size, upright (EXIF orientation applied).
    var imageSize: CGSize
    var ratio: CropRatio
    /// Clockwise quarter turns, 0…3 once normalised.
    var quarterTurns: Int
    /// Fine turn in degrees, −45…45 once normalised.
    var angle: Double
    /// Window units per image pixel.
    var scale: CGFloat
    /// The image point under the window centre, in pixels from the image centre, y down.
    var focus: CGPoint

    static let maxAngle: Double = 45
    /// How far past the smallest covering scale a pinch may go.
    static let maxZoom: CGFloat = 8
    /// The share of an overshoot the rubber band shows while dragging.
    static let rubberBandFactor: CGFloat = 0.3

    /// The editor's opening state (spec D3): the closest format, centred, covering.
    static func initial(imageSize: CGSize) -> CropGeometry {
        let ratio = CropRatio.closest(to: imageSize.width / max(imageSize.height, 1))
        var g = CropGeometry(imageSize: imageSize, ratio: ratio, quarterTurns: 0, angle: 0, scale: 0, focus: .zero)
        g.scale = g.minScale
        return g
    }

    // MARK: - Derived

    var windowSize: CGSize { CGSize(width: ratio.value, height: 1) }

    /// Total clockwise turn, radians.
    var theta: CGFloat { CGFloat((Double(quarterTurns) * 90 + angle) * .pi / 180) }

    /// The smallest scale at which the turned image covers the window.
    var minScale: CGFloat {
        let c = abs(cos(theta)), s = abs(sin(theta))
        let w = windowSize.width, h = windowSize.height
        let byWidth = (w * c + h * s) / imageSize.width
        let byHeight = (w * s + h * c) / imageSize.height
        return max(byWidth, byHeight)
    }

    var maxScale: CGFloat { minScale * Self.maxZoom }

    /// Half the window's extent along the image's own axes, in image pixels.
    private func halfExtentsInImage(scale: CGFloat) -> CGSize {
        let c = abs(cos(theta)), s = abs(sin(theta))
        let a = windowSize.width / 2, b = windowSize.height / 2
        return CGSize(width: (a * c + b * s) / scale, height: (a * s + b * c) / scale)
    }

    /// Where the photo's centre sits relative to the window centre, in window
    /// units: what the canvas offsets the rotated, scaled image by.
    var windowOffset: CGSize {
        let p = CGPoint(x: -focus.x * scale, y: -focus.y * scale)
            .applying(CGAffineTransform(rotationAngle: theta))
        return CGSize(width: p.x, height: p.y)
    }

    /// The window's four corners taken back into image pixels (from the image centre).
    func windowCornersInImage() -> [CGPoint] {
        let a = windowSize.width / 2, b = windowSize.height / 2
        let back = CGAffineTransform(rotationAngle: -theta)
        return [CGPoint(x: -a, y: -b), CGPoint(x: a, y: -b), CGPoint(x: a, y: b), CGPoint(x: -a, y: b)].map { c in
            let r = c.applying(back)
            return CGPoint(x: focus.x + r.x / scale, y: focus.y + r.y / scale)
        }
    }

    // MARK: - Clamping

    /// Angle and turns in range, scale between covering and the zoom cap, and
    /// the focus pulled in until every window corner is on the photo.
    func normalized() -> CropGeometry {
        var g = self
        g.quarterTurns = ((quarterTurns % 4) + 4) % 4
        g.angle = min(max(angle, -Self.maxAngle), Self.maxAngle)
        g.scale = min(max(scale, g.minScale), g.maxScale)
        g.focus = g.clampedFocus(g.focus)
        return g
    }

    private func clampedFocus(_ point: CGPoint) -> CGPoint {
        let half = halfExtentsInImage(scale: scale)
        let limitX = max(imageSize.width / 2 - half.width, 0)
        let limitY = max(imageSize.height / 2 - half.height, 0)
        return CGPoint(x: min(max(point.x, -limitX), limitX), y: min(max(point.y, -limitY), limitY))
    }

    /// While a drag runs past the edge: the photo follows a share of the
    /// overshoot, so the edge resists instead of stopping dead.
    func rubberBanded() -> CropGeometry {
        var g = self
        g.scale = min(max(scale, minScale), maxScale)
        let clamped = g.clampedFocus(focus)
        g.focus = CGPoint(x: clamped.x + (focus.x - clamped.x) * Self.rubberBandFactor,
                          y: clamped.y + (focus.y - clamped.y) * Self.rubberBandFactor)
        return g
    }

    // MARK: - Edits

    /// The photo follows the finger: a drag of `translation` window units moves
    /// the focus the opposite way, in image pixels. Not clamped.
    func panned(by translation: CGSize) -> CropGeometry {
        var g = self
        let d = CGPoint(x: translation.width, y: translation.height)
            .applying(CGAffineTransform(rotationAngle: -theta))
        g.focus = CGPoint(x: focus.x - d.x / scale, y: focus.y - d.y / scale)
        return g
    }

    /// A pinch about the window centre. Not clamped.
    func zoomed(by factor: CGFloat) -> CropGeometry {
        var g = self
        g.scale = scale * factor
        return g
    }

    /// Spec D6: same focus, the scale raised only if the new angle needs it.
    func withAngle(_ degrees: Double) -> CropGeometry {
        var g = self
        g.angle = degrees
        return g.normalized()
    }

    func withRatio(_ newRatio: CropRatio) -> CropGeometry {
        var g = self
        g.ratio = newRatio
        return g.normalized()
    }

    /// Spec D5: a clockwise quarter turn, the format flipped to its pair.
    func turnedQuarter() -> CropGeometry {
        var g = self
        g.quarterTurns += 1
        g.ratio = ratio.paired
        return g.normalized()
    }
}
```

- [ ] **Step 4: Run it to verify it passes.** If `minScaleIsExact` fails at 45° for the 0.999 case, check `minScale` uses `abs` on both cos and sin — that is the usual slip.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Crop/CropGeometry.swift apps/ios/PebblesTests/Crop/CropGeometryTests.swift
git commit -m "feat(ios): crop geometry that never leaves an empty corner"
```

### Task 3: `ImagePipeline` — decode at a size, process a `CGImage`

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift`
- Test: `apps/ios/PebblesTests/Crop/ImagePipelineCroppedTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

@Suite("ImagePipeline cropped input")
struct ImagePipelineCroppedTests {
    static func solid(width: Int, height: Int) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let context = try #require(CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                             bytesPerRow: 0, space: space,
                                             bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
        context.setFillColor(red: 0.3, green: 0.6, blue: 0.2, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        return try #require(context.makeImage())
    }

    private func pixelSize(_ jpeg: Data) throws -> CGSize {
        let source = try #require(CGImageSourceCreateWithData(jpeg as CFData, nil))
        let props = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
        let w = try #require(props[kCGImagePropertyPixelWidth] as? Int)
        let h = try #require(props[kCGImagePropertyPixelHeight] as? Int)
        return CGSize(width: w, height: h)
    }

    @Test("a cropped image comes out as a 1024 original and a 420 thumb, ratio kept")
    func sizes() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 1600, height: 2000))
        #expect(try pixelSize(processed.original) == CGSize(width: 819, height: 1024))
        #expect(try pixelSize(processed.thumb) == CGSize(width: 336, height: 420))
    }

    @Test("a small crop is not upscaled")
    func noUpscale() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 600, height: 600))
        #expect(try pixelSize(processed.original) == CGSize(width: 600, height: 600))
    }

    @Test("no EXIF, GPS or TIFF dictionary is written")
    func noMetadata() throws {
        let processed = try ImagePipeline.process(Self.solid(width: 800, height: 1000))
        for data in [processed.original, processed.thumb] {
            let source = try #require(CGImageSourceCreateWithData(data as CFData, nil))
            let props = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
            #expect(props[kCGImagePropertyExifDictionary] == nil)
            #expect(props[kCGImagePropertyGPSDictionary] == nil)
            #expect(props[kCGImagePropertyTIFFDictionary] == nil)
        }
    }

    @Test("decodeOriented caps the long edge")
    func decodeOriented() throws {
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, try Self.solid(width: 3000, height: 4000), nil)
        #expect(CGImageDestinationFinalize(destination))
        let image = try ImagePipeline.decodeOriented(buffer as Data, maxEdge: 2048)
        #expect(image.width == 1536 && image.height == 2048)
    }
}
```

- [ ] **Step 2: Run it to verify it fails** — `-only-testing:PebblesTests/ImagePipelineCroppedTests`. Expected: `type 'ImagePipeline' has no member 'process'` (for the `CGImage` overload) / `decodeOriented`.

- [ ] **Step 3: Implement.** In `ImagePipeline.swift`:

1. Extract the quality loop out of `renderJPEG` so both entry points share it. Replace the body of `renderJPEG` from `let cgImage = makeOpaque(thumbnail)` to the end with a call, and add the helper:

```swift
        // JPEG cannot store alpha. Handing ImageIO an RGBA image makes it log
        // a warning per encode and strip the channel itself, so drop it once
        // here, before the quality-step loop re-encodes the same pixels.
        return try encodeCapped(makeOpaque(thumbnail), startQuality: startQuality, byteCap: byteCap)
    }

    /// Re-encodes at falling quality until the bytes fit `byteCap`.
    private static func encodeCapped(_ image: CGImage, startQuality: CGFloat, byteCap: Int) throws -> Data {
        var quality = startQuality
        for _ in 0...qualitySteps {
            let data = try encodeJPEG(image, quality: quality)
            if data.count <= byteCap {
                return data
            }
            quality -= 0.1
            if quality <= 0.1 { break }
        }
        throw ImagePipelineError.tooLargeAfterResize
    }
```

2. Add the two new entry points after `process(_:uti:)`:

```swift
    /// The crop editor's output (spec "Pipeline"): already upright, already
    /// cropped, carrying no metadata. Same sizes, caps and encode as a pick.
    static func process(_ image: CGImage) throws -> ProcessedImage {
        let original = try encodeCapped(
            resized(image, maxEdge: originalMaxEdge), startQuality: originalStartQuality, byteCap: originalMaxBytes)
        let thumb = try encodeCapped(
            resized(image, maxEdge: thumbMaxEdge), startQuality: thumbStartQuality, byteCap: thumbMaxBytes)
        return ProcessedImage(original: original, thumb: thumb)
    }

    /// Decodes `data` upright (EXIF orientation baked) with its long edge at
    /// most `maxEdge`: the crop editor's working image.
    static func decodeOriented(_ data: Data, maxEdge: CGFloat) throws -> CGImage {
        let source = try makeImageSource(data)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxEdge
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw ImagePipelineError.decodeFailed
        }
        return image
    }

    /// Downscales into an opaque sRGB bitmap with its long edge at most
    /// `maxEdge`; never upscales. Opaque either way, as `makeOpaque`.
    static func resized(_ image: CGImage, maxEdge: CGFloat) -> CGImage {
        let longEdge = CGFloat(max(image.width, image.height))
        guard longEdge > maxEdge else { return makeOpaque(image) }
        let factor = maxEdge / longEdge
        let width = Int((CGFloat(image.width) * factor).rounded())
        let height = Int((CGFloat(image.height) * factor).rounded())
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: 0, space: space,
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            return makeOpaque(image)
        }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage() ?? makeOpaque(image)
    }
```

Note the expected sizes: 1600×2000 → factor 1024/2000 = 0.512 → 819.2 → **819**×1024; thumb 0.21 → 336×420.

- [ ] **Step 4: Run** `ImagePipelineCroppedTests` **and** `ImagePipelineOpaqueTests` (the extraction must not change the pick path). Both pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/ImagePipeline.swift apps/ios/PebblesTests/Crop/ImagePipelineCroppedTests.swift
git commit -m "feat(ios): let the image pipeline take an already-cropped image"
```

### Task 4: `PhotoCropRenderer`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropRenderer.swift`
- Test: `apps/ios/PebblesTests/Crop/PhotoCropRendererTests.swift`

- [ ] **Step 1: Write the failing test.** A 400×400 image with four coloured quadrants (red top-left, green top-right, blue bottom-right, yellow bottom-left, as seen upright):

```swift
import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PhotoCropRenderer")
struct PhotoCropRendererTests {
    enum Q { case red, green, blue, yellow }

    /// Upright quadrants: red top-left, green top-right, blue bottom-right, yellow bottom-left.
    static func quadrants(size: Int = 400) throws -> CGImage {
        let space = try #require(CGColorSpace(name: CGColorSpace.sRGB))
        let ctx = try #require(CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                                         space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue))
        let h = CGFloat(size) / 2
        // CGContext is y-up: the top half of the picture is y ∈ [h, 2h].
        ctx.setFillColor(red: 1, green: 0, blue: 0, alpha: 1); ctx.fill(CGRect(x: 0, y: h, width: h, height: h))
        ctx.setFillColor(red: 0, green: 1, blue: 0, alpha: 1); ctx.fill(CGRect(x: h, y: h, width: h, height: h))
        ctx.setFillColor(red: 0, green: 0, blue: 1, alpha: 1); ctx.fill(CGRect(x: h, y: 0, width: h, height: h))
        ctx.setFillColor(red: 1, green: 1, blue: 0, alpha: 1); ctx.fill(CGRect(x: 0, y: 0, width: h, height: h))
        return try #require(ctx.makeImage())
    }

    /// The colour at a point given y-down from the image's top-left.
    static func colour(_ image: CGImage, x: Int, yDown: Int) throws -> Q? {
        let rgba = try #require(ReferenceImage.rgba(image))
        let i = (yDown * image.width + x) * 4
        let (r, g, b) = (rgba[i], rgba[i + 1], rgba[i + 2])
        switch (r > 200, g > 200, b > 200) {
        case (true, false, false): return .red
        case (false, true, false): return .green
        case (false, false, true): return .blue
        case (true, true, false): return .yellow
        default: return nil
        }
    }

    private func corners(_ image: CGImage) throws -> [Q?] {
        let w = image.width, h = image.height, m = 4
        return [try Self.colour(image, x: m, yDown: m), try Self.colour(image, x: w - m, yDown: m),
                try Self.colour(image, x: w - m, yDown: h - m), try Self.colour(image, x: m, yDown: h - m)]
    }

    @Test("untouched square crop keeps the picture upright")
    func upright() throws {
        let source = try Self.quadrants()
        let g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400))
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(out.width == 400 && out.height == 400)
        #expect(try corners(out) == [.red, .green, .blue, .yellow])
    }

    @Test("a clockwise quarter turn moves top-left to top-right")
    func quarterTurn() throws {
        let source = try Self.quadrants()
        let g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400)).turnedQuarter()
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(try corners(out) == [.yellow, .red, .green, .blue])
    }

    @Test("a 4:5 crop panned left shows only the left half's colours")
    func panAndCrop() throws {
        let source = try Self.quadrants()
        var g = CropGeometry.initial(imageSize: CGSize(width: 400, height: 400)).withRatio(.fourFive)
        g.scale = g.minScale * 2.5
        g.focus = CGPoint(x: -100, y: 0)
        g = g.normalized()
        let out = try PhotoCropRenderer.render(source, geometry: g)
        #expect(abs(CGFloat(out.width) / CGFloat(out.height) - 0.8) < 0.01)
        #expect(try corners(out) == [.red, .red, .yellow, .yellow])
    }

    @Test("the output long edge never exceeds 1024 nor the source pixels it covers")
    func outputSize() {
        var g = CropGeometry.initial(imageSize: CGSize(width: 2048, height: 1536))
        #expect(PhotoCropRenderer.outputSize(for: g) == CGSize(width: 1024, height: 768))
        g.scale = g.minScale * 8   // window covers 256×192 source pixels
        #expect(PhotoCropRenderer.outputSize(for: g) == CGSize(width: 256, height: 192))
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `-only-testing:PebblesTests/PhotoCropRendererTests`.

- [ ] **Step 3: Implement**

```swift
import CoreGraphics
import Foundation

enum PhotoCropRendererError: Error { case contextFailed }

/// Draws the crop window's content (spec "Rendering on Done") from the working
/// image. Pure: no UIKit, no logging. The output is at most 1024 px on the long
/// edge and never more pixels than the window covers in the source.
enum PhotoCropRenderer {
    static let maxOutputEdge: CGFloat = 1024

    static func outputSize(for g: CropGeometry) -> CGSize {
        let window = g.windowSize
        let longWindow = max(window.width, window.height)
        let sourcePixels = longWindow / g.scale
        let longEdge = min(maxOutputEdge, sourcePixels.rounded())
        let perUnit = longEdge / longWindow
        return CGSize(width: (window.width * perUnit).rounded(), height: (window.height * perUnit).rounded())
    }

    static func render(_ image: CGImage, geometry g: CropGeometry) throws -> CGImage {
        let size = outputSize(for: g)
        let width = Int(size.width), height = Int(size.height)
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            throw PhotoCropRendererError.contextFailed
        }
        ctx.interpolationQuality = .high
        let perUnit = size.height / g.windowSize.height

        // y-down, window centre at the origin, window units.
        ctx.translateBy(x: 0, y: size.height)
        ctx.scaleBy(x: 1, y: -1)
        ctx.translateBy(x: size.width / 2, y: size.height / 2)
        ctx.scaleBy(x: perUnit, y: perUnit)
        // Same chain as the canvas: offset, turn, scale.
        ctx.translateBy(x: g.windowOffset.width, y: g.windowOffset.height)
        ctx.rotate(by: g.theta)
        ctx.scaleBy(x: g.scale, y: g.scale)
        // Image pixels from the image centre, y-down. CGContext.draw puts a
        // picture's top row at the rect's max y, so flip once more locally.
        let w = CGFloat(image.width), h = CGFloat(image.height)
        ctx.translateBy(x: -w / 2, y: h / 2)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))

        guard let output = ctx.makeImage() else { throw PhotoCropRendererError.contextFailed }
        return output
    }
}
```

- [ ] **Step 4: Run to verify it passes.** If `quarterTurn` reads `[.green, .blue, .yellow, .red]` the turn is anticlockwise: the canvas and renderer must agree that positive θ is clockwise in y-down space — check that the y-flip happens *before* `rotate`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropRenderer.swift apps/ios/PebblesTests/Crop/PhotoCropRendererTests.swift
git commit -m "feat(ios): render the crop window from the working image"
```

### Task 5: `CropSource` and the coordinator's cropped ingest

`CropSource` loads what the editor needs; the coordinator learns to take a cropped image. The pick loaders move out of the coordinator because the flows now load **before** the editor and ingest **after** it.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropSource.swift`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`
- Test: `apps/ios/PebblesTests/Crop/CropSourceTests.swift`, `apps/ios/PebblesTests/SnapUploadCoordinatorPickTests.swift` (append)

- [ ] **Step 1: Write the failing tests.** `CropSourceTests.swift`:

```swift
import CoreGraphics
import Foundation
import ImageIO
import Testing
import UniformTypeIdentifiers
@testable import Pebbles

@Suite("CropSource")
struct CropSourceTests {
    private func jpeg(width: Int, height: Int) throws -> Data {
        let image = try ImagePipelineCroppedTests.solid(width: width, height: height)
        let buffer = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(
            buffer as CFMutableData, UTType.jpeg.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))
        return buffer as Data
    }

    @Test("bytes become a working image capped at 2048")
    func loads() async throws {
        let source = try #require(await CropSource.make(from: try jpeg(width: 3000, height: 4000)))
        #expect(source.image.height == 2048)
        #expect(source.captureDate == nil)
    }

    @Test("bytes that are not an image give nothing")
    func rejects() async {
        #expect(await CropSource.make(from: Data("not an image".utf8)) == nil)
    }
}
```

Append to `SnapUploadCoordinatorPickTests` (inside the suite):

```swift
    @Test("a cropped image attaches, uploads and keeps the date read before the crop")
    func croppedIngest() async throws {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        let image = try ImagePipelineCroppedTests.solid(width: 800, height: 1000)
        let date = Date(timeIntervalSince1970: 1_700_000_000)

        await coordinator.ingest(cropped: image, captureDate: date, userId: UUID())

        guard case .pending(let snap) = coordinator.formSnap else {
            Issue.record("expected a pending snap, got \(String(describing: coordinator.formSnap))"); return
        }
        #expect(snap.state == .uploaded)
        #expect(coordinator.pickedCaptureDate == date)
        #expect(!coordinator.isBlocking)
    }

    @Test("a cropped image landing after close attaches and uploads nothing")
    func croppedAfterClose() async throws {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        await coordinator.cancelAndCleanup(userId: UUID())
        await coordinator.ingest(cropped: try ImagePipelineCroppedTests.solid(width: 100, height: 100),
                                 captureDate: nil, userId: UUID())
        #expect(coordinator.formSnap == nil)
        #expect(repo.calls.isEmpty)
    }
```

(If `FakeRepo`'s upload does not end in `.uploaded` by default, read `SnapUploadCoordinatorTests.FakeRepo` and match how the existing `ingest` tests assert success.)

- [ ] **Step 2: Run** `-only-testing:PebblesTests/CropSourceTests -only-testing:PebblesTests/SnapUploadCoordinatorPickTests`. Expected: compile errors for `CropSource` and `ingest(cropped:…)`.

- [ ] **Step 3: Implement `CropSource`**

```swift
import CoreGraphics
import Foundation
import PhotosUI
import SwiftUI
import os

/// What the crop editor opens on (spec "Working image"): the photo decoded
/// upright at 2048 px, and the capture date read from the original bytes
/// before anything drops the metadata (spec D8).
struct CropSource: Identifiable {
    let id = UUID()
    let image: CGImage
    let captureDate: Date?

    static let workingMaxEdge: CGFloat = 2048
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "crop-source")

    /// Bytes in hand (a pick already loaded, or the camera's JPEG). Nil when
    /// the format is unsupported or the bytes do not decode (logged).
    static func make(from data: Data) async -> CropSource? {
        guard let uti = ImageFormatValidator.uti(of: data), ImageFormatValidator.isSupported(uti) else {
            logger.warning("unsupported crop source format")
            return nil
        }
        let captureDate = ExifCaptureDate.from(data)
        do {
            let image = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.decodeOriented(data, maxEdge: CropSource.workingMaxEdge)
            }.value
            return CropSource(image: image, captureDate: captureDate)
        } catch {
            logger.error("crop source decode failed: \(String(describing: error), privacy: .public)")
            return nil
        }
    }

    /// The record flow's inline `PhotosPicker`.
    static func make(from item: PhotosPickerItem) async -> CropSource? {
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else {
                logger.warning("picker item carried no data representation")
                return nil
            }
            return await make(from: data)
        } catch {
            logger.error("picker item load failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    /// The sheet-presented `PHPicker` (`EditMomentSheet`).
    static func make(from picked: PhotoPickerView.PickedItem) async -> CropSource? {
        do {
            let data: Data = try await withCheckedThrowingContinuation { continuation in
                _ = picked.itemProvider.loadDataRepresentation(forTypeIdentifier: picked.uti) { data, error in
                    if let data { continuation.resume(returning: data) } else {
                        continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown))
                    }
                }
            }
            return await make(from: data)
        } catch {
            logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }
}
```

`make(from: Data)` sniffs the format with `ImageFormatValidator.uti(of:)`; for a PHPicker pick that names a supported UTI the sniff agrees. Before writing the `PickedItem` loader, read the coordinator's existing `private static func loadData(from:uti:)` (around line 429) and keep its behaviour (it may copy a file representation rather than a data one) — move it verbatim if it differs from the sketch above.

- [ ] **Step 4: Implement the coordinator change.** In `SnapUploadCoordinator.swift`:

1. Split the private `ingest(_:uti:userId:generation:)` after the pipeline step. Its tail (from `// The form closed or is saving…` to the end) becomes:

```swift
    /// Attach → replace → upload, shared by every ingest once its bytes are processed.
    private func attach(_ processed: ProcessedImage, captureDate: Date?, userId: UUID, generation: Int) async {
        // The form closed or is saving, or a later pick superseded this one,
        // while it was being read: nothing is attached, replaced or deleted.
        guard accepts(generation) else { return }
        // … the existing body, unchanged, from "A saved photo replaced by a new pick" to the end …
    }
```

and the existing private `ingest` ends with `await attach(processed, captureDate: captureDate, userId: userId, generation: generation)`.

2. Add the cropped entry point next to the public `ingest(_:uti:userId:)`:

```swift
    /// The crop editor's Done (spec "Pipeline"): the image is already upright
    /// and cropped, and its date was read from the original bytes beforehand.
    func ingest(cropped image: CGImage, captureDate: Date?, userId: UUID) async {
        let generation = nextPickGeneration()
        picksInFlight += 1
        defer { picksInFlight -= 1 }
        guard accepts(generation) else { return }
        let processed: ProcessedImage
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(image)
            }.value
        } catch {
            Self.logger.error("cropped pipeline failed: \(String(describing: error), privacy: .public)")
            return
        }
        await attach(processed, captureDate: captureDate, userId: userId, generation: generation)
    }
```

3. Leave `handlePicked(_:)` (both) and `loadData` in place for now — Part 3 removes them once their last callers move. (Each part must compile alone.)

- [ ] **Step 5: Run** both suites, then the whole `SnapUploadCoordinatorTests` and `SnapUploadCoordinatorPickTests` to prove the split changed nothing. All pass.

- [ ] **Step 6: Lint and commit**

```bash
npm run lint --workspace=@pbbls/ios
git add apps/ios/Pebbles/Features/PebbleMedia apps/ios/PebblesTests/Crop/CropSourceTests.swift apps/ios/PebblesTests/SnapUploadCoordinatorPickTests.swift
git commit -m "feat(ios): load a crop source and ingest a cropped photo"
```

### Part 1 PR

Push and open with `gh stack` (read the `gh-stack` skill first). Title `feat(ios): crop geometry, renderer and cropped ingest`; body starts `Part of #1654` (the last part closes it), lists the files, `Data safety: no change`. Labels `feat`, `ios`, `ui`; milestone `M70 · Record flow`.

---

# Part 2 — the editor screen (`feat/1654-crop-editor-ios`)

### Task 6: `SeededGenerator` and `WobblyRectangle`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/SeededGenerator.swift`, `apps/ios/Pebbles/Features/PebbleMedia/Crop/WobblyRectangle.swift`
- Test: `apps/ios/PebblesTests/Crop/WobblyRectangleTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import SwiftUI
import Testing
@testable import Pebbles

@Suite("Wobbly rectangle")
struct WobblyRectangleTests {
    @Test("the same seed draws the same path; another seed does not")
    func deterministic() {
        let rect = CGRect(x: 0, y: 0, width: 40, height: 50)
        let a = WobblyRectangle(seed: "4:5").path(in: rect)
        let b = WobblyRectangle(seed: "4:5").path(in: rect)
        let c = WobblyRectangle(seed: "3:4").path(in: rect)
        #expect(a.description == b.description)
        #expect(a.description != c.description)
    }

    @Test("the wobble stays within 5 % of the rect")
    func bounded() {
        let rect = CGRect(x: 0, y: 0, width: 100, height: 100)
        let bounds = WobblyRectangle(seed: "1:1").path(in: rect).boundingRect
        #expect(bounds.insetBy(dx: -5, dy: -5).contains(rect.insetBy(dx: 5, dy: 5)))
        #expect(rect.insetBy(dx: -5, dy: -5).contains(bounds))
    }

    @Test("the generator repeats for a seed")
    func generator() {
        var a = SeededGenerator(seed: "tick-12"), b = SeededGenerator(seed: "tick-12")
        #expect(a.next() == b.next())
        #expect(a.unit() == b.unit())
    }
}
```

- [ ] **Step 2: Run to verify it fails** — `-only-testing:PebblesTests/WobblyRectangleTests`.

- [ ] **Step 3: Implement**

`SeededGenerator.swift`:

```swift
import Foundation

/// SplitMix64 seeded by `StableHash.fnv1a`: the hand-drawn parts of the crop
/// editor wobble the same way on every render (references record twice).
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: String) {
        state = UInt64(StableHash.fnv1a(seed)) | 0x9E37_79B9_0000_0000
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A value in −1…1.
    mutating func unit() -> CGFloat {
        CGFloat(Double(next() >> 11) / Double(1 << 53)) * 2 - 1
    }
}
```

`WobblyRectangle.swift`:

```swift
import SwiftUI

/// A rectangle drawn by hand (spec "hand-drawn parts"): each corner nudged and
/// each edge bowed in or out by up to `amount` of its length, the same way for
/// the same seed. Small corner rounding keeps it a pebble, not a sketch.
struct WobblyRectangle: Shape {
    let seed: String
    var amount: CGFloat = 0.03

    func path(in rect: CGRect) -> Path {
        var rng = SeededGenerator(seed: seed)
        let nudge = min(rect.width, rect.height) * amount * 0.5
        let corners = [
            CGPoint(x: rect.minX, y: rect.minY), CGPoint(x: rect.maxX, y: rect.minY),
            CGPoint(x: rect.maxX, y: rect.maxY), CGPoint(x: rect.minX, y: rect.maxY)
        ].map { CGPoint(x: $0.x + rng.unit() * nudge, y: $0.y + rng.unit() * nudge) }

        var path = Path()
        path.move(to: corners[0])
        for i in 0..<4 {
            let from = corners[i], to = corners[(i + 1) % 4]
            let length = hypot(to.x - from.x, to.y - from.y)
            // Outward normal for a clockwise outline in y-down space.
            let normal = CGPoint(x: (to.y - from.y) / length, y: -(to.x - from.x) / length)
            let bow = rng.unit() * length * amount
            let mid = CGPoint(x: (from.x + to.x) / 2, y: (from.y + to.y) / 2)
            // A quadratic's control at twice the bow puts the curve's midpoint at the bow.
            let control = CGPoint(x: mid.x + normal.x * bow * 2, y: mid.y + normal.y * bow * 2)
            path.addQuadCurve(to: to, control: control)
        }
        path.closeSubpath()
        return path
    }
}
```

`bounded` allows 5 % although the bow is 3 %: the corner nudge adds up to 1.5 %.

- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `feat(ios): a seeded wobbly rectangle for the ratio chips`.

### Task 7: `PhotoCropModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropModel.swift`
- Test: `apps/ios/PebblesTests/Crop/PhotoCropModelTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

@Suite("PhotoCropModel") @MainActor
struct PhotoCropModelTests {
    private func model(width: Int = 1536, height: Int = 2048) throws -> PhotoCropModel {
        PhotoCropModel(source: CropSource(image: try ImagePipelineCroppedTests.solid(width: width, height: height),
                                          captureDate: nil))
    }

    @Test("opens unchanged on the closest format and the Ratio tool")
    func opens() throws {
        let m = try model()
        #expect(m.geometry.ratio == .threeFour)
        #expect(m.tool == .ratio)
        #expect(!m.hasChanges)
    }

    @Test("picking a format, turning, straightening all count as changes; reset undoes them")
    func changesAndReset() throws {
        let m = try model()
        m.select(.square)
        #expect(m.hasChanges)
        m.reset()
        #expect(!m.hasChanges)
        m.turnQuarter()
        #expect(m.geometry.ratio == .fourThree)
        m.setAngle(12)
        #expect(m.geometry.angle == 12)
        m.reset()
        #expect(m.geometry == CropGeometry.initial(imageSize: m.geometry.imageSize))
    }

    @Test("a drag past the edge rubber-bands, and settles inside on release")
    func dragSettles() throws {
        let m = try model(width: 2048, height: 1152)
        m.gestureChanged(translation: CGSize(width: 5000, height: 0), magnification: 1, windowHeight: 400)
        let during = m.geometry
        m.gestureEnded()
        #expect(m.geometry == m.geometry.normalized())
        #expect(during != m.geometry)
        #expect(!m.isInteracting)
    }

    @Test("a pinch below covering springs back to covering")
    func pinchClamps() throws {
        let m = try model()
        m.gestureChanged(translation: .zero, magnification: 0.3, windowHeight: 400)
        m.gestureEnded()
        #expect(abs(m.geometry.scale - m.geometry.minScale) < 1e-12)
    }

    @Test("render gives the window's shape")
    func renders() async throws {
        let m = try model()
        m.select(.fourFive)
        let image = try await m.render()
        #expect(abs(CGFloat(image.width) / CGFloat(image.height) - 0.8) < 0.01)
    }
}
```

- [ ] **Step 2: Run to verify it fails.**

- [ ] **Step 3: Implement**

```swift
import CoreGraphics
import Observation

enum CropTool: CaseIterable, Identifiable {
    case ratio, rotate
    var id: Self { self }
}

/// The crop editor's state. Gestures arrive as whole-gesture totals
/// (translation, magnification) applied to the geometry the gesture started
/// from, so pan and pinch together never fight over one value.
@Observable @MainActor
final class PhotoCropModel {
    let source: CropSource
    private(set) var geometry: CropGeometry
    var tool: CropTool = .ratio
    /// True while a finger is on the canvas: the thirds grid shows.
    private(set) var isInteracting = false
    /// True while the ruler is dragged: the finer grid shows.
    var isRotating = false

    @ObservationIgnored private var gestureStart: CropGeometry?
    private let initial: CropGeometry

    init(source: CropSource) {
        self.source = source
        let start = CropGeometry.initial(imageSize: CGSize(width: source.image.width, height: source.image.height))
        self.initial = start
        self.geometry = start
    }

    var hasChanges: Bool { geometry != initial }

    func select(_ ratio: CropRatio) { geometry = geometry.withRatio(ratio) }
    func setAngle(_ degrees: Double) { geometry = geometry.withAngle(degrees) }
    func turnQuarter() { geometry = geometry.turnedQuarter() }
    func reset() { geometry = initial }

    /// Accessibility zoom: one step of 1.25×, clamped.
    func zoom(by factor: CGFloat) { geometry = geometry.zoomed(by: factor).normalized() }

    func gestureChanged(translation: CGSize, magnification: CGFloat, windowHeight: CGFloat) {
        let start = gestureStart ?? geometry
        gestureStart = start
        isInteracting = true
        let units = CGSize(width: translation.width / windowHeight, height: translation.height / windowHeight)
        geometry = start.zoomed(by: magnification).panned(by: units).rubberBanded()
    }

    func gestureEnded() {
        gestureStart = nil
        isInteracting = false
        geometry = geometry.normalized()
    }

    func render() async throws -> CGImage {
        let image = source.image, g = geometry
        return try await Task.detached(priority: .userInitiated) {
            try PhotoCropRenderer.render(image, geometry: g)
        }.value
    }
}
```

Note `rubberBanded` (not `normalized`) during the gesture: a pinch below covering also only shows a share of the undershoot; release settles it.

- [ ] **Step 4: Run to verify it passes.**
- [ ] **Step 5: Commit** — `feat(ios): the crop editor's model`.

### Task 8: `CropRatioChip` and `AngleRuler`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropRatioChip.swift`, `apps/ios/Pebbles/Features/PebbleMedia/Crop/AngleRuler.swift`

No unit test: these are drawing; Task 11's references cover them.

- [ ] **Step 1: `CropRatioChip`**

```swift
import SwiftUI

/// One format (spec "Tool panel · Ratio"): the shape itself, hand-drawn, no
/// name. Filled when selected.
struct CropRatioChip: View {
    let ratio: CropRatio
    let isSelected: Bool
    let action: () -> Void

    /// The long side of the drawn shape.
    private static let shapeSize: CGFloat = 30

    var body: some View {
        Button(action: action) {
            let w = Self.shapeSize * min(ratio.value, 1)
            let h = Self.shapeSize / max(ratio.value, 1)
            ZStack {
                WobblyRectangle(seed: ratio.rawValue)
                    .fill(isSelected ? Color.pebbles.primaryContainer : Color.clear)
                WobblyRectangle(seed: ratio.rawValue)
                    .stroke(isSelected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant,
                            style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
            .frame(width: w, height: h)
            .frame(width: 56, height: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(ratio.width) by \(ratio.height)"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
```

- [ ] **Step 2: `AngleRuler`**

```swift
import SwiftUI

/// The straighten ruler (spec "Tool panel · Rotate"): −45…45°, ticks drawn by
/// hand under a fixed needle. A haptic per whole degree, a snap at 0°,
/// double-tap to return to 0°. Adjustable for VoiceOver, 1° per swipe.
struct AngleRuler: View {
    let angle: Double
    let onChange: (Double) -> Void
    let onEditingChanged: (Bool) -> Void

    private static let pointsPerDegree: CGFloat = 8
    private static let snapWithin: Double = 0.75

    @State private var dragStartAngle: Double?

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text("\(Int(angle.rounded()))°")
                .pebblesFont(.subhead)
                .monospacedDigit()
                .foregroundStyle(Color.pebbles.onSurface)
            ticks
                .frame(height: 36)
                .overlay { needle }
                .contentShape(Rectangle())
                .gesture(drag)
                .onTapGesture(count: 2) { onChange(0) }
        }
        .sensoryFeedback(.selection, trigger: Int(angle.rounded())) { _, _ in HapticsPreference.isOn() }
        .accessibilityElement()
        .accessibilityLabel(Text("Angle"))
        .accessibilityValue(Text("\(Int(angle.rounded())) degrees"))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: onChange(min(angle.rounded() + 1, CropGeometry.maxAngle))
            case .decrement: onChange(max(angle.rounded() - 1, -CropGeometry.maxAngle))
            @unknown default: break
            }
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let start = dragStartAngle ?? angle
                if dragStartAngle == nil {
                    dragStartAngle = start
                    onEditingChanged(true)
                }
                // Ticks slide under the needle: dragging left raises the angle.
                var next = start - Double(value.translation.width / Self.pointsPerDegree)
                next = min(max(next, -CropGeometry.maxAngle), CropGeometry.maxAngle)
                if abs(next) < Self.snapWithin { next = 0 }
                onChange(next)
            }
            .onEnded { _ in
                dragStartAngle = nil
                onEditingChanged(false)
            }
    }

    private var needle: some View {
        Capsule()
            .fill(Color.pebbles.primary)
            .frame(width: 3, height: 40)
    }

    private var ticks: some View {
        Canvas { context, size in
            let centreX = size.width / 2 - CGFloat(angle) * Self.pointsPerDegree
            for degree in -45...45 {
                var rng = SeededGenerator(seed: "angle-tick-\(degree)")
                let major = degree % 15 == 0, mid = degree % 5 == 0
                let base: CGFloat = degree == 0 ? 30 : major ? 24 : mid ? 18 : 11
                let length = base * (1 + rng.unit() * 0.1)
                let x = centreX + CGFloat(degree) * Self.pointsPerDegree + rng.unit() * 0.6
                guard x > -4, x < size.width + 4 else { continue }
                let lean = rng.unit() * 0.9
                let bottom = size.height / 2 + length / 2
                var tick = Path()
                tick.move(to: CGPoint(x: x - lean, y: bottom))
                tick.addLine(to: CGPoint(x: x + lean, y: bottom - length))
                let colour = degree == 0 || major ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant
                context.stroke(tick, with: .color(colour),
                               style: StrokeStyle(lineWidth: degree == 0 ? 2.5 : mid ? 1.8 : 1.3, lineCap: .round))
            }
        }
        .mask(LinearGradient(colors: [.clear, .black, .black, .clear],
                             startPoint: .leading, endPoint: .trailing))
    }
}
```

Check `pebblesFont(.subhead)` exists (`Theme/Font+Pebbles.swift`; the scaffold uses it). `.mask` with `.black` is a mask alpha, not a colour role; if `ThemeLiteralsTests` flags it, use `Color.pebbles.onSurface` as the scaffold's `fadeMask` does.

- [ ] **Step 3: Build** (`npm run build --workspace=@pbbls/ios` or the test command on `PhotoCropModelTests`). Expected: compiles.
- [ ] **Step 4: Commit** — `feat(ios): hand-drawn ratio chips and angle ruler`.

### Task 9: `CropCanvas`

**Files:** Create `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropCanvas.swift`

- [ ] **Step 1: Implement**

```swift
import SwiftUI

/// The photo under a fixed window (spec "Canvas"). The image is placed by the
/// same chain the renderer uses — scale, turn, offset — so what is in the
/// window is exactly what Done keeps.
struct CropCanvas: View {
    @Bindable var model: PhotoCropModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @GestureState private var isGesturing = false

    var body: some View {
        GeometryReader { proxy in
            let window = windowRect(in: proxy.size)
            let g = model.geometry
            let unit = window.height
            ZStack {
                Image(decorative: model.source.image, scale: 1)
                    .resizable()
                    .interpolation(.high)
                    .frame(width: g.imageSize.width * g.scale * unit, height: g.imageSize.height * g.scale * unit)
                    .rotationEffect(.radians(g.theta))
                    .offset(x: g.windowOffset.width * unit, y: g.windowOffset.height * unit)
                    .position(x: window.midX, y: window.midY)
                scrim(window: window, in: proxy.size)
                if model.isInteracting || model.isRotating {
                    CropGrid(divisions: model.isRotating ? 6 : 3)
                        .stroke(Color.pebbles.surface.opacity(0.6), lineWidth: 0.75)
                        .frame(width: window.width, height: window.height)
                        .position(x: window.midX, y: window.midY)
                }
                Rectangle()
                    .stroke(Color.pebbles.outlineVariant, lineWidth: 1)
                    .frame(width: window.width, height: window.height)
                    .position(x: window.midX, y: window.midY)
            }
            .contentShape(Rectangle())
            .gesture(gesture(windowHeight: unit))
            .animation(reduceMotion ? nil : .spring(duration: 0.3), value: model.geometry.ratio)
            .accessibilityElement()
            .accessibilityLabel(Text("Photo"))
            .accessibilityAction(named: Text("Zoom in")) { model.zoom(by: 1.25) }
            .accessibilityAction(named: Text("Zoom out")) { model.zoom(by: 0.8) }
        }
        .clipped()
    }

    /// The largest window of the current format that fits with a gutter.
    private func windowRect(in size: CGSize) -> CGRect {
        let inset = Spacing.lg
        let available = CGSize(width: max(size.width - inset * 2, 1), height: max(size.height - inset * 2, 1))
        let ratio = model.geometry.ratio.value
        let height = min(available.height, available.width / ratio)
        let width = height * ratio
        return CGRect(x: (size.width - width) / 2, y: (size.height - height) / 2, width: width, height: height)
    }

    private func scrim(window: CGRect, in size: CGSize) -> some View {
        Path { path in
            path.addRect(CGRect(origin: .zero, size: size))
            path.addRect(window)
        }
        .fill(Color.pebbles.surface.opacity(0.78), style: FillStyle(eoFill: true))
        .allowsHitTesting(false)
    }

    private func gesture(windowHeight: CGFloat) -> some Gesture {
        SimultaneousGesture(DragGesture(minimumDistance: 0), MagnifyGesture())
            .updating($isGesturing) { _, state, _ in state = true }
            .onChanged { value in
                model.gestureChanged(translation: value.first?.translation ?? .zero,
                                     magnification: value.second?.magnification ?? 1,
                                     windowHeight: windowHeight)
            }
            .onEnded { _ in
                if reduceMotion {
                    model.gestureEnded()
                } else {
                    withAnimation(.spring(duration: 0.35, bounce: 0.15)) { model.gestureEnded() }
                }
            }
    }
}

/// Lines dividing a rect into `divisions` × `divisions`.
struct CropGrid: Shape {
    let divisions: Int

    func path(in rect: CGRect) -> Path {
        var path = Path()
        for i in 1..<divisions {
            let x = rect.minX + rect.width * CGFloat(i) / CGFloat(divisions)
            let y = rect.minY + rect.height * CGFloat(i) / CGFloat(divisions)
            path.move(to: CGPoint(x: x, y: rect.minY)); path.addLine(to: CGPoint(x: x, y: rect.maxY))
            path.move(to: CGPoint(x: rect.minX, y: y)); path.addLine(to: CGPoint(x: rect.maxX, y: y))
        }
        return path
    }
}
```

Two traps to check while running it: (1) `SimultaneousGesture.onEnded` fires once when the last of the two ends — verify a pinch that releases one finger first does not snap mid-gesture; if it does, end on `DragGesture` only. (2) `.offset` must come **after** `.rotationEffect` so the offset is not itself rotated (the geometry already rotated it).

- [ ] **Step 2: Build.** Expected: compiles.
- [ ] **Step 3: Commit** — `feat(ios): the crop canvas`.

### Task 10: `PhotoCropEditor` and its strings

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Implement the screen**

```swift
import SwiftUI
import os

/// The crop editor (spec "Layout"): follows the app theme, top bar, canvas,
/// the current tool's panel, then the tool tabs in thumb reach. Filters will
/// be a third `CropTool` and tab.
struct PhotoCropEditor: View {
    @State private var model: PhotoCropModel
    @State private var isRendering = false
    let onCancel: () -> Void
    let onDone: (CGImage, Date?) -> Void

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "crop-editor")

    init(source: CropSource, onCancel: @escaping () -> Void, onDone: @escaping (CGImage, Date?) -> Void) {
        _model = State(initialValue: PhotoCropModel(source: source))
        self.onCancel = onCancel
        self.onDone = onDone
    }

    var body: some View {
        VStack(spacing: 0) {
            topBar
            CropCanvas(model: model)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            toolPanel
                .frame(height: 96)
                .padding(.horizontal, Spacing.lg)
            tabs
                .padding(.bottom, Spacing.sm)
        }
        .background(Color.pebbles.surface)
        .pebblesScreen()
    }

    private var topBar: some View {
        HStack {
            PebbleToolbarButton("Cancel", action: onCancel)
            Spacer()
            if model.hasChanges {
                Button("Reset") { withAnimation(.snappy) { model.reset() } }
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            Spacer()
            PebbleToolbarButton("Done") { Task { await done() } }
                .disabled(isRendering)
        }
        .padding(.horizontal, Spacing.lg)
        .frame(height: 52)
    }

    @ViewBuilder
    private var toolPanel: some View {
        switch model.tool {
        case .ratio:
            HStack(spacing: Spacing.sm) {
                ForEach(CropRatio.allCases) { ratio in
                    CropRatioChip(ratio: ratio, isSelected: model.geometry.ratio == ratio) {
                        withAnimation(.snappy) { model.select(ratio) }
                    }
                }
            }
            .frame(maxWidth: .infinity)
        case .rotate:
            HStack(spacing: Spacing.md) {
                Button { withAnimation(.snappy) { model.turnQuarter() } } label: {
                    PbblsGlyphView(.againBackward, size: 22, style: AnyShapeStyle(Color.pebbles.onSurface))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Rotate 90°"))
                AngleRuler(angle: model.geometry.angle,
                           onChange: { model.setAngle($0) },
                           onEditingChanged: { model.isRotating = $0 })
            }
        }
    }

    private var tabs: some View {
        HStack(spacing: Spacing.xxl) {
            tab(.ratio, glyph: .rectangleDashed, label: "Ratio")
            tab(.rotate, glyph: .setSquare, label: "Rotate")
        }
        .frame(maxWidth: .infinity)
    }

    private func tab(_ tool: CropTool, glyph: PbblsGlyph, label: LocalizedStringResource) -> some View {
        let selected = model.tool == tool
        let colour = selected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant
        return Button { model.tool = tool } label: {
            VStack(spacing: Spacing.xs) {
                PbblsGlyphView(glyph, size: 24, style: AnyShapeStyle(colour))
                Text(label).pebblesFont(.caption).foregroundStyle(colour)
            }
            .frame(minWidth: 64, minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func done() async {
        isRendering = true
        defer { isRendering = false }
        do {
            let image = try await model.render()
            onDone(image, model.source.captureDate)
        } catch {
            logger.error("crop render failed: \(String(describing: error), privacy: .public)")
        }
    }
}
```

Before compiling, confirm against the code: the `PbblsGlyph` type name and the `PbblsGlyphView(_:size:style:)` initialiser (used that way in `RecordPhotoControls`), `pebblesFont(.caption)` (else `.footnote`), `.pebblesScreen()`, and that `PebbleToolbarButton` renders outside a `.toolbar` (it is meant for toolbar items; if it looks wrong in a plain `HStack`, use `Button` with `.pebblesFont(.body)` and `Color.pebbles.primary` for Done). A render failure leaves the editor open (logged); there is no error UI because the renderer only fails on context allocation.

- [ ] **Step 2: Strings.** Add these keys to `Localizable.xcstrings` (insert as text at the alphabetical anchor, never re-dump the JSON; mirror the shape of the existing `"Done"` entry, `extractionState` omitted so the build marks them extracted):

| Key | en | fr |
|---|---|---|
| `Ratio` | Ratio | Format |
| `Rotate` | Rotate | Rotation |
| `Rotate 90°` | Rotate 90° | Pivoter de 90° |
| `Reset` | Reset | Réinitialiser |
| `Angle` | Angle | Angle |
| `%lld degrees` | %lld degrees | %lld degrés |
| `%lld by %lld` | %lld by %lld | %lld sur %lld |
| `%lld°` | %lld° | %lld° |
| `Photo` | (check it exists; add if not) | Photo |
| `Zoom in` | Zoom in | Zoomer |
| `Zoom out` | Zoom out | Dézoomer |

Then build once and open the catalog in Xcode: no entry `New` or `Stale`, both columns filled (`LocalizationTests` runs too).

- [ ] **Step 3: Build and run** `LocalizationTests`, `ThemeLiteralsTests`. Pass.
- [ ] **Step 4: Commit** — `feat(ios): the photo crop editor screen`.

### Task 11: Previews and references

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift` (add `#Preview`)
- Create: `apps/ios/PebblesTests/References/PhotoCropReferences.swift`

- [ ] **Step 1: A fixture image and a preview.** Add at the bottom of `PhotoCropEditor.swift`:

```swift
#if DEBUG
extension CropSource {
    /// A 3:4 gradient with a marked corner, for previews and references.
    static var fixture: CropSource {
        let width = 768, height = 1024
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        let colours = [CGColor(red: 0.85, green: 0.6, blue: 0.4, alpha: 1),
                       CGColor(red: 0.3, green: 0.45, blue: 0.6, alpha: 1)] as CFArray
        let gradient = CGGradient(colorsSpace: space, colors: colours, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: width, y: height), options: [])
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 40, y: height - 200, width: 160, height: 160))
        return CropSource(image: ctx.makeImage()!, captureDate: nil)
    }
}

#Preview {
    PhotoCropEditor(source: .fixture, onCancel: {}, onDone: { _, _ in })
}
#endif
```

`CGColor(red:…)` literals sit inside `#if DEBUG` fixture code; if `ThemeLiteralsTests` scans it, move the fixture into `PebblesTests/Crop/` and drop the preview's dependency on it.

- [ ] **Step 2: References.** Follow `RecordFlowReferences.swift`'s shape:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The crop editor (#1654): each tool, both schemes, English and French on Paper.
    @Suite("Photo crop references") @MainActor
    struct PhotoCropReferences {
        private static let height: CGFloat = 760

        private static func config(_ scheme: ReferenceScheme) -> ReferenceConfig {
            ReferenceConfig(scheme: scheme, locales: scheme == .paper ? [.en, .fr] : [.en])
        }

        private static func editor(tool: CropTool) -> some View {
            PhotoCropEditor(source: .fixture, onCancel: {}, onDone: { _, _ in }, initialTool: tool)
                .frame(width: ReferenceConfig.phoneWidth, height: height)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func ratio(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.editor(tool: .ratio), named: "cropRatio", config: Self.config(scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func rotate(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(Self.editor(tool: .rotate), named: "cropRotate", config: Self.config(scheme))
        }
    }
}
```

This needs an `initialTool: CropTool = .ratio` parameter on `PhotoCropEditor.init` that sets `model.tool` — add it (one line in `init`: `let model = PhotoCropModel(source: source); model.tool = initialTool; _model = State(initialValue: model)`).

- [ ] **Step 3: Run locally once** (`-only-testing:PebblesTests/References`): new references fail with "no reference" — expected; the PNGs are recorded by CI. Check the `.actual.png` under `build/reference-diffs/` by eye: chips wobble, the ruler's 0° tick is under the needle, light and dark both read.
- [ ] **Step 4: Commit** — `test(ios): references for the crop editor`. After the PR is open, add the `rebaseline-ios-screenshots` label **alone** to record them.

### Part 2 PR

`feat(ios): the photo crop editor` — `Part of #1654`, stacked on Part 1, `Data safety: no change`.

---

# Part 3 — wiring and the photo step (`feat/1654-crop-wiring-ios`)

### Task 12: Capture flow — pick and camera go through the editor

**Files:** Modify `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`

- [ ] **Step 1: State.** Next to `@State private var pickedPhoto`:

```swift
    /// The photo waiting in the crop editor (spec D1): set once its bytes are
    /// read, cleared by Cancel or Done.
    @State private var cropping: CropSource?
    /// True from a pick's tap until its bytes are read and the editor opens.
    @State private var isLoadingCrop = false
```

- [ ] **Step 2: Pick → load → editor.** Replace the `.onChange(of: pickedPhoto)` body:

```swift
        .onChange(of: pickedPhoto) { _, picked in
            guard let picked else { return }
            isLoadingCrop = true
            Task {
                let source = await CropSource.make(from: picked)
                isLoadingCrop = false
                if let source { cropping = source } else { pickedPhoto = nil }
            }
        }
```

- [ ] **Step 3: Camera → load → editor.** In the `CameraPicker` `onCapture`, replace the `Task { await snaps.handleCaptured(…) }` line (keep `model.applyCaptureDate(shutter)`):

```swift
                    guard let data = image.jpegData(compressionQuality: 0.9) else { return }
                    model.applyCaptureDate(shutter)
                    Task { cropping = await CropSource.make(from: data) }
```

(Drop the now-unused `userId`/`snaps` bindings from that `guard`.)

- [ ] **Step 4: Present the editor.** After the camera's `.fullScreenCover`:

```swift
        .fullScreenCover(item: $cropping) { source in
            PhotoCropEditor(
                source: source,
                onCancel: {
                    cropping = nil
                    pickedPhoto = nil
                },
                onDone: { image, date in
                    cropping = nil
                    guard let userId = currentUserId, let snaps else { return }
                    // Never cancelled: once the cover closes or saves, the
                    // coordinator rejects the pick and deletes what it uploaded.
                    Task { await snaps.ingest(cropped: image, captureDate: date, userId: userId) }
                }
            )
        }
```

The existing `.onChange(of: snaps?.formSnap)` auto-advance and `.onChange(of: snaps?.pickedCaptureDate)` work unchanged: `ingest(cropped:)` sets both.

- [ ] **Step 5: Loading veil.** In `stepBody`'s `.photo` case, overlay the picker while bytes load:

```swift
        case .photo:
            RecordPhotoStep(selection: $pickedPhoto)
                .overlay {
                    if isLoadingCrop {
                        ProgressView()
                            .tint(Color.pebbles.primary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.pebbles.surface.opacity(0.6))
                    }
                }
```

(Keep whatever already follows `RecordPhotoStep` in that case.)

- [ ] **Step 6: Build**, then run `CaptureFlowModelTests` and the `Capture` test folder suites. Pass.
- [ ] **Step 7: Commit** — `feat(ios): open the crop editor after a pick or a shot in capture`.

### Task 13: Edit moment — pick goes through the editor

**Files:** Modify `apps/ios/Pebbles/Features/Path/EditMomentSheet.swift`

- [ ] **Step 1:** Add `@State private var cropping: CropSource?` and replace the picker sheet's handler:

```swift
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                guard let picked else { return }
                Task { cropping = await CropSource.make(from: picked) }
            }
        }
        .fullScreenCover(item: $cropping) { source in
            PhotoCropEditor(
                source: source,
                onCancel: { cropping = nil },
                onDone: { image, date in
                    cropping = nil
                    guard let userId = currentUserId, let snaps else { return }
                    Task { await snaps.ingest(cropped: image, captureDate: date, userId: userId) }
                }
            )
        }
```

The picker sheet must be fully dismissed before the cover presents; the `await` for the bytes normally covers that. If the cover fails to appear on device ("already presenting"), set `cropping` in the picker sheet's `onDismiss:` instead.

- [ ] **Step 2: Build; commit** — `feat(ios): open the crop editor after a pick in edit moment`.

### Task 14: Remove the now-unused pick loaders

**Files:** Modify `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`

- [ ] **Step 1:** Confirm no callers: `grep -rn "handlePicked\|loadData(from" apps/ios/Pebbles apps/ios/PebblesTests`. Only the definitions remain.
- [ ] **Step 2:** Delete both `handlePicked` overloads, `processPick` (if now uncalled — check `grep -rn processPick`), and the private `loadData(from:uti:)`. Remove `import PhotosUI` if nothing else needs it. Keep `handleCaptured` (Home's camera) and `ingest(_:uti:userId:)` (tests, `handleCaptured`).
- [ ] **Step 3:** Run `SnapUploadCoordinatorTests`, `SnapUploadCoordinatorPickTests`. Pass.
- [ ] **Step 4: Commit** — `quality(ios): drop the pick loaders the crop source replaced`.

### Task 15: Photo step controls in thumb reach

**Files:** Modify `apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift`, `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoControls.swift` (doc comment only)

- [ ] **Step 1:** In the scaffold's `contentFillsHeight` branch, move `fixedSlot` below the content:

```swift
            if contentFillsHeight {
                VStack(spacing: Spacing.md) {
                    content()
                        .padding(.horizontal, contentHasGutters ? Spacing.lg : 0)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .overlay(alignment: .bottom) { floatingAction }
                    if let fixedSlot {
                        fixedSlot
                            .padding(.horizontal, Spacing.lg)
                            .padding(.bottom, Spacing.sm)
                    }
                }
                .padding(.bottom, fixedSlot == nil && contentHasGutters ? Spacing.sm : 0)
            }
```

and update the `fixedSlot` doc comment: "Drawn under a `contentFillsHeight` content, in thumb reach, never scrolled. The photo step's two controls."

- [ ] **Step 2:** Update `RecordPhotoControls`'s doc comment ("fixed-slot controls" → "the controls under the grid"). The `HStack` stays: "Just write" and the camera side by side, now at the bottom.
- [ ] **Step 3:** Run the References suite locally for `RecordFlowReferences` and compare against a baseline run on the untouched branch (27 references fail on this Mac on main — gate by byte-identity of the failing set, not by green). Only Life/moment pages must be unchanged; the photo step has no reference.
- [ ] **Step 4: Commit** — `feat(ios): move just write and the camera under the photo grid`.

### Task 16: Verify on the simulator

- [ ] **Step 1:** Build and run on "iPhone 17 crop" (`mcp__Claude_Code_iOS_Simulator__control` attach first, then launch). The maintainer signs in to a throwaway account; drive the flow:
  1. Record → pick a portrait photo → editor opens on 3:4 → choose 1:1 → pan to an edge (it resists, settles inside) → Rotate tab → drag the ruler to ~12° (no empty corner, haptic ticks) → 90° (format flips to its pair) → Done → moment page with the cropped thumbnail, uploading then ready.
  2. Pick → Cancel → back on the picker, nothing attached; pick the same photo again → editor reopens.
  3. Camera (device only) → editor → Done.
  4. Path → a moment → Edit → Add a photo → editor → Done → Save; the Spine card shows the new shape (ratio read from the file).
  5. Dark mode: the editor follows it. VoiceOver: chips read "4 by 5", the ruler adjusts by 1°.
- [ ] **Step 2:** Screenshot each tool in both schemes for the PR and the build journal.

### Part 3 PR

`feat(ios): crop and rotate a photo before it uploads` — body starts `Resolves #1654`, `Data safety: no change`, screenshots. Labels `feat`, `ios`, `ui`; milestone `M70 · Record flow`. Then open the Android mirror issue (`[Feat] Crop and rotate a photo before it uploads on Android`, labels `feat`, `android`, `ui`) and drop the build-journal pebble (`--size medium --valence highlight --collection iOS`).

---

## Lessons learned

(Filled after ship.)
