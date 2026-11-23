# Photo filters on iOS — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Filter and Adjust tools to the iOS photo editor: seven presets, one intensity, three extras, and the lab's sliders as offsets. The look is baked into the uploaded JPEG.

**Architecture:** `FilterPreset` and `PhotoLook` are pure values. `PhotoLookModel` renders through the lab's `FilterPipeline` on the 1024 px crop. The crop editor gains two `CropTool` cases and swaps its canvas and panel for them. Done crops, then applies the look.

**Tech stack:** SwiftUI (iOS 17), Core Image through `FilterPipeline`, Vision through `FilterMasks`, Swift Testing, XcodeGen.

Spec: `docs/superpowers/specs/2026-11-23-ios-photo-filters-design.md`. Issue #1680, milestone M70 · Record flow.

---

## Ground rules for every task

- Work in `apps/ios`. Read `apps/ios/CLAUDE.md`. `project.yml` is the source of truth, so run `xcodegen generate` after adding files (the commands below do).
- Test one suite:

  ```bash
  cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=FilterLab iPhone 17' -derivedDataPath build/dd-filterlab -only-testing:PebblesTests/<Suite> 2>&1 | tail -40
  ```

- If a build serves stale objects after `xcodegen`, delete `apps/ios/build/dd-filterlab/Build` and rerun.
- **Lint only the files you touched:** `cd apps/ios && swiftlint lint --quiet <paths>`. Never lint the whole folder: the gitignored `build/` folders flood it.
- **Colours** are `Color.pebbles.<role>` only, never `.clear`, literals or `Color(red:`, because `ThemeLiteralsTests` fails on anything else. No `role: .destructive` in `Features/`.
- **Strings:** user-facing strings use `LocalizedStringResource` or literal `Text("…")`. Task 6 adds every new key to `Localizable.xcstrings` in en and fr.
- **Reference images are a CI-only gate.** 27 references fail locally on an untouched main on this Mac, so never re-baseline locally. Re-render on CI by adding the label `rebaseline-ios-screenshots` to the PR, one label at a time.
- **Commits:** conventional, lowercase, ending with the `Co-Authored-By` line the session gives.
- **Branches:** each part is one branch and one PR, chained with `gh stack` (read the `gh-stack` skill). Branch names carry `-ios`: `feat/1680-photo-look-model-ios`, `feat/1680-filter-tool-ios`, `feat/1680-adjust-tool-ios`.

## File map

| File | Part | Responsibility |
|---|---|---|
| `Pebbles/Features/PebbleMedia/Filters/AdjustControl.swift` | 1 | The eleven Adjust controls: key path into `FilterParams`, range, label |
| `Pebbles/Features/PebbleMedia/Filters/FilterPreset.swift` | 1 | The seven presets and their values |
| `Pebbles/Features/PebbleMedia/Filters/PhotoLook.swift` | 1 | Preset + intensity + extras + Adjust offsets → `FilterParams` |
| `PebblesTests/Filters/FilterPresetTests.swift`, `PhotoLookTests.swift` | 1 | Tests |
| `PebblesTests/Filters/FilterTestImages.swift` | 1 | Gains `meanChroma` |
| `Pebbles/Features/PebbleMedia/Filters/PhotoLookModel.swift` | 2 (3 extends) | Editor state: crop base, thumbnails, coalesced preview, Done render, masks |
| `Pebbles/Features/PebbleMedia/Filters/LookCanvas.swift` | 2 | The canvas on Filter and Adjust: the preview, hold for the original |
| `Pebbles/Features/PebbleMedia/Filters/LookSlider.swift` | 2 | One labelled slider with readout, double-tap reset |
| `Pebbles/Features/PebbleMedia/Filters/FilterToolPanel.swift` | 2 | Preset row + four knobs |
| `Pebbles/Features/PebbleMedia/Filters/AdjustToolPanel.swift` | 3 | The eleven sliders |
| `Pebbles/Features/PebbleMedia/Crop/PhotoCropModel.swift` | 2, 3 | `CropTool` gains `.filter`, then `.adjust` |
| `Pebbles/Features/PebbleMedia/Crop/CropToolGroup.swift` | 2, 3 | Labels (Ratio → Reframe) and glyphs |
| `Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift` | 2, 3 | Canvas and panel switch, per-tab Reset, Done applies the look |
| `Pebbles/Resources/Localizable.xcstrings` | 2, 3 | New strings, en + fr |
| `PebblesTests/Filters/PhotoLookModelTests.swift` | 2, 3 | Model tests |
| `PebblesTests/References/PhotoCropReferences.swift` | 2, 3 | Filter and Adjust references |

---

# Part 1 — `FilterPreset` and `PhotoLook` (branch `feat/1680-photo-look-model-ios`, from `main`)

### Task 1: `AdjustControl`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/AdjustControl.swift`
- Test: `apps/ios/PebblesTests/Filters/PhotoLookTests.swift`. The file is created here and grows in Task 3.

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

@Suite("PhotoLook")
struct PhotoLookTests {
    @Test("Adjust covers the ten tone and colour controls plus refocus, never the extras")
    func adjustControls() {
        #expect(AdjustControl.allCases.count == 11)
        var probe = FilterParams.neutral
        for control in AdjustControl.allCases { probe[keyPath: control.keyPath] = 0.5 }
        #expect(probe.vignette == 0 && probe.blurVignette == 0 && probe.grain == 0)
        #expect(probe.tint == 0.5 && probe.tone == 0.5 && probe.refocus == 0.5)
        #expect(AdjustControl.sharpness.range == 0...1 && AdjustControl.refocus.range == 0...1)
        #expect(AdjustControl.exposure.range == -1...1)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run the suite `PhotoLookTests`. Expected: a compile error, "cannot find 'AdjustControl' in scope".

- [ ] **Step 3: Implement**

```swift
import Foundation

/// The Adjust tool's sliders (spec 2026-11-23 "Adjust"), in pipeline order. Each one
/// writes one `FilterParams` value; vignette, blur and grain belong to the Filter tool.
enum AdjustControl: CaseIterable, Identifiable, Sendable {
    case exposure, brilliance, highlights, shadows, contrast, saturation, vibrance, warmth, tint, sharpness, refocus

    var id: Self { self }

    var keyPath: WritableKeyPath<FilterParams, Double> {
        switch self {
        case .exposure: \.exposure
        case .brilliance: \.brilliance
        case .highlights: \.highlights
        case .shadows: \.shadows
        case .contrast: \.contrast
        case .saturation: \.saturation
        case .vibrance: \.vibrance
        case .warmth: \.tone
        case .tint: \.tint
        case .sharpness: \.sharpness
        case .refocus: \.refocus
        }
    }

    var range: ClosedRange<Double> {
        switch self {
        case .sharpness, .refocus: 0...1
        default: -1...1
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .exposure: "Exposure"
        case .brilliance: "Brilliance"
        case .highlights: "Highlights"
        case .shadows: "Shadows"
        case .contrast: "Contrast"
        case .saturation: "Saturation"
        case .vibrance: "Vibrance"
        case .warmth: "Warmth"
        case .tint: "Tint"
        case .sharpness: "Sharpness"
        case .refocus: "Refocus"
        }
    }
}
```

- [ ] **Step 4: Run it and watch it pass.** Run the suite `PhotoLookTests`. Expected: 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/AdjustControl.swift apps/ios/PebblesTests/Filters/PhotoLookTests.swift
git commit -m "feat(ios): add the adjust controls for photo looks"
```

### Task 2: `FilterPreset`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterPreset.swift`
- Modify: `apps/ios/PebblesTests/Filters/FilterTestImages.swift` (add `meanChroma` after `lumaStdDev`)
- Test: `apps/ios/PebblesTests/Filters/FilterPresetTests.swift`

- [ ] **Step 1: Add the chroma helper** to `FilterTestImages`:

```swift
    /// Mean per-pixel chroma: max − min of red, green and blue, 0…1.
    static func meanChroma(_ image: CGImage) throws -> Double {
        let rgba = try bytes(image)
        var total = 0.0
        for i in stride(from: 0, to: rgba.count, by: 4) {
            let r = Double(rgba[i]), g = Double(rgba[i + 1]), b = Double(rgba[i + 2])
            total += (max(r, g, b) - min(r, g, b)) / 255
        }
        return total / Double(rgba.count / 4)
    }
```

- [ ] **Step 2: Write the failing tests**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

private typealias Img = FilterTestImages

@Suite("FilterPreset")
struct FilterPresetTests {
    @Test("seven presets, in the row's order")
    func order() {
        #expect(FilterPreset.allCases == [.everyday, .vivid, .soft, .lift, .mono, .monoDeep, .memory])
    }

    @Test("every preset stays inside each control's range and leaves the extras to the knobs",
          arguments: FilterPreset.allCases)
    func inRange(preset: FilterPreset) {
        let p = preset.params
        for control in AdjustControl.allCases {
            #expect(control.range.contains(p[keyPath: control.keyPath]), "\(control)")
        }
        #expect(p.vignette == 0 && p.blurVignette == 0 && p.grain == 0 && p.refocus == 0 && !p.autoEnhance)
    }

    @Test("the black and white presets drop the colour", arguments: [FilterPreset.mono, .monoDeep])
    func monoIsGrey(preset: FilterPreset) throws {
        let image = try Img.render(preset.params, on: Img.ramp(size: 64))
        #expect(try Img.meanChroma(image) < 0.01)
    }

    @Test("Vivid raises the colour")
    func vividRaisesChroma() throws {
        let source = Img.ramp(size: 64)
        let image = try Img.render(FilterPreset.vivid.params, on: source)
        #expect(try Img.meanChroma(image) > Img.meanChroma(source))
    }
}
```

- [ ] **Step 3: Run them and watch them fail.** Run the suite `FilterPresetTests`. Expected: a compile error, "cannot find 'FilterPreset' in scope".

- [ ] **Step 4: Implement.** The values are the spec's "The presets" table.

```swift
import Foundation

/// The Filter tool's presets (spec 2026-11-23 "The presets"): the pipeline's values at
/// intensity 1, drawn from the filter lab's looks. Any control not set is 0.
enum FilterPreset: String, CaseIterable, Identifiable, Sendable {
    case everyday, vivid, soft, lift, mono, monoDeep, memory

    var id: Self { self }

    var name: LocalizedStringResource {
        switch self {
        case .everyday: "Everyday"
        case .vivid: "Vivid"
        case .soft: "Soft"
        case .lift: "Lift"
        case .mono: "Mono"
        case .monoDeep: "Mono Deep"
        case .memory: "Memory"
        }
    }

    var params: FilterParams {
        var p = FilterParams()
        switch self {
        case .everyday:
            p.exposure = 0.08; p.brilliance = -0.05; p.shadows = -0.19; p.contrast = 0.06
            p.saturation = -0.09; p.vibrance = 0.16; p.tone = -0.08; p.sharpness = 0.20
        case .vivid:
            p.exposure = 0.13; p.brilliance = 0.22; p.highlights = -0.04; p.shadows = -0.22; p.contrast = 0.11
            p.saturation = 0.10; p.vibrance = 0.30; p.tone = -0.25; p.sharpness = 0.22
        case .soft:
            p.exposure = 0.23; p.brilliance = 0.18; p.highlights = -0.26; p.shadows = 0.09
            p.vibrance = -0.08; p.tone = -0.29; p.sharpness = 0.30
        case .lift:
            p.exposure = 0.35; p.brilliance = 0.30; p.highlights = 0.36; p.shadows = -0.15; p.contrast = 0.09
            p.saturation = -0.21; p.vibrance = 0.22; p.tone = -0.10; p.sharpness = 0.38
        case .mono:
            p.exposure = 0.21; p.brilliance = 0.14; p.highlights = 0.25; p.shadows = -0.08; p.contrast = 0.12
            p.saturation = -1; p.sharpness = 0.29
        case .monoDeep:
            p.exposure = 0.18; p.brilliance = 0.35; p.highlights = 0.38; p.shadows = -0.32; p.contrast = 0.36
            p.saturation = -1; p.sharpness = 0.43
        case .memory:
            p.exposure = 0.20; p.brilliance = 0.15; p.highlights = -0.08; p.shadows = 0.15; p.contrast = -0.08
            p.saturation = -1; p.tone = 0.15; p.sharpness = 0.10
        }
        return p
    }
}
```

If swiftlint flags `statement_position` or the line length on the `;`-joined lines, put one assignment per line. Do not disable the rule.

- [ ] **Step 5: Run them and watch them pass.** Run the suite `FilterPresetTests`. Expected: every test passes, 12 cases in all.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterPreset.swift apps/ios/PebblesTests/Filters/FilterPresetTests.swift apps/ios/PebblesTests/Filters/FilterTestImages.swift
git commit -m "feat(ios): add the seven photo filter presets"
```

### Task 3: `PhotoLook`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLook.swift`
- Test: `apps/ios/PebblesTests/Filters/PhotoLookTests.swift` (add to the suite from Task 1)

- [ ] **Step 1: Write the failing tests** inside `PhotoLookTests`:

```swift
    @Test("untouched resolves to neutral and is the identity")
    func untouched() {
        #expect(PhotoLook.untouched.resolve() == .neutral)
        #expect(PhotoLook.untouched.isIdentity)
        #expect(!PhotoLook.untouched.hasFilterChanges && !PhotoLook.untouched.hasAdjustChanges)
    }

    @Test("intensity 1 is the preset, intensity 0 is the photo plus the extras")
    func intensity() {
        var look = PhotoLook.untouched
        look.select(.mono)
        var expected = FilterPreset.mono.params
        expected.vignette = 0.1; expected.blurVignette = 0.2; expected.grain = 0.4
        #expect(look.resolve() == expected)
        look.intensity = 0
        var extrasOnly = FilterParams.neutral
        extrasOnly.vignette = 0.1; extrasOnly.blurVignette = 0.2; extrasOnly.grain = 0.4
        #expect(look.resolve() == extrasOnly)
        look.intensity = 0.5
        #expect(look.resolve().saturation == -0.5)
    }

    @Test("picking a preset fills only the extras nobody moved, and never on Original")
    func extrasDefaults() {
        var look = PhotoLook.untouched
        look.select(nil)
        #expect(look.value(of: .grain) == 0)
        look.set(.grain, to: 0.7)
        look.select(.soft)
        #expect(look.value(of: .grain) == 0.7)
        #expect(look.value(of: .vignette) == 0.1 && look.value(of: .blur) == 0.2)
        look.set(.vignette, to: 0)
        look.select(.lift)
        #expect(look.value(of: .vignette) == 0)
    }

    @Test("extras never move with the preset or the intensity")
    func extrasIndependent() {
        var look = PhotoLook.untouched
        look.select(.vivid)
        look.set(.blur, to: 0.55)
        look.intensity = 0.3
        look.select(.memory)
        look.select(nil)
        #expect(look.resolve().blurVignette == 0.55)
    }

    @Test("an Adjust offset adds to the preset and clamps to the range")
    func adjustAddsAndClamps() {
        var look = PhotoLook.untouched
        look.select(.lift)
        look.adjust.exposure = 0.1
        #expect(abs(look.resolve().exposure - 0.45) < 1e-9)
        look.adjust.exposure = 1
        #expect(look.resolve().exposure == 1)
        look.adjust.sharpness = -1
        #expect(look.resolve().sharpness == 0)
        #expect(look.hasAdjustChanges)
    }

    @Test("each tool resets on its own")
    func resets() {
        var look = PhotoLook.untouched
        look.select(.vivid)
        look.adjust.contrast = 0.2
        look.resetFilter()
        #expect(!look.hasFilterChanges && look.hasAdjustChanges)
        #expect(look.resolve().contrast == 0.2)
        look.select(.vivid)
        #expect(look.value(of: .grain) == 0.4)
        look.resetAdjust()
        #expect(!look.hasAdjustChanges)
    }
```

- [ ] **Step 2: Run them and watch them fail.** Run the suite `PhotoLookTests`. Expected: a compile error, "cannot find 'PhotoLook' in scope".

- [ ] **Step 3: Implement**

```swift
import Foundation

/// What the Filter and Adjust tools hold for one photo (spec 2026-11-23 "The knobs",
/// "Adjust"). Resolved value per control: preset × intensity + Adjust offset, clamped.
/// Vignette, blur and grain are the person's own and never follow the preset.
struct PhotoLook: Equatable, Sendable {
    enum Extra: CaseIterable, Sendable {
        case vignette, blur, grain

        /// Set the first time a preset is picked, unless the person moved this extra already.
        var defaultValue: Double {
            switch self {
            case .vignette: 0.1
            case .blur: 0.2
            case .grain: 0.4
            }
        }

        var label: LocalizedStringResource {
            switch self {
            case .vignette: "Vignette"
            case .blur: "Blur"
            case .grain: "Grain"
            }
        }
    }

    private(set) var preset: FilterPreset?
    var intensity: Double = 1
    /// Offsets on top of preset × intensity. Only `AdjustControl` key paths are read.
    var adjust = FilterParams.neutral
    private var vignette: Double = 0
    private var blur: Double = 0
    private var grain: Double = 0
    private var touched: Set<Extra> = []

    static let untouched = PhotoLook()

    var hasFilterChanges: Bool { preset != nil || Extra.allCases.contains { value(of: $0) != 0 } }
    var hasAdjustChanges: Bool { adjust != .neutral }
    var isIdentity: Bool { resolve() == .neutral }

    func value(of extra: Extra) -> Double {
        switch extra {
        case .vignette: vignette
        case .blur: blur
        case .grain: grain
        }
    }

    /// `nil` is Original. Picking a preset fills the extras nobody has moved yet.
    mutating func select(_ preset: FilterPreset?) {
        self.preset = preset
        guard preset != nil else { return }
        for extra in Extra.allCases where !touched.contains(extra) {
            store(extra.defaultValue, in: extra)
        }
    }

    mutating func set(_ extra: Extra, to value: Double) {
        touched.insert(extra)
        store(min(max(value, 0), 1), in: extra)
    }

    mutating func resetFilter() {
        preset = nil
        intensity = 1
        touched = []
        for extra in Extra.allCases { store(0, in: extra) }
    }

    mutating func resetAdjust() { adjust = .neutral }

    func resolve() -> FilterParams {
        var p = FilterParams.neutral
        let strength = min(max(intensity, 0), 1)
        for control in AdjustControl.allCases {
            let base = (preset?.params[keyPath: control.keyPath] ?? 0) * strength
            let sum = base + adjust[keyPath: control.keyPath]
            p[keyPath: control.keyPath] = min(max(sum, control.range.lowerBound), control.range.upperBound)
        }
        p.vignette = vignette
        p.blurVignette = blur
        p.grain = grain
        return p
    }

    private mutating func store(_ value: Double, in extra: Extra) {
        switch extra {
        case .vignette: vignette = value
        case .blur: blur = value
        case .grain: grain = value
        }
    }
}
```

- [ ] **Step 4: Run them and watch them pass.** Run the suites `PhotoLookTests` and `FilterPresetTests`. Expected: all pass.

- [ ] **Step 5: Lint and commit**

```bash
cd apps/ios && swiftlint lint --quiet Pebbles/Features/PebbleMedia/Filters/AdjustControl.swift Pebbles/Features/PebbleMedia/Filters/FilterPreset.swift Pebbles/Features/PebbleMedia/Filters/PhotoLook.swift PebblesTests/Filters/PhotoLookTests.swift PebblesTests/Filters/FilterPresetTests.swift
cd ../.. && git add apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLook.swift apps/ios/PebblesTests/Filters/PhotoLookTests.swift
git commit -m "feat(ios): resolve a photo look from preset, intensity, extras and adjust"
```

- [ ] **Step 6: Open the Part 1 PR.**
  - Run the whole test target once; it must be green apart from the 27 local reference failures.
  - Open the PR with `gh stack`: title `feat(ios): add the photo look model and presets`, body `Part of #1680` (not "Resolves": the issue closes with Part 3), `Data safety: no change`, labels `feat` + `ios`, milestone `M70 · Record flow`.
  - The new strings are not used on screen yet; Task 6 adds them to the catalog.

---

# Part 2 — The Filter tab (branch `feat/1680-filter-tool-ios`, stacked on Part 1)

When picking this part up, move the photo editor's view in the hosted Arkaik map to `development` and add the Filter tool to it (`arkaik` skill; arkaik-mcp needs `ARKAIK_TOKEN` exported). If the arkaik-mcp tools are unavailable, say so and stop: never edit `docs/arkaik/bundle.json`.

### Task 4: `PhotoLookModel`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLookModel.swift`
- Test: `apps/ios/PebblesTests/Filters/PhotoLookModelTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import CoreGraphics
import Testing
@testable import Pebbles

private typealias Img = FilterTestImages

@Suite("PhotoLookModel") @MainActor
struct PhotoLookModelTests {
    private func cropped() throws -> (CGImage, CropGeometry) {
        let crop = PhotoCropModel(source: CropSource(image: Img.ramp(size: 512), captureDate: nil))
        return (try PhotoCropRenderer.render(crop.source.image, geometry: crop.geometry), crop.geometry)
    }

    @Test("prepare renders the preview with the current look and one thumbnail per preset")
    func prepare() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        model.look.select(.mono)
        await model.prepare(image, for: geometry)
        #expect(model.preparedFor == geometry)
        #expect(model.thumbnails.count == FilterPreset.allCases.count)
        #expect(model.originalThumbnail.map { max($0.width, $0.height) } == PhotoLookModel.thumbnailEdge)
        let preview = try #require(model.preview)
        #expect(try Img.meanChroma(preview) < 0.02)
    }

    @Test("Done with an untouched look uploads the crop itself")
    func identityPassesThrough() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        let out = await model.apply(to: image, sameCrop: true)
        #expect(out === image)
    }

    @Test("Done applies the look at upload size")
    func appliesLook() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        model.look.select(.monoDeep)
        let out = await model.apply(to: image, sameCrop: true)
        #expect(out.width == image.width && out.height == image.height)
        #expect(try Img.meanChroma(out) < 0.02)
    }
}
```

The Mono checks allow 0.02, not 0.01, because the default extras add grain and vignette.

- [ ] **Step 2: Run them and watch them fail.** Run the suite `PhotoLookModelTests`. Expected: a compile error, "cannot find 'PhotoLookModel' in scope".

- [ ] **Step 3: Implement**

```swift
import CoreGraphics
import CoreImage
import Observation
import os

/// The Filter and Adjust tools' state (spec 2026-11-23 "Architecture"). It works on the
/// crop at upload size, so the preview is the upload. Look changes coalesce: at most one
/// render runs, and one more follows if the look moved meanwhile.
@Observable @MainActor
final class PhotoLookModel {
    static let thumbnailEdge = 192

    var look = PhotoLook.untouched {
        didSet { if look != oldValue { lookChanged() } }
    }
    var showsOriginal = false
    private(set) var base: CGImage?
    private(set) var preparedFor: CropGeometry?
    private(set) var preview: CGImage?
    private(set) var originalThumbnail: CGImage?
    private(set) var thumbnails: [FilterPreset: CGImage] = [:]
    private(set) var masks = FilterMasks.none

    @ObservationIgnored private var rendering = false
    @ObservationIgnored private var dirty = false
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-look")

    /// Called with each new crop, before Filter or Adjust shows it.
    func prepare(_ cropped: CGImage, for geometry: CropGeometry) async {
        base = cropped
        preparedFor = geometry
        masks = .none
        let small = Self.downscaled(cropped, longEdge: Self.thumbnailEdge)
        originalThumbnail = small
        thumbnails = await Task.detached(priority: .userInitiated) {
            var out: [FilterPreset: CGImage] = [:]
            guard let small else { return out }
            for preset in FilterPreset.allCases {
                if let image = Self.render(preset.params, on: small, masks: .none) { out[preset] = image }
            }
            return out
        }.value
        if thumbnails.count != FilterPreset.allCases.count { logger.error("preset thumbnails incomplete") }
        // The first preview is awaited, so a freshly prepared model already shows the look.
        let params = look.resolve(), masks = self.masks
        preview = await Task.detached(priority: .userInitiated) { Self.render(params, on: cropped, masks: masks) }.value
        if preview == nil { logger.error("look preview render failed") }
    }

    /// Done: the look on the 1024 px crop, or the crop itself when the look is the identity.
    /// `sameCrop` says whether `cropped` matches the crop this model was prepared with.
    func apply(to cropped: CGImage, sameCrop: Bool) async -> CGImage {
        let params = look.resolve()
        guard params != .neutral else { return cropped }
        let masks = sameCrop ? self.masks : .none
        let rendered = await Task.detached(priority: .userInitiated) {
            Self.render(params, on: cropped, masks: masks)
        }.value
        guard let rendered else {
            logger.error("look render failed on Done; uploading the plain crop")
            return cropped
        }
        return rendered
    }

    private func lookChanged() {
        scheduleRender()
    }

    private func scheduleRender() {
        guard base != nil else { return }
        guard !rendering else { dirty = true; return }
        rendering = true
        Task { await renderLoop() }
    }

    private func renderLoop() async {
        repeat {
            dirty = false
            guard let base else { break }
            let params = look.resolve(), masks = self.masks
            let image = await Task.detached(priority: .userInitiated) {
                Self.render(params, on: base, masks: masks)
            }.value
            if let image { preview = image } else { logger.error("look preview render failed") }
        } while dirty
        rendering = false
    }

    /// Renders in the image's own colour space, as the lab does: a Display P3 photo stays P3.
    nonisolated static func render(_ params: FilterParams, on image: CGImage, masks: FilterMasks) -> CGImage? {
        guard let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        let output = FilterPipeline.apply(params, to: CIImage(cgImage: image), masks: masks)
        return FilterPipeline.render(output, context: FilterPipeline.sharedContext, colorSpace: space)
    }

    nonisolated static func downscaled(_ image: CGImage, longEdge: Int) -> CGImage? {
        let scale = Double(longEdge) / Double(max(image.width, image.height))
        let width = max(1, Int((Double(image.width) * scale).rounded()))
        let height = max(1, Int((Double(image.height) * scale).rounded()))
        guard let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        ctx.interpolationQuality = .high
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return ctx.makeImage()
    }
}
```

- [ ] **Step 4: Run them and watch them pass.** Run the suite `PhotoLookModelTests`. Expected: 3 passed. If `CropGeometry` is not `Equatable` for `preparedFor ==`, stop and report: `PhotoCropModel.hasChanges` already compares geometries, so it should be.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLookModel.swift apps/ios/PebblesTests/Filters/PhotoLookModelTests.swift
git commit -m "feat(ios): add the photo look model with coalesced previews"
```

### Task 5: `LookCanvas`, `LookSlider`, `FilterToolPanel`

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/LookCanvas.swift`, `LookSlider.swift`, `FilterToolPanel.swift`

These are views without their own tests. The references in Task 7 cover them.

- [ ] **Step 1: `LookSlider.swift`**

```swift
import SwiftUI

/// One labelled slider of the Filter and Adjust tools: a readout, double-tap to reset
/// to its default (spec 2026-11-23 decision 7), and a "Reset" accessibility action.
struct LookSlider: View {
    enum Readout { case percent, signed }

    let label: LocalizedStringResource
    @Binding var value: Double
    let range: ClosedRange<Double>
    let defaultValue: Double
    let readout: Readout
    var isEnabled = true

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack {
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurface)
                Spacer()
                readoutText
                    .pebblesFont(.subhead)
                    .monospacedDigit()
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .accessibilityHidden(true)
            Slider(value: $value, in: range)
                .tint(Color.pebbles.primary)
                .disabled(!isEnabled)
                // Simultaneous, so the double-tap resets without stealing the drag.
                .simultaneousGesture(TapGesture(count: 2).onEnded { value = defaultValue })
                .accessibilityLabel(Text(label))
                .accessibilityValue(readoutText)
                .accessibilityAction(named: Text("Reset")) { value = defaultValue }
        }
        .opacity(isEnabled ? 1 : 0.5)
    }

    private var readoutText: Text {
        switch readout {
        case .percent: Text(value, format: .percent.precision(.fractionLength(0)))
        case .signed: Text(value, format: .number.precision(.fractionLength(2)).sign(strategy: .always()))
        }
    }
}
```

- [ ] **Step 2: `LookCanvas.swift`**

```swift
import SwiftUI

/// The canvas on Filter and Adjust: the crop with the look, no gestures but
/// touch-and-hold for the photo without it (spec 2026-11-23 "Layout").
struct LookCanvas: View {
    @Bindable var model: PhotoLookModel

    var body: some View {
        Group {
            if let image = model.showsOriginal ? model.base : (model.preview ?? model.base) {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .scaledToFit()
                    .padding(Spacing.lg)
                    .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, perform: {},
                                        onPressingChanged: { model.showsOriginal = $0 })
                    .accessibilityElement()
                    .accessibilityLabel(Text("Photo with the look"))
                    .accessibilityAction(named: Text("Show original")) { model.showsOriginal.toggle() }
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
```

- [ ] **Step 3: `FilterToolPanel.swift`**

```swift
import SwiftUI

/// The Filter tool (spec 2026-11-23 "Layout"): Original and the seven presets as
/// thumbnails, then intensity and the three extras.
struct FilterToolPanel: View {
    @Bindable var model: PhotoLookModel

    private static let tile: CGFloat = 64
    private static let radius: CGFloat = 12

    var body: some View {
        VStack(spacing: Spacing.md) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: Spacing.sm) {
                    tile(nil)
                    ForEach(FilterPreset.allCases) { tile($0) }
                }
            }
            LookSlider(label: "Intensity", value: $model.look.intensity, range: 0...1, defaultValue: 1,
                       readout: .percent, isEnabled: model.look.preset != nil)
            ForEach(PhotoLook.Extra.allCases, id: \.self) { extra in
                LookSlider(label: extra.label, value: binding(for: extra), range: 0...1,
                           defaultValue: model.look.preset == nil ? 0 : extra.defaultValue, readout: .percent)
            }
        }
    }

    private func binding(for extra: PhotoLook.Extra) -> Binding<Double> {
        Binding(get: { model.look.value(of: extra) }, set: { model.look.set(extra, to: $0) })
    }

    private func tile(_ preset: FilterPreset?) -> some View {
        let selected = model.look.preset == preset
        let image = preset.map { model.thumbnails[$0] } ?? model.originalThumbnail
        let shape = RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
        return Button { model.look.select(preset) } label: {
            VStack(spacing: Spacing.xs) {
                ZStack {
                    shape.fill(Color.pebbles.surfaceContainerHigh)
                    if let image {
                        Image(decorative: image, scale: 1).resizable().scaledToFill()
                    }
                }
                .frame(width: Self.tile, height: Self.tile)
                .clipShape(shape)
                .overlay(shape.strokeBorder(Color.pebbles.primary, lineWidth: selected ? 3 : 0))
                Text(preset?.name ?? "Original")
                    .pebblesFont(.meta)
                    .lineLimit(1)
                    .foregroundStyle(selected ? Color.pebbles.onSurface : Color.pebbles.onSurfaceVariant)
            }
            .frame(width: Self.tile + Spacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(preset?.name ?? "Original"))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
```

`model.thumbnails[$0]` is `CGImage?`, so `preset.map { … }` is `CGImage??`. If the compiler rejects `?? model.originalThumbnail`, write it out:

```swift
let image: CGImage? = if let preset { model.thumbnails[preset] } else { model.originalThumbnail }
```

- [ ] **Step 4: Build.**

```bash
cd apps/ios && xcodegen generate && xcodebuild build -scheme Pebbles -destination 'platform=iOS Simulator,name=FilterLab iPhone 17' -derivedDataPath build/dd-filterlab 2>&1 | tail -5
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/LookCanvas.swift apps/ios/Pebbles/Features/PebbleMedia/Filters/LookSlider.swift apps/ios/Pebbles/Features/PebbleMedia/Filters/FilterToolPanel.swift
git commit -m "feat(ios): add the filter tool panel, its sliders and the look canvas"
```

### Task 6: Wire the Filter tab into the editor, with strings

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropModel.swift:4-7`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/Crop/CropToolGroup.swift:64-78`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Add the case.** In `PhotoCropModel.swift`: `case ratio, rotate, filter`.

- [ ] **Step 2: Rename and add the label and glyph.** In `CropToolGroup.swift`, add `case .filter: .filterBox` to `glyph`. In `label`, write `case .ratio: "Reframe"` and add `case .filter: "Filter"`. Keep the `.ratio` case name: renaming it is a refactor the spec doesn't ask for. Update the doc comment on line 6: "A tool is one more case in `tools`."

- [ ] **Step 3: Wire the editor.** In `PhotoCropEditor.swift`:

  1. **State and init.** Add `@State private var look: PhotoLookModel`. Give the init a parameter `look: PhotoLookModel = PhotoLookModel()` after `initialTool`, assigned with `_look = State(initialValue: look)`.
  2. **Canvas.** In `body`, replace `CropCanvas(model: model)` with the block below:

     ```swift
     Group {
         if model.tool.showsLook {
             LookCanvas(model: look)
         } else {
             CropCanvas(model: model)
         }
     }
     ```

     Keep its `.frame` and `.disabled` modifiers. After `.pebblesScreen()`, add `.task(id: model.tool) { await prepareLookIfNeeded() }`.
  3. **Panel.** Replace `.frame(height: 96)` on `toolPanel` with `.frame(height: model.tool.panelHeight)`. Add `case .filter: FilterToolPanel(model: look)` to the `toolPanel` switch.
  4. **Reset.** Replace `if model.hasChanges {` with `if showsReset {`, and replace the action `animated { model.reset() }` with `resetCurrentTool()`.
  5. **Done.** In `done()`, replace `let image = try await model.render()` with:

     ```swift
     let cropped = try await model.render()
     let image = await look.apply(to: cropped, sameCrop: look.preparedFor == model.geometry)
     ```

  6. **Helpers.** Add these members:

     ```swift
     private var showsReset: Bool {
         switch model.tool {
         case .ratio, .rotate: model.hasChanges
         case .filter: look.look.hasFilterChanges
         }
     }

     /// Reset acts on the open tab's tool only (spec 2026-11-23 decision 8).
     private func resetCurrentTool() {
         animated {
             switch model.tool {
             case .ratio, .rotate: model.reset()
             case .filter: look.look.resetFilter()
             }
         }
     }

     /// The look works on the crop, so a crop change since the last visit re-prepares it.
     private func prepareLookIfNeeded() async {
         let geometry = model.geometry
         guard model.tool.showsLook, look.preparedFor != geometry else { return }
         do {
             let cropped = try await model.render()
             await look.prepare(cropped, for: geometry)
         } catch {
             logger.error("look crop failed: \(String(describing: error), privacy: .public)")
         }
     }
     ```

  7. **Extension.** At the end of the file, outside `#Preview`, add:

     ```swift
     private extension CropTool {
         var showsLook: Bool { self == .filter }
         /// The crop tools fit one row; the filter needs its preset row and four sliders.
         var panelHeight: CGFloat { showsLook ? 300 : 96 }
     }
     ```

  8. **Comments.** Update the file's header comment, since filters are no longer "will be".

- [ ] **Step 4: Strings.** Rename the existing `"Ratio"` entry's key to `"Reframe"`, with en "Reframe" and fr "Cadrer". `CropToolGroup` was its only use: check with `grep -rn '"Ratio"' apps/ios/Pebbles`. Then add these keys, each with both values `"state" : "translated"`:

  | Key (en) | fr |
  |---|---|
  | Filter | Filtre |
  | Original | Original |
  | Everyday | Quotidien |
  | Vivid | Vif |
  | Soft | Doux |
  | Lift | Lumineux |
  | Mono | Mono |
  | Mono Deep | Mono profond |
  | Memory | Souvenir |
  | Intensity | Intensité |
  | Vignette | Vignette |
  | Blur | Flou |
  | Grain | Grain |
  | Photo with the look | Photo avec le filtre |
  | Show original | Voir l'original |

  `"Reset"` already exists. **Insert each entry as text, never by reloading and dumping the JSON:** Xcode's formatting must survive, and a whole-file `json.dump` rewrites it. Copy the shape of the `"Ratio"` entry (4-space key indent, ` : ` separators). Place each before the first existing top-level key that sorts after it. Then validate with:

  ```bash
  python3 -c "import json;json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))"
  ```

  The diff must hold only the new entries and the renamed key.

- [ ] **Step 5: Build and run the crop and filter suites.**

  ```bash
  cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=FilterLab iPhone 17' -derivedDataPath build/dd-filterlab -only-testing:PebblesTests/PhotoCropModelTests -only-testing:PebblesTests/PhotoLookModelTests -only-testing:PebblesTests/PhotoLookTests -only-testing:PebblesTests/FilterPresetTests -only-testing:PebblesTests/ThemeLiteralsTests 2>&1 | tail -20
  ```

  Expected: every suite passes. Swift Testing suite names are the struct names. If `-only-testing` matches nothing, use the struct's name as it appears in the test file.

- [ ] **Step 6: Lint and commit**

```bash
cd apps/ios && swiftlint lint --quiet Pebbles/Features/PebbleMedia/Crop/PhotoCropModel.swift Pebbles/Features/PebbleMedia/Crop/CropToolGroup.swift Pebbles/Features/PebbleMedia/Crop/PhotoCropEditor.swift
cd ../.. && git add apps/ios/Pebbles/Features/PebbleMedia/Crop apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): add the filter tab to the photo editor"
```

### Task 7: Filter references, simulator check, PR

**Files:**
- Modify: `apps/ios/PebblesTests/References/PhotoCropReferences.swift`

- [ ] **Step 1: Add the reference test** inside `PhotoCropReferences`:

```swift
        @Test(arguments: ReferenceScheme.allCases)
        func filter(scheme: ReferenceScheme) async throws {
            let crop = PhotoCropModel(source: .fixture)
            let look = PhotoLookModel()
            look.look.select(.vivid)
            await look.prepare(try await crop.render(), for: crop.geometry)
            let editor = PhotoCropEditor(source: .fixture, initialTool: .filter, look: look,
                                         onCancel: {}, onDone: { _, _ in })
                .frame(width: ReferenceConfig.phoneWidth, height: Self.height)
            ReferenceImage.assertMatches(editor, named: "cropFilter", config: Self.config(scheme))
        }
```

The look is selected before `prepare`, so the awaited first preview already carries Vivid and the image is deterministic (the grain is seeded). The ratio and rotate references change too: three tabs, and "Reframe". All three are re-baselined on CI.

- [ ] **Step 2: Check it in the simulator.**
  1. Use the `run` skill, or install the Debug build on "FilterLab iPhone 17".
  2. Pick a photo in Capture and open Filter. Check:
     - the thumbnails show;
     - picking Mono turns the canvas grey;
     - intensity at 0 brings the colour back while the extras stay;
     - touch-and-hold shows the original;
     - Reset clears the filter but not the crop;
     - Done uploads a filtered photo.
  3. Change the format on Reframe, then come back to Filter: the canvas shows the new crop.
  4. Take a screenshot of the Filter tab for the PR.

- [ ] **Step 3: Commit, then open the Part 2 PR.**

  ```bash
  git add apps/ios/PebblesTests/References/PhotoCropReferences.swift
  git commit -m "test(ios): add the filter tab reference"
  ```

  - Open the PR on the stack: title `feat(ios): add the filter tab to the photo editor`, body `Part of #1680`, the screenshot, and `Data safety: no change`. Labels `feat` + `ios`, milestone M70.
  - Then add the label `rebaseline-ios-screenshots` alone and let CI commit the references.

---

# Part 3 — The Adjust tab and the device pass (branch `feat/1680-adjust-tool-ios`, stacked on Part 2)

### Task 8: Lazy subject masks in `PhotoLookModel`

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLookModel.swift`
- Test: `apps/ios/PebblesTests/Filters/PhotoLookModelTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
    @Test("masks load only once refocus moves, and a picture with no subject says so")
    func masksAreLazy() async throws {
        let (image, geometry) = try cropped()
        let model = PhotoLookModel()
        await model.prepare(image, for: geometry)
        #expect(model.masksState == .notLoaded)
        model.look.adjust.refocus = 0.5
        #expect(model.masksState == .loading)
        await model.waitForMasks()
        #expect(model.masksState == .ready)
        // A ramp has no subject: refocus has nothing to act on, so it goes back to 0.
        #expect(!model.subjectFound)
        #expect(model.look.adjust.refocus == 0)
    }
```

- [ ] **Step 2: Run it and watch it fail.** Run the suite `PhotoLookModelTests`. Expected: a compile error, "value of type 'PhotoLookModel' has no member 'masksState'".

- [ ] **Step 3: Implement.** Add these to `PhotoLookModel`:

```swift
    enum MasksState: Equatable { case notLoaded, loading, ready }

    private(set) var masksState = MasksState.notLoaded
    @ObservationIgnored private var masksTask: Task<Void, Never>?

    var subjectFound: Bool { masks.subject != nil }

    /// Tests and Done wait on the subject search rather than poll it.
    func waitForMasks() async { await masksTask?.value }

    private func loadMasksIfNeeded() {
        guard look.adjust.refocus > 0, masksState == .notLoaded, let base else { return }
        masksState = .loading
        masksTask = Task {
            let found = await Task.detached(priority: .userInitiated) { Self.subjectMasks(for: base) }.value
            guard self.base === base else { return } // a new crop arrived meanwhile
            masks = found
            masksState = .ready
            if found.subject == nil { look.adjust.refocus = 0 }
            scheduleRender()
        }
    }

    /// Refocus only: no auto-enhance chain (the spec drops auto-enhance).
    nonisolated static func subjectMasks(for image: CGImage) -> FilterMasks {
        FilterMasks(subject: FilterMasks.subjectMask(for: image, faceCount: PhotoMeasures.faceCount(in: image)),
                    autoEnhance: [])
    }
```

Then change these existing members:
- In `prepare`, after `masks = .none`, add `masksState = .notLoaded`, `masksTask?.cancel()` and `masksTask = nil`. At the end, add `loadMasksIfNeeded()`.
- `lookChanged()` becomes `scheduleRender(); loadMasksIfNeeded()`.
- Replace the masks line in `apply(to:sameCrop:)` with the code below. On Done with refocus, the masks are recomputed on the final crop when they are stale or still loading:

```swift
        let masks: FilterMasks
        if params.refocus == 0 {
            masks = .none
        } else if sameCrop, masksState == .ready {
            masks = self.masks
        } else {
            masks = await Task.detached(priority: .userInitiated) { Self.subjectMasks(for: cropped) }.value
        }
```

- [ ] **Step 4: Run it and watch it pass.** Run the suite `PhotoLookModelTests`. Expected: 4 passed. Vision runs in the simulator; if the foreground request throws there, it logs, returns `nil`, and the test still holds.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/Filters/PhotoLookModel.swift apps/ios/PebblesTests/Filters/PhotoLookModelTests.swift
git commit -m "feat(ios): find the photo's subject only once refocus is used"
```

### Task 9: `AdjustToolPanel` and the Adjust tab

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/Filters/AdjustToolPanel.swift`
- Modify: `PhotoCropModel.swift`, `CropToolGroup.swift`, `PhotoCropEditor.swift`, `Localizable.xcstrings`

- [ ] **Step 1: Write `AdjustToolPanel.swift`**

```swift
import SwiftUI

/// The Adjust tool (spec 2026-11-23 "Adjust"): the lab's sliders as offsets on top of
/// the filter. Each shows the person's own offset, so it starts at 0 whatever the preset.
struct AdjustToolPanel: View {
    @Bindable var model: PhotoLookModel

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                ForEach(AdjustControl.allCases) { control in
                    LookSlider(label: control.label, value: binding(for: control), range: control.range,
                               defaultValue: 0, readout: .signed, isEnabled: isEnabled(control))
                    if control == .refocus, let note = refocusNote {
                        Text(note)
                            .pebblesFont(.meta)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding(.vertical, Spacing.sm)
        }
    }

    private func binding(for control: AdjustControl) -> Binding<Double> {
        Binding(get: { model.look.adjust[keyPath: control.keyPath] },
                set: { model.look.adjust[keyPath: control.keyPath] = $0 })
    }

    /// Refocus stays movable until the search finished without a subject.
    private func isEnabled(_ control: AdjustControl) -> Bool {
        control != .refocus || model.masksState != .ready || model.subjectFound
    }

    private var refocusNote: LocalizedStringResource? {
        switch model.masksState {
        case .notLoaded: nil
        case .loading: "Finding the subject…"
        case .ready: model.subjectFound ? nil : "No subject found in this photo."
        }
    }
}
```

- [ ] **Step 2: Wire it.**
  - `CropTool` becomes `case ratio, rotate, filter, adjust`.
  - In `CropToolGroup`, add `case .adjust: .slider` (glyph) and `case .adjust: "Adjust"` (label).
  - In `PhotoCropEditor`, the `CropTool` extension's `showsLook` becomes `self == .filter || self == .adjust`.
  - `toolPanel` gains `case .adjust: AdjustToolPanel(model: look)`.
  - `showsReset` gains `case .adjust: look.look.hasAdjustChanges`.
  - `resetCurrentTool()` gains `case .adjust: look.look.resetAdjust()`.
  - Done waits on the subject search only through `apply`, which recomputes masks itself, so nothing else changes.

- [ ] **Step 3: Strings.** Add these by text insertion, as in Task 6 Step 4:

  | Key (en) | fr |
  |---|---|
  | Adjust | Ajuster |
  | Exposure | Exposition |
  | Brilliance | Éclat |
  | Highlights | Hautes lumières |
  | Shadows | Ombres |
  | Contrast | Contraste |
  | Saturation | Saturation |
  | Vibrance | Vibrance |
  | Warmth | Chaleur |
  | Tint | Teinte |
  | Sharpness | Netteté |
  | Refocus | Mise au point |
  | Finding the subject… | Recherche du sujet… |
  | No subject found in this photo. | Aucun sujet trouvé sur cette photo. |

  Validate the JSON as before.

- [ ] **Step 4: Add the Adjust reference** to `PhotoCropReferences`:

```swift
        @Test(arguments: ReferenceScheme.allCases)
        func adjust(scheme: ReferenceScheme) async throws {
            let crop = PhotoCropModel(source: .fixture)
            let look = PhotoLookModel()
            look.look.select(.soft)
            look.look.adjust.exposure = 0.3
            await look.prepare(try await crop.render(), for: crop.geometry)
            let editor = PhotoCropEditor(source: .fixture, initialTool: .adjust, look: look,
                                         onCancel: {}, onDone: { _, _ in })
                .frame(width: ReferenceConfig.phoneWidth, height: Self.height)
            ReferenceImage.assertMatches(editor, named: "cropAdjust", config: Self.config(scheme))
        }
```

- [ ] **Step 5: Build, test, lint and commit.** Run the suites from Task 6 Step 5, then swiftlint on the touched files.

```bash
git add apps/ios/Pebbles/Features/PebbleMedia apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/PebblesTests/References/PhotoCropReferences.swift
git commit -m "feat(ios): add the adjust tab to the photo editor"
```

### Task 10: Device pass and tuning

The maintainer tunes on the iPhone 15. The ids and the install recipe are in the session memory "iOS device deploy recipe".

- [ ] **Step 1: Install** the Debug build on the iPhone 15. If the phone is locked, the launch fails while the install lands: ask the maintainer to open the app.
- [ ] **Step 2: Walk the maintainer through** about five of their own photos:
  - each preset at 50 % and 100 %;
  - grain and blur at 1;
  - Mono at 30 %;
  - refocus on a portrait.
- [ ] **Step 3: Apply what they decide,** each as its own commit with the spec table updated in the same commit:
  - **Grain or blur too weak at 1:** scale the value in `PhotoLook.resolve()`, for example `p.grain = grain * 1.6`, and clamp to the pipeline's 0…1 if the scale lets it exceed 1. Keep 0 as the identity, and update the `intensity` test's expected extras.
  - **Black and white at any intensity:** in `resolve()`, pin `saturation` to −1 when `preset?.params.saturation == -1` and intensity > 0, before adding the Adjust offset. Add a test: Mono at intensity 0.3 resolves to saturation −1.
  - **Preset values tweaked:** edit `FilterPreset.params` and the spec's table together.
- [ ] **Step 4: Open the Part 3 PR.**
  - Title `feat(ios): add the adjust tab to the photo editor`, body `Resolves #1680`, a screenshot, and `Data safety: no change`. Labels `feat` + `ios`, milestone M70.
  - Add `rebaseline-ios-screenshots` alone.
  - Mark the photo editor's Arkaik acceptance `development`; the App moves it to `releasing` at merge. Never set `live`.
  - Drop the build journal pebble for the feature (`--size medium`, `--collection iOS`).
  - Open the Android mirror issue if the maintainer wants it now.
