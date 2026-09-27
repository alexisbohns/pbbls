# iOS Stone Material Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Debug-only iOS screen showing the nine pebble shapes as lit stone (lava / river / gem by polarity), coloured by any of the seven emotion palettes, with veins, fossil and glyph carved into the surface, under a draggable light.

**Architecture:** The stone body is the existing wobbled backdrop silhouette, painted by a Metal `colorEffect` shader (`stone`) that is a pure function of its uniforms. The carving is the engine shape's outline, veins, fossil and a sample glyph, wobbled through the existing renderer, drawn flat and passed through a `layerEffect` shader (`carve`) that shades the ink's edges relative to the light. A palette table, a material struct and a light struct are the only state; nothing on the page is time-driven.

**Tech Stack:** SwiftUI (iOS 17), Metal shaders via `ShaderLibrary`, Swift Testing, XcodeGen, the existing `WobbleRenderer`.

**Spec:** `docs/superpowers/specs/2026-09-27-ios-stone-material-lab-design.md`

**Refinements to the spec, decided while planning:**
- The material pass is a `colorEffect`, not a zero-offset `layerEffect`: it needs only the body pixel's colour and alpha, and `colorEffect` is the cheaper pipeline for that.
- Tones are not free colour sliders. Each of the four roles (body, ink, lip light, lip shadow) picks one of the six palette slots plus an opacity, which is what the spec's "every tone is a slider" was for and is testable.
- The Debug row sits in its own `Developer` section of the settings sheet rather than inside Legal, so a debug entry never reads as a legal document.
- The seed copies are prefixed `stone-lab-shape-` because the app bundle is flat and `Outlines/small-neutral.svg` already owns the bare name.

---

## File structure

| File | Responsibility |
|---|---|
| `apps/ios/Pebbles/Resources/StoneLab/stone-lab-shape-<size>-<polarity>.svg` (×9) | Verbatim copies of `docs/seeds/shape-seeds/*.svg` |
| `apps/ios/Pebbles/Resources/StoneLab/stone-lab-glyph.json` | The `identity` strokes from `docs/seeds/domain-glyph-seed.json` |
| `apps/ios/Pebbles/Resources/StoneLab/stone.metal` | Noise, `stone` colour effect, `carve` layer effect |
| `apps/ios/Pebbles/Features/StoneLab/StoneShapeParser.swift` | Order-based split of an engine shape into outline / veins / fossil |
| `apps/ios/Pebbles/Features/StoneLab/StoneLabPalettes.swift` | Seven hard-coded palettes, hex → RGB |
| `apps/ios/Pebbles/Features/StoneLab/StoneMaterial.swift` | Material knobs, starting table per polarity, uniform order |
| `apps/ios/Pebbles/Features/StoneLab/StoneTones.swift` | Palette-slot picks per role, starting table per polarity |
| `apps/ios/Pebbles/Features/StoneLab/StoneLight.swift` | Light direction + elevation, rest pose, shader vector |
| `apps/ios/Pebbles/Features/StoneLab/StoneShaders.swift` | Builds the two `Shader` values in the declared uniform order |
| `apps/ios/Pebbles/Features/StoneLab/StoneLabArt.swift` | Wobbled carving art per `Valence`, cached, glyph placed |
| `apps/ios/Pebbles/Features/StoneLab/StoneView.swift` | One stone: body, material, carving, sheen; flat mode |
| `apps/ios/Pebbles/Features/StoneLab/StoneLabView.swift` | The page: grid, emotion picker, light drag, knobs, detail |
| `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` | `#if DEBUG` Developer section opening the lab |
| `apps/ios/PebblesTests/StoneLab/*.swift` | Parser, palettes, material, tones, light, art tests |

Every Swift file under `Features/StoneLab/` is wrapped in `#if DEBUG … #endif` so Release compiles none of it. The `.metal` file compiles in every configuration (Metal has no compilation conditions worth fighting); it is dead weight of a few KB in Release, accepted.

All commands run from `apps/ios/`. The test command for one suite:

```bash
xcodegen generate >/dev/null && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:PebblesTests/<SuiteName> 2>&1 | grep -E "Test (Suite|Case|\").*(passed|failed)|error:" | tail -20
```

If a build serves stale objects after `xcodegen generate`, remove `~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build` first (project memory: iOS xcodegen stale builds).

---

### Task 1: Resources — shape seeds and the sample glyph

**Files:**
- Create: `apps/ios/Pebbles/Resources/StoneLab/stone-lab-shape-{small,medium,large}-{lowlight,neutral,highlight}.svg`
- Create: `apps/ios/Pebbles/Resources/StoneLab/stone-lab-glyph.json`

- [ ] **Step 1: Copy the nine seeds with the prefix**

```bash
mkdir -p Pebbles/Resources/StoneLab
for size in small medium large; do for pol in lowlight neutral highlight; do
  cp ../../docs/seeds/shape-seeds/$size-$pol.svg Pebbles/Resources/StoneLab/stone-lab-shape-$size-$pol.svg
done; done
ls Pebbles/Resources/StoneLab | wc -l
```
Expected: `9`

- [ ] **Step 2: Extract the identity glyph strokes**

```bash
node -e '
const seed = require("../../docs/seeds/domain-glyph-seed.json");
const strokes = seed.identity.map(s => ({ d: s.d, width: s.width }));
require("fs").writeFileSync("Pebbles/Resources/StoneLab/stone-lab-glyph.json", JSON.stringify({ viewBox: 200, strokes }, null, 2) + "\n");
console.log(strokes.length, "strokes");
'
```
Expected: a stroke count above 0 and a file whose top level is `{"viewBox": 200, "strokes": [{"d": …, "width": 6}, …]}`.

- [ ] **Step 3: Verify XcodeGen picks the folder up as resources**

```bash
xcodegen generate >/dev/null && grep -c "stone-lab-shape" Pebbles.xcodeproj/project.pbxproj
```
Expected: a number ≥ 9.

- [ ] **Step 4: Commit**

```bash
git add Pebbles/Resources/StoneLab
git commit -m "feat(ios): bundle the engine shape seeds and a sample glyph for the stone lab (#974)"
```

---

### Task 2: StoneShapeParser

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneShapeParser.swift`
- Test: `apps/ios/PebblesTests/StoneLab/StoneShapeParserTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import CoreGraphics
@testable import Pebbles

@Suite("StoneShapeParser")
struct StoneShapeParserTests {

    private static let veinsBySize: [ValenceSizeGroup: Int] = [.small: 0, .medium: 2, .large: 3]

    @Test("splits every engine seed into one outline, N veins and one fossil", arguments: Valence.allCases)
    func splitsSeed(valence: Valence) throws {
        let name = "stone-lab-shape-\(valence.sizeGroup.rawValue)-\(valence.polarity.rawValue)"
        let url = try #require(Bundle.main.url(forResource: name, withExtension: "svg"))
        let svg = try String(contentsOf: url, encoding: .utf8)
        let parsed = try #require(StoneShapeParser.parse(svg))
        #expect(parsed.veins.count == Self.veinsBySize[valence.sizeGroup])
        #expect(parsed.outline.width == 6)
        #expect(parsed.fossil != nil)
    }

    @Test("the outline is the first stroked path, in document order")
    func outlineIsFirst() throws {
        let svg = """
        <svg viewBox="0 0 10 10"><path d="M0 0L1 1" stroke="black" stroke-width="6"/>\
        <path d="M2 2L3 3" stroke="black" stroke-width="6"/>\
        <path fill-rule="evenodd" d="M5 5L6 6Z" fill="black"/></svg>
        """
        let parsed = try #require(StoneShapeParser.parse(svg))
        #expect(parsed.outline.d == "M0 0L1 1")
        #expect(parsed.veins.map(\.d) == ["M2 2L3 3"])
        #expect(parsed.fossil?.d == "M5 5L6 6Z")
        #expect(parsed.fossil?.usesEvenOddFill == true)
        #expect(parsed.viewBox == CGRect(x: 0, y: 0, width: 10, height: 10))
    }

    @Test("returns nil without a viewBox or without a stroked path")
    func rejectsMalformed() {
        #expect(StoneShapeParser.parse("<svg><path d=\"M0 0\" stroke=\"black\"/></svg>") == nil)
        #expect(StoneShapeParser.parse("<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\" fill=\"black\"/></svg>") == nil)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the suite command with `-only-testing:PebblesTests/StoneShapeParserTests`.
Expected: compile error, `StoneShapeParser` not found.

- [ ] **Step 3: Implement the parser**

```swift
#if DEBUG
import CoreGraphics
import Foundation

/// Splits an engine shape seed (`docs/seeds/shape-seeds/*.svg`) into the parts
/// the stone lab carves separately. The engine emits no ids, only order: the
/// first stroked `<path>` is the outline, every later stroked path is a vein,
/// and the single filled path is the fossil.
enum StoneShapeParser {

    struct Stroke: Equatable {
        // swiftlint:disable:next identifier_name
        let d: String
        /// `stroke-width` in viewBox units; the engine always writes 6.
        let width: Double
    }

    struct Fill: Equatable {
        // swiftlint:disable:next identifier_name
        let d: String
        let usesEvenOddFill: Bool
    }

    struct Parsed: Equatable {
        let viewBox: CGRect
        let outline: Stroke
        let veins: [Stroke]
        let fossil: Fill?
    }

    private static let pathPattern = try? NSRegularExpression(pattern: "<path\\s+([^>]*?)/?>")
    private static let attributePattern = try? NSRegularExpression(pattern: "([\\w-]+)=\"([^\"]*)\"")
    private static let viewBoxPattern = try? NSRegularExpression(pattern: "viewBox=\"([^\"]*)\"")

    static func parse(_ svg: String) -> Parsed? {
        guard let viewBox = viewBox(in: svg) else { return nil }
        var strokes: [Stroke] = []
        var fossil: Fill?
        for attributes in pathAttributes(in: svg) {
            guard let d = attributes["d"] else { continue }
            let fill = attributes["fill"]
            if let fill, fill != "none" {
                // Keep the first filled path only: the engine emits one fossil.
                if fossil == nil {
                    fossil = Fill(d: d, usesEvenOddFill: attributes["fill-rule"] == "evenodd")
                }
            } else if attributes["stroke"] != nil {
                strokes.append(Stroke(d: d, width: Double(attributes["stroke-width"] ?? "6") ?? 6))
            }
        }
        guard let outline = strokes.first else { return nil }
        return Parsed(viewBox: viewBox, outline: outline, veins: Array(strokes.dropFirst()), fossil: fossil)
    }

    private static func viewBox(in svg: String) -> CGRect? {
        guard let viewBoxPattern,
              let match = viewBoxPattern.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
              let range = Range(match.range(at: 1), in: svg) else { return nil }
        let parts = svg[range].split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
        guard parts.count == 4 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }

    private static func pathAttributes(in svg: String) -> [[String: String]] {
        guard let pathPattern, let attributePattern else { return [] }
        let whole = NSRange(svg.startIndex..., in: svg)
        return pathPattern.matches(in: svg, range: whole).compactMap { match in
            guard let range = Range(match.range(at: 1), in: svg) else { return nil }
            let body = String(svg[range])
            var attributes: [String: String] = [:]
            for attr in attributePattern.matches(in: body, range: NSRange(body.startIndex..., in: body)) {
                guard let key = Range(attr.range(at: 1), in: body),
                      let value = Range(attr.range(at: 2), in: body) else { continue }
                attributes[String(body[key])] = String(body[value])
            }
            return attributes
        }
    }
}
#endif
```

- [ ] **Step 4: Run the suite to verify it passes**

Expected: all 11 cases (9 parametrised + 2) pass. If a seed fails the vein count, print `parsed.veins.count` for that seed and check the seed by eye: the count in the spec (0/2/3) came from the medium seeds and the `ValenceArt` counts; if a large seed carries 4 veins, the table in the test is corrected to what the seeds hold and the spec's table is updated in the same commit.

- [ ] **Step 5: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneShapeParser.swift PebblesTests/StoneLab/StoneShapeParserTests.swift
git commit -m "feat(ios): split an engine shape seed into outline, veins and fossil (#974)"
```

---

### Task 3: StoneLabPalettes

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneLabPalettes.swift`
- Test: `apps/ios/PebblesTests/StoneLab/StoneLabPalettesTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import Foundation
@testable import Pebbles

@Suite("StoneLabPalettes")
struct StoneLabPalettesTests {

    /// The seed migration is the source of truth for the seven sextets. The
    /// simulator shares the host filesystem, so the test walks up from its
    /// own path to the repo and reads the SQL.
    private static func migrationLines() throws -> [String] {
        var url = URL(fileURLWithPath: #filePath)
        while url.lastPathComponent != "apps" { url.deleteLastPathComponent() }
        url.deleteLastPathComponent()
        url.appendPathComponent("packages/supabase/supabase/migrations/20260912120000_seed_emotion_reference_data.sql")
        return try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n")
    }

    @Test("every palette matches the migration literals", arguments: StoneLabPalettes.all)
    func matchesMigration(palette: StoneLabPalette) throws {
        let line = try #require(Self.migrationLines().first { $0.contains("'\(palette.slug)', '") })
        let hexes = line.split(separator: "'").map(String.init).filter { $0.hasPrefix("#") }
        #expect(hexes == [palette.primary, palette.secondary, palette.light, palette.surface, palette.dark, palette.shaded])
    }

    @Test("all seven groups, in the default category order")
    func order() {
        #expect(StoneLabPalettes.all.map(\.slug) == EmotionCategoryOrdering.default)
    }

    @Test("hex to rgb")
    func rgb() {
        let rgb = StoneLabPalettes.rgb("#8E4242FF")
        #expect(abs(rgb.x - 0x8E / 255.0) < 0.001)
        #expect(abs(rgb.y - 0x42 / 255.0) < 0.001)
        #expect(abs(rgb.z - 0x42 / 255.0) < 0.001)
        #expect(StoneLabPalettes.rgb("nope") == SIMD3<Float>(repeating: 0))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: compile error, `StoneLabPalettes` not found.

- [ ] **Step 3: Implement**

```swift
#if DEBUG
import Foundation
import SwiftUI

/// One emotion group's six palette tones, as `#RRGGBBAA` hex.
struct StoneLabPalette: Identifiable, Hashable {
    let slug: String
    let primary: String
    let secondary: String
    let light: String
    let surface: String
    let dark: String
    let shaded: String

    var id: String { slug }

    enum Slot: String, CaseIterable, Identifiable {
        case primary, secondary, light, surface, dark, shaded
        var id: String { rawValue }
    }

    func hex(_ slot: Slot) -> String {
        switch slot {
        case .primary: return primary
        case .secondary: return secondary
        case .light: return light
        case .surface: return surface
        case .dark: return dark
        case .shaded: return shaded
        }
    }

    func rgb(_ slot: Slot) -> SIMD3<Float> { StoneLabPalettes.rgb(hex(slot)) }
    func color(_ slot: Slot) -> Color { Color(hex: hex(slot)) ?? .clear }
}

/// The seven seeded palettes, hard-coded so the lab works offline and signed
/// out. `StoneLabPalettesTests` pins them to the migration literals.
enum StoneLabPalettes {
    static let all: [StoneLabPalette] = [
        StoneLabPalette(slug: "peace",   primary: "#487C5AFF", secondary: "#80BF96FF", light: "#EDF2EEFF", surface: "#487C5A1A", dark: "#32573FFF", shaded: "#0E1912FF"),
        StoneLabPalette(slug: "fear",    primary: "#7B5E99FF", secondary: "#AE91CCFF", light: "#F2EFF5FF", surface: "#7B5E991A", dark: "#56426BFF", shaded: "#19131FFF"),
        StoneLabPalette(slug: "joy",     primary: "#A15C08FF", secondary: "#CF8C39FF", light: "#FAF6EAFF", surface: "#A15C081A", dark: "#714006FF", shaded: "#201202FF"),
        StoneLabPalette(slug: "anger",   primary: "#8E4242FF", secondary: "#C17575FF", light: "#F4ECECFF", surface: "#8E42421A", dark: "#632E2EFF", shaded: "#1C0D0DFF"),
        StoneLabPalette(slug: "pride",   primary: "#A9478AFF", secondary: "#EA91CEFF", light: "#F6EDF3FF", surface: "#A9478A1A", dark: "#763261FF", shaded: "#220E1CFF"),
        StoneLabPalette(slug: "shame",   primary: "#868686FF", secondary: "#B9B9B9FF", light: "#F3F3F3FF", surface: "#8686861A", dark: "#5E5E5EFF", shaded: "#1B1B1BFF"),
        StoneLabPalette(slug: "sadness", primary: "#59658AFF", secondary: "#8C98BDFF", light: "#EEF0F3FF", surface: "#59658A1A", dark: "#3E4761FF", shaded: "#12141CFF"),
    ]

    /// `#RRGGBB` or `#RRGGBBAA` → linear-ish 0..1 RGB for the shader. Alpha is
    /// ignored: opacity is a separate tone knob. Unparseable → black.
    static func rgb(_ hex: String) -> SIMD3<Float> {
        var digits = Substring(hex)
        if digits.hasPrefix("#") { digits = digits.dropFirst() }
        guard digits.count == 6 || digits.count == 8, let value = UInt32(digits.prefix(6), radix: 16) else {
            return SIMD3<Float>(repeating: 0)
        }
        return SIMD3<Float>(
            Float((value >> 16) & 0xFF) / 255,
            Float((value >> 8) & 0xFF) / 255,
            Float(value & 0xFF) / 255
        )
    }
}
#endif
```

- [ ] **Step 4: Run the suite to verify it passes**

Expected: 9 cases pass. If the migration walk fails, the `#filePath` root is wrong: print `url` in the failure and fix the walk, do not loosen the assertion.

- [ ] **Step 5: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneLabPalettes.swift PebblesTests/StoneLab/StoneLabPalettesTests.swift
git commit -m "feat(ios): hard-code the seven emotion palettes for the stone lab (#974)"
```

---

### Task 4: StoneMaterial, StoneTones, StoneLight

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneMaterial.swift`
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneTones.swift`
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneLight.swift`
- Test: `apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import Foundation
@testable import Pebbles

@Suite("StoneMaterial · StoneTones · StoneLight")
struct StoneMaterialTests {

    @Test("each polarity starts on its own material kind")
    func startingKinds() {
        #expect(StoneMaterial.starting(for: .lowlight).kind == .lava)
        #expect(StoneMaterial.starting(for: .neutral).kind == .river)
        #expect(StoneMaterial.starting(for: .highlight).kind == .gem)
    }

    @Test("uniforms are emitted in the declared order")
    func uniformOrder() {
        var material = StoneMaterial.starting(for: .neutral)
        material.scale = 1; material.relief = 2; material.contrast = 3; material.sheen = 4
        material.facetDensity = 5; material.glitter = 6; material.crack = 7; material.banding = 8
        #expect(material.uniforms == [1, 1, 2, 3, 4, 5, 6, 7, 8])
    }

    @Test("starting tones follow the spec table")
    func startingTones() {
        #expect(StoneTones.starting(for: .lowlight).body.slot == .dark)
        #expect(StoneTones.starting(for: .neutral).body.slot == .secondary)
        #expect(StoneTones.starting(for: .highlight).body.slot == .primary)
        #expect(StoneTones.starting(for: .highlight).ink.slot == .dark)
        #expect(StoneTones.starting(for: .lowlight).lipLight.opacity < 1)
    }

    @Test("the rest light comes from the top-left at 55°")
    func restLight() {
        let rest = StoneLight.rest
        #expect(abs(rest.direction.dx + 0.7071) < 0.001)
        #expect(abs(rest.direction.dy + 0.7071) < 0.001)
        #expect(rest.elevationDegrees == 55)
        let v = rest.vector
        #expect(abs(v.z - sin(55 * .pi / 180)) < 0.001)
        #expect(abs(simd_length(v) - 1) < 0.001)
    }

    @Test("a light built from a finger offset is a unit direction")
    func fromOffset() {
        let light = StoneLight(pointingTo: CGVector(dx: 30, dy: -40), elevationDegrees: 40)
        #expect(abs(light.direction.dx - 0.6) < 0.001)
        #expect(abs(light.direction.dy + 0.8) < 0.001)
        #expect(StoneLight(pointingTo: .zero, elevationDegrees: 40).direction == StoneLight.rest.direction)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: compile errors for the three missing types.

- [ ] **Step 3: Implement the three files**

`StoneMaterial.swift`:

```swift
#if DEBUG
import Foundation

/// The knobs of one surface. Every field is a shader uniform; `uniforms`
/// fixes their order so `StoneShaders` and `stone.metal` agree by test rather
/// than by eye.
struct StoneMaterial: Equatable {
    enum Kind: Int, CaseIterable, Identifiable {
        case lava, river, gem
        var id: Int { rawValue }
        var label: String {
            switch self {
            case .lava: return "Lava"
            case .river: return "River"
            case .gem: return "Gem"
            }
        }
    }

    var kind: Kind
    /// Grain frequency in cycles per stone unit (viewBox unit).
    var scale: Double
    /// Height-field depth for the normal. 0 is flat.
    var relief: Double
    /// How far the material may darken or lighten the body tone, 0..1.
    var contrast: Double
    /// Specular strength, 0..1.
    var sheen: Double
    /// Gem: facets per stone unit multiplier.
    var facetDensity: Double
    /// Gem: glitter point density, 0..1.
    var glitter: Double
    /// Lava: crack width threshold on the cell edge distance, 0..0.3.
    var crack: Double
    /// River: sediment anisotropy, 0..1.
    var banding: Double
    /// Carving lip width in points.
    var lipWidth: Double
    /// Carving lip strength, 0..1.
    var lipOpacity: Double

    /// Material uniforms in the order `stone.metal` declares them after the
    /// light and the tones: kind, scale, relief, contrast, sheen,
    /// facetDensity, glitter, crack, banding.
    var uniforms: [Float] {
        [Float(kind.rawValue), Float(scale), Float(relief), Float(contrast), Float(sheen),
         Float(facetDensity), Float(glitter), Float(crack), Float(banding)]
    }

    static func starting(for polarity: ValencePolarity) -> StoneMaterial {
        switch polarity {
        case .lowlight:
            return StoneMaterial(kind: .lava, scale: 0.045, relief: 1.6, contrast: 0.55, sheen: 0.05,
                                 facetDensity: 1, glitter: 0, crack: 0.06, banding: 0,
                                 lipWidth: 1.5, lipOpacity: 0.8)
        case .neutral:
            return StoneMaterial(kind: .river, scale: 0.35, relief: 0.6, contrast: 0.22, sheen: 0.18,
                                 facetDensity: 1, glitter: 0, crack: 0, banding: 0.5,
                                 lipWidth: 1.2, lipOpacity: 0.7)
        case .highlight:
            return StoneMaterial(kind: .gem, scale: 0.06, relief: 1.2, contrast: 0.45, sheen: 0.35,
                                 facetDensity: 1, glitter: 0.35, crack: 0, banding: 0,
                                 lipWidth: 1.2, lipOpacity: 0.9)
        }
    }
}
#endif
```

`StoneTones.swift`:

```swift
#if DEBUG
import Foundation
import SwiftUI

/// Which palette slot each colour role takes, and at what opacity. The palette
/// itself is chosen on the page; the tones only say "body is `dark`".
struct StoneTones: Equatable {
    struct Pick: Equatable {
        var slot: StoneLabPalette.Slot
        var opacity: Double
    }

    var body: Pick
    var ink: Pick
    var lipLight: Pick
    var lipShadow: Pick

    static func starting(for polarity: ValencePolarity) -> StoneTones {
        switch polarity {
        case .lowlight:
            return StoneTones(body: Pick(slot: .dark, opacity: 1), ink: Pick(slot: .shaded, opacity: 1),
                              lipLight: Pick(slot: .secondary, opacity: 0.35), lipShadow: Pick(slot: .shaded, opacity: 1))
        case .neutral:
            return StoneTones(body: Pick(slot: .secondary, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .light, opacity: 1), lipShadow: Pick(slot: .dark, opacity: 0.45))
        case .highlight:
            return StoneTones(body: Pick(slot: .primary, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .light, opacity: 1), lipShadow: Pick(slot: .dark, opacity: 1))
        }
    }

    /// Straight (non-premultiplied) RGB for the shader.
    func rgb(_ pick: Pick, in palette: StoneLabPalette) -> SIMD3<Float> { palette.rgb(pick.slot) }

    func color(_ pick: Pick, in palette: StoneLabPalette) -> Color {
        palette.color(pick.slot).opacity(pick.opacity)
    }
}
#endif
```

`StoneLight.swift`:

```swift
#if DEBUG
import CoreGraphics
import simd

/// Where the light is, in the stone's plane. `direction` points from the
/// stone toward the light in screen coordinates (y down), unit length;
/// `elevationDegrees` lifts it off the plane.
struct StoneLight: Equatable {
    var direction: CGVector
    var elevationDegrees: Double

    /// Top-left, 55° up: the rest pose the page springs back to.
    static let rest = StoneLight(direction: CGVector(dx: -sqrt(0.5), dy: -sqrt(0.5)), elevationDegrees: 55)

    /// A light in the direction of `offset` from the stone's centre. A zero
    /// offset keeps the rest direction rather than producing NaN.
    init(pointingTo offset: CGVector, elevationDegrees: Double) {
        let length = hypot(offset.dx, offset.dy)
        self.direction = length > 0.001
            ? CGVector(dx: offset.dx / length, dy: offset.dy / length)
            : StoneLight.rest.direction
        self.elevationDegrees = elevationDegrees
    }

    init(direction: CGVector, elevationDegrees: Double) {
        self.direction = direction
        self.elevationDegrees = elevationDegrees
    }

    /// Unit vector for the shader: xy in the plane, z up out of the screen.
    var vector: SIMD3<Float> {
        let elevation = elevationDegrees * .pi / 180
        return SIMD3<Float>(
            Float(direction.dx * cos(elevation)),
            Float(direction.dy * cos(elevation)),
            Float(sin(elevation))
        )
    }

    /// The carving lip offset toward the light, in points.
    func lipOffset(width: Double) -> CGSize {
        CGSize(width: direction.dx * width, height: direction.dy * width)
    }

    /// Where the sheen pool sits: pulled toward the light from the centre.
    var poolCenter: UnitPointValue {
        UnitPointValue(x: 0.5 + direction.dx * 0.35, y: 0.5 + direction.dy * 0.35)
    }

    struct UnitPointValue: Equatable {
        let x: Double
        let y: Double
    }
}
#endif
```

- [ ] **Step 4: Run the suite to verify it passes**

Expected: 6 cases pass.

- [ ] **Step 5: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneMaterial.swift Pebbles/Features/StoneLab/StoneTones.swift Pebbles/Features/StoneLab/StoneLight.swift PebblesTests/StoneLab/StoneMaterialTests.swift
git commit -m "feat(ios): material, tone and light models for the stone lab (#974)"
```

---

### Task 5: StoneLabArt — the wobbled carving per valence

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneLabArt.swift`
- Test: `apps/ios/PebblesTests/StoneLab/StoneLabArtTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
import CoreGraphics
@testable import Pebbles

@Suite("StoneLabArt")
struct StoneLabArtTests {

    @Test("builds carving art for every valence", arguments: Valence.allCases)
    func builds(valence: Valence) throws {
        let art = try #require(StoneLabArt.art(for: valence))
        #expect(!art.ink.isEmpty)
        #expect(art.fossil != nil)
        #expect(art.viewBox.width > 0)
        // The ink stays inside the canvas: nothing was placed off the stone.
        #expect(art.viewBox.insetBy(dx: -20, dy: -20).contains(art.ink.boundingBoxOfPath))
    }

    @Test("the glyph lands in the engine's slot")
    func glyphPlacement() {
        // medium / highlight: translate(26, 84) scale(150 / 200)
        let transform = StoneLabArt.glyphTransform(size: .medium, polarity: .highlight, glyphViewBox: 200)
        #expect(transform.tx == 26)
        #expect(transform.ty == 84)
        #expect(abs(transform.a - 0.75) < 0.001)
        #expect(abs(transform.d - 0.75) < 0.001)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Expected: compile error, `StoneLabArt` not found.

- [ ] **Step 3: Implement**

```swift
#if DEBUG
import CoreGraphics
import Foundation
import os

/// The carving of one stone: the engine shape's outline and veins, the sample
/// glyph placed in the engine's slot, all inked as wobbled fills and merged;
/// plus the fossil as a displaced region. Built once per valence and cached,
/// the way `ValenceArt` does it.
enum StoneLabArt {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone-lab")

    /// `@unchecked Sendable`: immutable `CGPath` copies, never mutated.
    final class Art: @unchecked Sendable {
        /// The engine canvas for this size.
        let viewBox: CGRect
        /// Outline + veins + glyph, wobbled ink, nonzero fill.
        let ink: CGPath
        /// The fossil's displaced region, with its fill rule.
        let fossil: WobbleBackdropArt?

        init(viewBox: CGRect, ink: CGPath, fossil: WobbleBackdropArt?) {
            self.viewBox = viewBox
            self.ink = ink
            self.fossil = fossil
        }
    }

    private struct GlyphFile: Decodable {
        struct Stroke: Decodable {
            // swiftlint:disable:next identifier_name
            let d: String
            let width: Double
        }
        let viewBox: Double
        let strokes: [Stroke]
    }

    private static let lock = NSLock()
    private static var cache: [Valence: Art] = [:]

    static func art(for valence: Valence) -> Art? {
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache[valence] { return cached }
        guard let built = build(valence) else { return nil }
        cache[valence] = built
        return built
    }

    static func prewarm() {
        for valence in Valence.allCases { _ = art(for: valence) }
    }

    // MARK: - Engine layout (mirrors `_shared/engine/layout.ts`)

    private static let glyphSize: [ValenceSizeGroup: Double] = [.small: 140, .medium: 150, .large: 160]

    private static let glyphPosition: [ValenceSizeGroup: [ValencePolarity: CGPoint]] = [
        .small:  [.highlight: CGPoint(x: 37.5, y: 30), .neutral: CGPoint(x: 25, y: 30), .lowlight: CGPoint(x: 30, y: 30)],
        .medium: [.highlight: CGPoint(x: 26, y: 84),   .neutral: CGPoint(x: 26, y: 55), .lowlight: CGPoint(x: 39, y: 26)],
        .large:  [.highlight: CGPoint(x: 50, y: 75),   .neutral: CGPoint(x: 26, y: 88), .lowlight: CGPoint(x: 26, y: 75)],
    ]

    /// `translate(x, y) scale(s)` in CG row-vector order (scale first).
    static func glyphTransform(size: ValenceSizeGroup, polarity: ValencePolarity, glyphViewBox: Double) -> CGAffineTransform {
        let slot = glyphSize[size] ?? 150
        let origin = glyphPosition[size]?[polarity] ?? .zero
        let scale = slot / glyphViewBox
        return CGAffineTransform(scaleX: scale, y: scale)
            .concatenating(CGAffineTransform(translationX: origin.x, y: origin.y))
    }

    // MARK: - Build

    private static func build(_ valence: Valence) -> Art? {
        let name = "stone-lab-shape-\(valence.sizeGroup.rawValue)-\(valence.polarity.rawValue)"
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let svg = try? String(contentsOf: url, encoding: .utf8),
              let parsed = StoneShapeParser.parse(svg) else {
            logger.error("stone lab: missing or unparseable seed \(name, privacy: .public)")
            return nil
        }

        let ink = CGMutablePath()
        for stroke in [parsed.outline] + parsed.veins {
            if let path = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width) {
                ink.addPath(path)
            }
        }

        if let glyph = loadGlyph() {
            var transform = glyphTransform(size: valence.sizeGroup, polarity: valence.polarity, glyphViewBox: glyph.viewBox)
            for stroke in glyph.strokes {
                guard let path = WobbleRenderer.glyphInk(d: stroke.d, width: stroke.width),
                      let placed = path.copy(using: &transform) else { continue }
                ink.addPath(placed)
            }
        }

        var fossil: WobbleBackdropArt?
        if let fill = parsed.fossil {
            let asset = """
            <svg viewBox="\(parsed.viewBox.minX) \(parsed.viewBox.minY) \(parsed.viewBox.width) \(parsed.viewBox.height)">\
            <path d="\(fill.d)"\(fill.usesEvenOddFill ? " fill-rule=\"evenodd\"" : "")/></svg>
            """
            fossil = WobbleRenderer.backdropArt(fromAsset: asset)
        }

        guard !ink.isEmpty else {
            logger.error("stone lab: no ink for \(name, privacy: .public)")
            return nil
        }
        return Art(viewBox: parsed.viewBox, ink: ink.copy() ?? ink, fossil: fossil)
    }

    private static func loadGlyph() -> GlyphFile? {
        guard let url = Bundle.main.url(forResource: "stone-lab-glyph", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(GlyphFile.self, from: data) else {
            logger.error("stone lab: missing sample glyph")
            return nil
        }
        return file
    }
}
#endif
```

- [ ] **Step 4: Run the suite to verify it passes**

Expected: 10 cases pass. The wobble of nine shapes plus the glyph costs about a second on the simulator; that is expected.

- [ ] **Step 5: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneLabArt.swift PebblesTests/StoneLab/StoneLabArtTests.swift
git commit -m "feat(ios): wobbled carving art per valence for the stone lab (#974)"
```

---

### Task 6: The shader and its Swift builder

**Files:**
- Create: `apps/ios/Pebbles/Resources/StoneLab/stone.metal`
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneShaders.swift`

No unit test can execute a shader; the gate for this task is that the target builds and Task 8's screenshots show the material. Keep the uniform order identical between `StoneMaterial.uniforms`, `StoneShaders`, and the `.metal` signature.

- [ ] **Step 1: Write the shader**

```metal
// Pebbles/Resources/StoneLab/stone.metal
//
// The stone lab's two effects (#974).
//
//   stone — a colorEffect over the body silhouette. Reads the body pixel's
//           premultiplied colour and returns the lit material in the same
//           alpha. Three materials share one entry point, selected by `kind`.
//   carve — a layerEffect over the flat carving ink. Shades the ink's edges
//           relative to the light so a line reads as a groove.
//
// Everything is a pure function of the uniforms. There is no time input.
// Noise functions are carried over from the Femfolk native card POC
// (femfolk/ios/FemfolkCard/Sources/Shaders/foil.metal).

#include <metal_stdlib>
#include <SwiftUI/SwiftUI.h>
using namespace metal;

// ---- noise ---------------------------------------------------------------

static float hash21(int2 p, uint seed) {
    uint n = (uint(p.x) * 1597334677u) ^ (uint(p.y) * 3812015801u) ^ (seed * 2654435761u);
    n = (n ^ (n >> 16)) * 0x45d9f3bu;
    n = (n ^ (n >> 16)) * 0x45d9f3bu;
    n = n ^ (n >> 16);
    return float(n) / 4294967296.0;
}

static float2 grad2(int2 p, uint seed) {
    float a = hash21(p, seed) * 2.0 * M_PI_F;
    return float2(cos(a), sin(a));
}

static float pnoise(float2 p, uint seed) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    int2 c = int2(i);
    float a = dot(grad2(c, seed), f);
    float b = dot(grad2(c + int2(1, 0), seed), f - float2(1, 0));
    float d = dot(grad2(c + int2(0, 1), seed), f - float2(0, 1));
    float e = dot(grad2(c + int2(1, 1), seed), f - float2(1, 1));
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

static float fractal(float2 p, float frequency, int octaves, uint seed) {
    float sum = 0.0, amp = 1.0;
    float2 q = p * frequency;
    for (int o = 0; o < octaves; o++) {
        sum += amp * pnoise(q, seed + uint(o) * 17u);
        q *= 2.0;
        amp *= 0.5;
    }
    return clamp(sum * 0.5 + 0.5, 0.0, 1.0);
}

/// Worley cells: nearest and second-nearest feature distance, and the
/// nearest cell's hash for a per-cell random.
static float worley(float2 p, uint seed, thread float &d1, thread float &d2) {
    float2 i = floor(p);
    float2 f = fract(p);
    d1 = 8.0; d2 = 8.0;
    float id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            int2 cell = int2(i) + int2(x, y);
            float2 feature = float2(hash21(cell, seed), hash21(cell, seed + 101u));
            float d = length(float2(x, y) + feature - f);
            if (d < d1) { d2 = d1; d1 = d; id = hash21(cell, seed + 202u); }
            else if (d < d2) { d2 = d; }
        }
    }
    return id;
}

// ---- lighting ------------------------------------------------------------

struct Lit { float diffuse; float spec; };

static Lit lit(float3 n, float3 L, float shininess) {
    float diffuse = clamp(dot(n, L), 0.0, 1.0);
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(clamp(dot(n, H), 0.0, 1.0), shininess);
    return Lit{ diffuse, spec };
}

static float3 normalFromHeight(float h, float hx, float hy, float step, float relief) {
    // Screen y points down; the light vector is in the same frame, so no flip.
    return normalize(float3(-(hx - h) / step * relief, -(hy - h) / step * relief, 1.0));
}

// ---- stone ---------------------------------------------------------------

/// `light` is a unit vector, xy in the stone plane (y down), z out of the
/// screen. Tones are straight RGB. `unit` is points per stone (viewBox) unit,
/// so the grain has the same size on every stone at every on-screen size.
[[ stitchable ]] half4 stone(float2 position, half4 color,
                             float unit, float seedValue, float3 light,
                             float3 body, float3 ink, float3 lipLight, float3 lipShadow,
                             float kind, float scale, float relief, float contrast, float sheen,
                             float facetDensity, float glitter, float crack, float banding) {
    if (color.a <= 0.002h) { return color; }
    float2 p = position / max(unit, 0.0001);
    uint seed = uint(seedValue);
    float3 L = normalize(light);
    float3 col = body;
    int m = int(kind);

    if (m == 0) {
        // Lava: crust plates (Worley cells) with dark cracks between, each
        // plate tilted by its own random normal, fine grain over the top.
        float d1, d2;
        float id = worley(p * scale, seed, d1, d2);
        float edge = d2 - d1;
        float plate = smoothstep(crack, crack + 0.05, edge);
        float2 tilt = (float2(hash21(int2(id * 4096.0, 1), seed), hash21(int2(1, id * 4096.0), seed)) - 0.5) * relief * 0.7;
        float g = fractal(p, scale * 8.0, 2, seed + 3u) - 0.5;
        float3 n = normalize(float3(tilt.x + g * relief * 0.6, tilt.y + g * relief * 0.6, 1.0));
        Lit l = lit(n, L, 6.0);
        float shade = 0.45 + 0.85 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        col = mix(ink, col, plate);
        col += sheen * l.spec * lipLight;
    } else if (m == 1) {
        // River: continuous fine grain, one axis stretched into sediment
        // banding, a soft broad specular.
        float2 q = p * float2(1.0, 1.0 + banding * 3.0);
        float step = 0.5;
        float h = fractal(q, scale, 3, seed);
        float hx = fractal(q + float2(step, 0.0), scale, 3, seed);
        float hy = fractal(q + float2(0.0, step), scale, 3, seed);
        float3 n = normalFromHeight(h, hx, hy, step, relief);
        Lit l = lit(n, L, 18.0);
        float shade = 0.6 + 0.6 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        col += sheen * l.spec * lipLight * 0.8;
    } else {
        // Gem: flat facets, one random normal per Worley cell, blinking as
        // the light crosses; sparse glitter where a fine noise peaks.
        float d1, d2;
        float id = worley(p * scale * facetDensity, seed, d1, d2);
        float a = hash21(int2(id * 4096.0, 7), seed) * 2.0 * M_PI_F;
        float t = hash21(int2(9, id * 4096.0), seed) * relief * 0.5;
        float3 n = normalize(float3(cos(a) * t, sin(a) * t, 1.0));
        Lit l = lit(n, L, 48.0);
        float shade = 0.55 + 0.75 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        float seam = smoothstep(0.0, 0.04, d2 - d1);
        col = mix(col * 0.7, col, seam);
        col += sheen * l.spec * lipLight;
        float sparkle = fractal(p, scale * 14.0, 1, seed + 9u);
        float threshold = 1.0 - glitter * 0.12;
        float twinkle = smoothstep(threshold, threshold + 0.03, sparkle) * (0.4 + 0.6 * l.spec);
        col += twinkle * lipLight;
    }

    col = clamp(col, 0.0, 1.0);
    return half4(half3(col) * color.a, color.a);
}

// ---- carve ---------------------------------------------------------------

/// `offset` is the lip width times the direction toward the light, in
/// points. A groove's wall on the light side is in shadow and its far wall
/// catches the light, so: ink with no ink toward the light → shadow lip; ink
/// with no ink away from the light → light lip; the rest of the ink is `ink`.
[[ stitchable ]] half4 carve(float2 position, SwiftUI::Layer layer,
                             float2 offset, float3 ink, float3 lipLight, float3 lipShadow,
                             float lipOpacity) {
    half4 here = layer.sample(position);
    float a = float(here.a);
    if (a <= 0.002) { return here; }
    float toward = float(layer.sample(position + offset).a);
    float away = float(layer.sample(position - offset).a);
    float shadow = (1.0 - toward) * lipOpacity;
    float light = (1.0 - away) * lipOpacity;
    float3 col = mix(ink, lipShadow, shadow);
    col = mix(col, lipLight, light);
    return half4(half3(col) * a, a);
}
```

- [ ] **Step 2: Write the Swift builder**

```swift
#if DEBUG
import SwiftUI

/// Builds the two lab shaders so the views never spell the argument order out.
/// The order here is the `.metal` signature; `StoneMaterial.uniforms` supplies
/// the material tail in the same order (pinned by `StoneMaterialTests`).
enum StoneShaders {

    /// The material over the body. `unit` is points per stone (viewBox) unit.
    static func stone(
        material: StoneMaterial,
        tones: StoneTones,
        palette: StoneLabPalette,
        light: StoneLight,
        unit: CGFloat,
        seed: Int
    ) -> Shader {
        var arguments: [Shader.Argument] = [
            .float(unit),
            .float(Double(seed)),
            .float3(Double(light.vector.x), Double(light.vector.y), Double(light.vector.z)),
            rgb(tones.rgb(tones.body, in: palette)),
            rgb(tones.rgb(tones.ink, in: palette)),
            rgb(tones.rgb(tones.lipLight, in: palette)),
            rgb(tones.rgb(tones.lipShadow, in: palette)),
        ]
        arguments += material.uniforms.map { .float(Double($0)) }
        return Shader(function: ShaderFunction(library: .default, name: "stone"), arguments: arguments)
    }

    /// The groove over the flat ink. `offset` is toward the light, in points.
    static func carve(
        material: StoneMaterial,
        tones: StoneTones,
        palette: StoneLabPalette,
        light: StoneLight
    ) -> Shader {
        let offset = light.lipOffset(width: material.lipWidth)
        return Shader(function: ShaderFunction(library: .default, name: "carve"), arguments: [
            .float2(offset),
            rgb(tones.rgb(tones.ink, in: palette)),
            rgb(tones.rgb(tones.lipLight, in: palette)),
            rgb(tones.rgb(tones.lipShadow, in: palette)),
            .float(material.lipOpacity),
        ])
    }

    private static func rgb(_ v: SIMD3<Float>) -> Shader.Argument {
        .float3(Double(v.x), Double(v.y), Double(v.z))
    }
}
#endif
```

- [ ] **Step 3: Build the app target**

```bash
xcodegen generate >/dev/null && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|warning: .*metal|BUILD" | tail -10
```
Expected: `** BUILD SUCCEEDED **`, no `error:` lines. A Metal compile error names the line in `stone.metal`; fix it there.

- [ ] **Step 4: Commit**

```bash
git add Pebbles/Resources/StoneLab/stone.metal Pebbles/Features/StoneLab/StoneShaders.swift
git commit -m "feat(ios): stone material and carving shaders for the stone lab (#974)"
```

---

### Task 7: StoneView

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneView.swift`

- [ ] **Step 1: Write the view**

```swift
#if DEBUG
import SwiftUI

/// One stone under the lab's light. Bottom to top: the wobbled backdrop
/// silhouette filled with the body tone and painted by the `stone` shader,
/// the carving (outline, veins, fossil, glyph) shaded by `carve`, and a sheen
/// pool masked by the silhouette. `isFlat` draws the feed rendering instead:
/// body tone and ink, no shader, no sheen.
///
/// Nothing here reads time. The view redraws when a parameter changes.
struct StoneView: View {
    let valence: Valence
    let palette: StoneLabPalette
    let material: StoneMaterial
    let tones: StoneTones
    let light: StoneLight
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    var isFlat: Bool = false

    private var size: ValenceSizeGroup { valence.sizeGroup }
    private var width: CGFloat { height * PebbleOutlineGeometry.aspectRatio(for: size) }
    /// A different crust per shape, stable across launches.
    private var seed: Int { Valence.allCases.firstIndex(of: valence).map { $0 * 31 + 7 } ?? 7 }

    var body: some View {
        ZStack {
            bodyLayer
            carvingLayer
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))
            if !isFlat {
                sheenLayer
            }
        }
        .frame(width: width, height: height)
    }

    // MARK: - Body

    @ViewBuilder
    private var bodyLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let shape = WobbledBackdropShape(art: art)
            let fill = shape.fill(tones.color(tones.body, in: palette), style: FillStyle(eoFill: art.usesEvenOddFill))
            if isFlat {
                fill
            } else {
                // Points per viewBox unit for this on-screen size, so the
                // shader's grain is the same size on every stone.
                let unit = width / art.viewBox.width
                fill.colorEffect(StoneShaders.stone(
                    material: material, tones: tones, palette: palette,
                    light: light, unit: unit, seed: seed
                ))
            }
        } else {
            Color.clear
        }
    }

    // MARK: - Carving

    @ViewBuilder
    private var carvingLayer: some View {
        if let art = StoneLabArt.art(for: valence) {
            let ink = ZStack {
                if let fossil = art.fossil {
                    WobbledPathShape(path: fossil.path, layerTransform: .identity, viewBox: art.viewBox)
                        .fill(tones.color(tones.ink, in: palette), style: FillStyle(eoFill: fossil.usesEvenOddFill))
                }
                WobbledPathShape(path: art.ink, layerTransform: .identity, viewBox: art.viewBox)
                    .fill(tones.color(tones.ink, in: palette))
            }
            .compositingGroup()
            if isFlat {
                ink
            } else {
                let lip = CGFloat(material.lipWidth)
                ink.layerEffect(
                    StoneShaders.carve(material: material, tones: tones, palette: palette, light: light),
                    maxSampleOffset: CGSize(width: lip, height: lip)
                )
            }
        }
    }

    // MARK: - Sheen

    @ViewBuilder
    private var sheenLayer: some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            let center = light.poolCenter
            RadialGradient(
                colors: [Color.white.opacity(0.9), Color.white.opacity(0.25), .clear],
                center: UnitPoint(x: center.x, y: center.y),
                startRadius: 0,
                endRadius: width * 0.6
            )
            .blendMode(.screen)
            .opacity(material.sheen)
            .mask {
                WobbledBackdropShape(art: art)
                    .fill(style: FillStyle(eoFill: art.usesEvenOddFill))
            }
        }
    }
}

#Preview("Nine stones · joy") {
    let palette = StoneLabPalettes.all[2]
    return VStack(spacing: 12) {
        ForEach(ValenceSizeGroup.allCases) { size in
            HStack(spacing: 12) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                    StoneView(
                        valence: valence, palette: palette,
                        material: .starting(for: polarity), tones: .starting(for: polarity),
                        light: .rest, height: 110
                    )
                }
            }
        }
    }
    .padding()
    .background(Color.system.background)
}
#endif
```

`Valence.polarity` exists (used by `ValenceStoneView`); `Color.system.background` is the app's theme token used by other previews.

- [ ] **Step 2: Build**

Same build command as Task 6. Expected: `** BUILD SUCCEEDED **`. If `fill(style:)` on the mask does not compile, use `.fill(Color.black, style: …)`.

- [ ] **Step 3: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneView.swift
git commit -m "feat(ios): the lit stone view for the stone lab (#974)"
```

---

### Task 8: StoneLabView — the page

**Files:**
- Create: `apps/ios/Pebbles/Features/StoneLab/StoneLabView.swift`

- [ ] **Step 1: Write the page**

```swift
#if DEBUG
import SwiftUI

/// The stone lab (#974): nine stones under one light, one emotion palette at
/// a time, with the material knobs beside them. Debug-only. Nothing on this
/// page is time-driven; the only animation is the light's spring back to
/// rest on release.
struct StoneLabView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var paletteIndex = 2 // joy: the warmest, easiest to judge
    @State private var light: StoneLight = .rest
    @State private var materials: [ValencePolarity: StoneMaterial] = Dictionary(
        uniqueKeysWithValues: ValencePolarity.allCases.map { ($0, StoneMaterial.starting(for: $0)) }
    )
    @State private var tones: [ValencePolarity: StoneTones] = Dictionary(
        uniqueKeysWithValues: ValencePolarity.allCases.map { ($0, StoneTones.starting(for: $0)) }
    )
    @State private var knobPolarity: ValencePolarity = .neutral
    @State private var isFlat = false
    @State private var isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
    @State private var detailValence: Valence?
    @State private var isKnobsOpen = false

    private var palette: StoneLabPalette { StoneLabPalettes.all[paletteIndex] }
    private var flat: Bool { isFlat || isLowPower }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                emotionPicker
                grid
                knobs
            }
            .navigationTitle("Stone lab")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { dismiss() }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)) { _ in
                isLowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
            .sheet(item: $detailValence) { valence in
                detail(valence)
            }
        }
        .task(priority: .userInitiated) {
            // The nine carvings cost about a second of wobbling; off the main
            // actor, before the grid asks for them.
            await Task.detached(priority: .userInitiated) { StoneLabArt.prewarm() }.value
        }
    }

    // MARK: - Emotion

    private var emotionPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(StoneLabPalettes.all.enumerated()), id: \.element.id) { index, palette in
                    Button {
                        paletteIndex = index
                    } label: {
                        Text(palette.slug.capitalized)
                            .font(.footnote.weight(.medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                Capsule().fill(palette.color(.primary).opacity(index == paletteIndex ? 1 : 0.18))
                            )
                            .foregroundStyle(index == paletteIndex ? palette.color(.light) : palette.color(.dark))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    // MARK: - Grid

    private var grid: some View {
        GeometryReader { proxy in
            let rowHeight = min(130, (proxy.size.height - 32) / 3)
            VStack(spacing: 10) {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 10) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                            stone(valence, height: rowHeight)
                                .frame(maxWidth: .infinity)
                                .onLongPressGesture { detailValence = valence }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
            .gesture(lightDrag(center: CGPoint(x: proxy.size.width / 2, y: proxy.size.height / 2)))
        }
        .padding(.horizontal, 16)
    }

    private func stone(_ valence: Valence, height: CGFloat) -> some View {
        StoneView(
            valence: valence,
            palette: palette,
            material: materials[valence.polarity] ?? .starting(for: valence.polarity),
            tones: tones[valence.polarity] ?? .starting(for: valence.polarity),
            light: light,
            height: height,
            isFlat: flat
        )
    }

    /// The light sits where the finger is, relative to the grid's centre.
    /// Release springs it home so screenshots are repeatable.
    private func lightDrag(center: CGPoint) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                let offset = CGVector(dx: value.location.x - center.x, dy: value.location.y - center.y)
                light = StoneLight(pointingTo: offset, elevationDegrees: light.elevationDegrees)
            }
            .onEnded { _ in
                withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                    light = StoneLight(direction: StoneLight.rest.direction, elevationDegrees: light.elevationDegrees)
                }
            }
    }

    // MARK: - Knobs

    private var knobs: some View {
        VStack(spacing: 0) {
            HStack {
                Toggle("Flat", isOn: $isFlat).toggleStyle(.button)
                Text(isLowPower ? "Low Power: on" : "Low Power: off")
                    .font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button(isKnobsOpen ? "Hide knobs" : "Knobs") { isKnobsOpen.toggle() }
                Button("Reset") {
                    for polarity in ValencePolarity.allCases {
                        materials[polarity] = .starting(for: polarity)
                        tones[polarity] = .starting(for: polarity)
                    }
                    light = .rest
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            if isKnobsOpen {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        Picker("Polarity", selection: $knobPolarity) {
                            ForEach(ValencePolarity.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        materialKnobs
                        toneKnobs
                        lightKnobs
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
                .frame(maxHeight: 280)
            }
        }
        .background(.bar)
    }

    private var materialBinding: Binding<StoneMaterial> {
        Binding(
            get: { materials[knobPolarity] ?? .starting(for: knobPolarity) },
            set: { materials[knobPolarity] = $0 }
        )
    }

    private var tonesBinding: Binding<StoneTones> {
        Binding(
            get: { tones[knobPolarity] ?? .starting(for: knobPolarity) },
            set: { tones[knobPolarity] = $0 }
        )
    }

    @ViewBuilder
    private var materialKnobs: some View {
        let m = materialBinding
        Picker("Material", selection: m.kind) {
            ForEach(StoneMaterial.Kind.allCases) { Text($0.label).tag($0) }
        }
        .pickerStyle(.segmented)
        knob("Scale", m.scale, 0.01...0.6)
        knob("Relief", m.relief, 0...3)
        knob("Contrast", m.contrast, 0...1)
        knob("Sheen", m.sheen, 0...1)
        knob("Lip width", m.lipWidth, 0...4)
        knob("Lip opacity", m.lipOpacity, 0...1)
        switch m.wrappedValue.kind {
        case .lava:
            knob("Crack", m.crack, 0...0.3)
        case .river:
            knob("Banding", m.banding, 0...1)
        case .gem:
            knob("Facet density", m.facetDensity, 0.3...3)
            knob("Glitter", m.glitter, 0...1)
        }
    }

    @ViewBuilder
    private var toneKnobs: some View {
        let t = tonesBinding
        tonePick("Body", t.body)
        tonePick("Ink", t.ink)
        tonePick("Lip light", t.lipLight)
        tonePick("Lip shadow", t.lipShadow)
    }

    @ViewBuilder
    private var lightKnobs: some View {
        knob("Light elevation", Binding(
            get: { light.elevationDegrees },
            set: { light = StoneLight(direction: light.direction, elevationDegrees: $0) }
        ), 10...85)
    }

    private func knob(_ label: String, _ value: Binding<Double>, _ range: ClosedRange<Double>) -> some View {
        HStack {
            Text(label).font(.caption).frame(width: 96, alignment: .leading)
            Slider(value: value, in: range)
            Text(value.wrappedValue.formatted(.number.precision(.fractionLength(2))))
                .font(.caption.monospacedDigit()).frame(width: 44, alignment: .trailing)
        }
    }

    private func tonePick(_ label: String, _ pick: Binding<StoneTones.Pick>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption)
            Picker(label, selection: pick.slot) {
                ForEach(StoneLabPalette.Slot.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            knob("opacity", pick.opacity, 0...1)
        }
    }

    // MARK: - Detail

    private func detail(_ valence: Valence) -> some View {
        VStack(spacing: 16) {
            Text(valence.assetName).font(.caption).foregroundStyle(.secondary)
            stone(valence, height: 320)
                .contentShape(Rectangle())
                .gesture(lightDrag(center: CGPoint(x: 180, y: 160)))
            Spacer()
        }
        .padding(24)
        .presentationDetents([.large])
    }
}

extension Valence: Identifiable {
    var id: String { rawValue }
}
#endif
```

If `Valence` already conforms to `Identifiable` (it does: `enum Valence: String, CaseIterable, Identifiable`), delete the extension at the bottom.

- [ ] **Step 2: Build**

Same build command. Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add Pebbles/Features/StoneLab/StoneLabView.swift
git commit -m "feat(ios): the stone lab page — grid, emotion picker, light drag, knobs (#974)"
```

---

### Task 9: Debug entry from the settings sheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` (state block near line 30, the `List` body near line 117, the modifiers near line 151, and a new section after `legalSection`)

- [ ] **Step 1: Add the state, the section, and the cover**

State, next to `isPresentingDeleteConfirm`:

```swift
    #if DEBUG
    @State private var isPresentingStoneLab = false
    #endif
```

In the `List`, after `legalSection`:

```swift
                legalSection
                #if DEBUG
                developerSection
                #endif
                deleteAccountSection
```

Next to the `.sheet(item: $presentedLegalDoc)` modifier:

```swift
            #if DEBUG
            .fullScreenCover(isPresented: $isPresentingStoneLab) {
                StoneLabView()
            }
            #endif
```

After `legalSection`'s definition:

```swift
    #if DEBUG
    /// Debug builds only: the stone material lab (#974).
    private var developerSection: some View {
        Section {
            Button { isPresentingStoneLab = true } label: {
                Label("Stone lab", systemImage: "sparkles")
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .only)
        } header: {
            Text("Developer").pebblesSectionHeader()
        }
    }
    #endif
```

`"Stone lab"` and `"Developer"` are `LocalizedStringKey` literals and will be auto-extracted into `Localizable.xcstrings` on build. Because the row is Debug-only, mark both entries in the catalog as **Don't translate** (in Xcode: select the row, Localization state → "Don't Translate"), so the `New` state does not block the PR checklist's catalog check. Alternatively use `Text(verbatim:)` for both labels and skip the catalog entirely; prefer `verbatim` if the catalog UI is not to hand.

- [ ] **Step 2: Build and run the full unit suite**

```bash
xcodegen generate >/dev/null && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 | grep -E "Test Suite 'All tests'|error:|failed" | tail -5
```
Expected: `Test Suite 'All tests' passed`.

- [ ] **Step 3: Commit**

```bash
git add Pebbles/Features/Profile/Sheets/SettingsSheet.swift Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): open the stone lab from a debug row in settings (#974)"
```

---

### Task 10: Visual verification on the simulator

**Files:**
- Create (temporary, deleted before commit): `apps/ios/PebblesTests/StoneLab/StoneLabSnapshotTests.swift`

- [ ] **Step 1: Write the temporary snapshot test**

`ImageRenderer` does render SwiftUI Metal effects on the simulator. If a PNG comes out with flat bodies, fall back to the on-screen method in project memory (root view controller swap plus `xcrun simctl io booted screenshot`).

```swift
import Testing
import SwiftUI
@testable import Pebbles

@MainActor
@Suite("StoneLab snapshots (temporary)")
struct StoneLabSnapshotTests {
    private let outDir = "/private/tmp/claude-503/-Users-alexis-code-pbbls/e1250e2f-cf0c-4d6f-89f4-bb3f5623f0f9/scratchpad"

    @Test("nine stones per palette, rest light and raked light")
    func grid() throws {
        StoneLabArt.prewarm()
        for palette in StoneLabPalettes.all {
            for (label, light) in [("rest", StoneLight.rest),
                                   ("raked", StoneLight(direction: CGVector(dx: 1, dy: 0), elevationDegrees: 25))] {
                let view = VStack(spacing: 12) {
                    ForEach(ValenceSizeGroup.allCases) { size in
                        HStack(spacing: 12) {
                            ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                                let valence = Valence.allCases.first { $0.sizeGroup == size && $0.polarity == polarity }!
                                StoneView(valence: valence, palette: palette,
                                          material: .starting(for: polarity), tones: .starting(for: polarity),
                                          light: light, height: 120)
                            }
                        }
                    }
                }
                .padding(16)
                .frame(width: 390)
                .background(Color.white)
                let renderer = ImageRenderer(content: view)
                renderer.scale = 2
                let png = try #require(renderer.uiImage?.pngData())
                try png.write(to: URL(fileURLWithPath: "\(outDir)/stones-\(palette.slug)-\(label).png"))
            }
        }
    }
}
```

- [ ] **Step 2: Run it and look at the PNGs**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build
xcodegen generate >/dev/null && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/StoneLabSnapshotTests 2>&1 | grep -E "passed|failed|error:" | tail -3
ls /private/tmp/claude-503/-Users-alexis-code-pbbls/e1250e2f-cf0c-4d6f-89f4-bb3f5623f0f9/scratchpad/stones-*.png | wc -l
```
Expected: 14 PNGs. Open `stones-joy-rest.png` and `stones-joy-raked.png` with the Read tool and check, in this order:

1. All nine silhouettes are filled (no empty cell): the seeds and outlines both loaded.
2. Lowlight stones show plates and cracks, neutral a fine grain, highlight facets and a few bright points.
3. Between rest and raked, the bright side of the carving lips moves with the light.
4. The carving is inside the stone, 12% in from the edge, glyph placed in its slot per size.

If the bodies are flat colour with no material, the shader did not run under `ImageRenderer`; switch to the on-screen method and capture with `xcrun simctl io booted screenshot`.

- [ ] **Step 3: Tune only what is broken**

The starting numbers are guesses. If a material is unreadable at 120 pt (grain too fine to see, cracks covering the stone, glitter everywhere), change the `starting(for:)` value in `StoneMaterial.swift`, re-run the snapshot test, and stop when each material reads as its name. Do not chase beauty here; the page's sliders are for that, in the maintainer's hand.

- [ ] **Step 4: Delete the temporary test, send the PNGs, commit any tuning**

```bash
rm PebblesTests/StoneLab/StoneLabSnapshotTests.swift
git status --short
git add Pebbles/Features/StoneLab/StoneMaterial.swift
git commit -m "feat(ios): stone lab starting materials tuned against the simulator render (#974)"
```
Skip the commit if nothing was tuned. Send `stones-joy-rest.png`, `stones-joy-raked.png` and `stones-sadness-rest.png` to the user with `SendUserFile` so they see the render before the PR.

---

### Task 11: Lint, full build, PR

- [ ] **Step 1: Lint and build the workspace**

```bash
npm run lint --workspace=@pbbls/ios 2>&1 | tail -5
npm run build --workspace=@pbbls/ios 2>&1 | grep -E "error:|BUILD" | tail -3
```
Expected: SwiftLint reports 0 serious violations in `Features/StoneLab/` (fix any it reports; `identifier_name` on `d` is already disabled inline), and `** BUILD SUCCEEDED **`.

- [ ] **Step 2: Confirm Release compiles nothing from the lab**

```bash
xcodebuild -scheme Pebbles -configuration Release -destination 'generic/platform=iOS Simulator' build 2>&1 | grep -E "error:|BUILD" | tail -3
```
Expected: `** BUILD SUCCEEDED **`. If Release fails on a `StoneLabView` reference, a `#if DEBUG` guard is missing at that call site.

- [ ] **Step 3: Push and open the PR**

```bash
git push -u origin feat/974-ios-stone-material-lab
gh pr create --title "feat(ios): stone material lab — nine shapes, three materials, seven emotions" \
  --label feat --label ios --label ui --label no-lab-note \
  --body "$(cat <<'EOF'
Resolves #974

A Debug-only page (Profile → Settings → Developer → Stone lab) to judge the polished pebble render before any shipped surface takes it: the nine engine shapes as lava / river / gem stone by polarity, coloured by any of the seven emotion palettes, with outline, veins, fossil and a sample glyph carved into the surface under a draggable light.

**Key files**
- `Pebbles/Resources/StoneLab/stone.metal` — noise (from the Femfolk card POC), the `stone` colour effect, the `carve` layer effect
- `Pebbles/Features/StoneLab/StoneView.swift` — body, material, carving, sheen; flat mode for Low Power
- `Pebbles/Features/StoneLab/StoneLabView.swift` — grid, emotion picker, light drag, knobs, long-press detail
- `Pebbles/Features/StoneLab/StoneLabArt.swift` — wobbled carving per valence, glyph placed with the engine's slot table
- `Pebbles/Features/StoneLab/StoneShapeParser.swift` — order-based split of an engine seed into outline / veins / fossil

**Notes**
- Every shader is a pure function of its uniforms; nothing on the page is time-driven. Low Power Mode and the Flat toggle draw the feed rendering (body tone + ink).
- All Swift under `Features/StoneLab/` is `#if DEBUG`; Release builds compile none of it.
- Spec: `docs/superpowers/specs/2026-09-27-ios-stone-material-lab-design.md`. Plan: `docs/superpowers/plans/2026-09-27-ios-stone-material-lab.md`.
- Follow-ups (not here): the detail page takes the material live with the phone's tilt; the feed takes it still.

No Lab Note: Debug-only, nothing a user can see.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01VFkaZQoivws4LW6i2fiv3e
EOF
)"
```

Before running `gh pr create`, ask the user which milestone the PR takes (none of the open milestones names this work; "Quality & Tooling" is the nearest) and add `--milestone` accordingly. The PR checklist forbids opening without one unless the user says there is none.

---

## Self-review

**Spec coverage.** Orthogonal model → Tasks 3, 4, 5, 7. Body = backdrop silhouette, carving = engine parts + glyph in the engine slot → Task 5, 7. Layer model (body, material, carving, sheen; flat under Low Power) → Task 7. Shader with the listed uniforms → Task 6 (`size` became `unit`, the same information in the form the shader needs). Page: grid, emotion switcher, light drag with spring-back, knobs drawer, Flat and Low Power, Reset, long-press detail → Task 8. Debug entry → Task 9. Battery contract (no time input, Low Power flat) → Tasks 6, 7, 8. Tests: parser, palettes vs migration, material starting tables and uniform order → Tasks 2, 3, 4; art per valence → Task 5. Visual judgement PNGs → Task 10. Out of scope untouched: no change to `WobbleRenderer`, the engine, or any shipped view.

**Placeholders.** None; every code step is complete. Task 10's tuning step is deliberately bounded ("only what is broken").

**Type consistency.** `StoneMaterial.uniforms` order = `StoneShaders.stone` tail = `stone.metal` parameters after `lipShadow` (kind, scale, relief, contrast, sheen, facetDensity, glitter, crack, banding). `StoneLight.vector`, `lipOffset(width:)`, `poolCenter` are used by `StoneShaders` and `StoneView` under those names. `StoneTones.Pick.slot`/`.opacity` and `StoneLabPalette.Slot` match between Tasks 3, 4, 8. `StoneLabArt.Art.ink/fossil/viewBox` match Task 7. `WobbleRenderer.glyphInk(d:width:)`, `backdropArt(size:polarity:)`, `backdropArt(fromAsset:)`, `WobbledBackdropShape(art:)`, `WobbledPathShape(path:layerTransform:viewBox:)`, `PebbleOutlineGeometry.pebbleScale(for:)`/`aspectRatio(for:)`, `Valence.polarity/sizeGroup/assetName`, `EmotionCategoryOrdering.default`, `Color(hex:)` all exist in the codebase today.
