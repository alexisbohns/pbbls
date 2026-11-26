# iOS vector stone Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS paints every pebble stone with the web/Android vector construction instead of the Metal material, so a pebble id draws the same stone on all three surfaces.

**Architecture:** Pure-Swift ports of the web's stone modules (geometry, tones, recipe, carvings, glyph line, grain) feed a `GraphicsContext` painter. The painter renders one image per stone spec into a cache. `PebbleStoneView` and its siblings draw that image behind their current APIs, and the Metal stack is deleted last. Android's port (`apps/android/app/src/main/kotlin/app/pbbls/android/core/ui/render/stone/`) is the structural template; its tests already transcribe the web's numbers, so iOS transcribes Android's tests case for case.

**Tech Stack:** SwiftUI (`Canvas`/`GraphicsContext`, `ImageRenderer`), Swift Testing, xcodegen.

**Spec:** `docs/superpowers/specs/2026-11-26-ios-vector-stone-design.md` (issue #1690).

## Global Constraints

- **Platform.** iOS 17 APIs only, `@Observable`, Swift Testing (`@Suite`/`@Test`/`#expect`), never XCTest. `os.Logger`, never `print`.
- **Where the code lives.** New stone code lives in `apps/ios/Pebbles/Features/Path/Stone/Lit/`, because `Features/Path/Stone/` is the only folder `ThemeLiteralsTests` exempts from the colour-literal ban. Tests go in `apps/ios/PebblesTests/Stone/Lit/`. Names carry a `Lit` prefix (`LitStoneGeometry`, …), because `StoneGeometry`, `StoneTones` and `StoneCarving` are already taken on iOS.
- **Tone convention.** Palette slots: `dark` is the darkest tone, `shaded` the mid one. Web `--p/--s/--l/--sh/--d` map to `StonePalette.primary/secondary/light/shaded/dark`.
- **Seed.** `StableHash.seed(id)`, the full 32 bits, never folded to 24.
- **Battery contract.** No time input in any paint. Low Power Mode draws flat: body + ink only, no grain, blur, shadows or glint.
- **Determinism.** Never iterate a `Set` or `Dictionary` while drawing. A reference whose two recordings differ blocks the re-baseline bot.
- **Line weights (spec D5).** The px constants (`CARVE_STROKE_PX` 1.25, `LIP_PX`, `SHADOW_*_PX`, AO and edge widths, grain) are points × display scale, whatever the stone's size, as in `StonePainter.kt`. The carve and glyph strokes are also multiplied by Android's `carveGain`.
- **iOS test runs.** Use a dedicated simulator per worktree: `xcrun simctl create "iPhone 17 vector-stone" "iPhone 17" <latest iOS runtime>`. Pass `-destination 'id=<that udid>' -derivedDataPath $SCRATCH/dd-stone`. After `xcodegen generate`, delete `$SCRATCH/dd-stone/Build` before trusting a result. Filter with `-only-testing:PebblesTests/<SuiteType>` at suite level, never a single test.
- **Reference renders.** 27 fail locally on untouched main. The local gate is the same failing set, with byte-identical PNGs against a baseline run; CI is the real gate.

## Review Focus

1. **A night-scheme stone.** Web dims river and gem (`brightness(.92) saturate(.96)`) and brightens lava (`brightness(1.2) saturate(1.05)`). A stone painted at night must differ from the day one, and lava must get lighter, not darker. Test in Task 7.
2. **A moment with no pebble yet (blank).** It paints a grey neutral river pebble, uncarved, whatever valence it is given. No glyph, veins or fossil. Test in Task 7.
3. **A glyph with an unreadable or missing viewBox.** `glyphSide` falls back to 200 and the glyph still draws; a glyph `d` that doesn't parse draws the plain `d`. Tests in Tasks 1 and 5.
4. **The same stone at 34 pt (Spine) and 240 pt (richer step).** The carve line's on-screen width is the same number of points times the gain, not scaled with the stone. Test in Task 7.
5. **A Spine week of 60 stones.** Nothing repaints on scroll. The cache returns the same image for the same spec and evicts under memory pressure (`NSCache`). Test in Task 8.

---

## Part 1 — geometry, tones, recipe, carvings

Branch `feat/1690-vector-stone-logic-ios`, from updated `origin/main`. No UI changes. One PR.

### Task 1: Geometry

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneGeometry.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneGeometryTests.swift`

**Interfaces:**
- Produces: `enum LitStoneGeometry`, with:
  - **Constants:**
    - `carveScale = 1.22`
    - `carveStrokePx = 1.25`
    - `shadowFarPx = CGVector(dx: 3.86, dy: 6.95)`
    - `shadowNearPx = CGVector(dx: 0.93, dy: 2.01)`
    - `lipPx = CGVector(dx: 0.32, dy: 0.54)`
    - `blur: (near: 5, far: 15, ao: 7, soft: 12, glow: 4)`
  - **Functions:**
    - `static func phoneWidth(_ size: ValenceSizeGroup) -> CGFloat`
    - `static func unitsPerPx(_: ValenceSizeGroup) -> CGFloat`
    - `static func seedCanvas(_: ValenceSizeGroup) -> CGSize`
    - `static func canvasScale(_: ValenceSizeGroup) -> CGFloat`
    - `static func canvasTransform(_: ValenceSizeGroup, _: ValencePolarity) -> CGAffineTransform`
    - `static func carveTransform(_:_:) -> CGAffineTransform`
    - `static func glyphTransform(_: ValenceSizeGroup, _: ValencePolarity, side: CGFloat) -> CGAffineTransform`
    - `static func glyphSide(viewBox: String) -> CGFloat` (200 when unreadable)
    - `static func silhouetteFrame(_ d: String) -> CGRect`
    - `static func outlineBox(_: ValenceSizeGroup) -> CGSize` (from `PebbleOutlineGeometry`)
- **Source to port:** `apps/web/lib/config/stone-geometry.ts`, with `StoneGeometry.kt` as the shape. Skip `ringGeometry`; `StoneRingGeometry` already covers it on iOS and is not touched.

- [ ] **Step 1:** Write `LitStoneGeometryTests`, transcribing every case of `apps/android/app/src/test/kotlin/app/pbbls/android/core/ui/render/stone/StoneGeometryTest.kt` except the two ring cases and the cache case. Use the same expected numbers, to 3 decimals, compared with `abs(a - b) < 0.0005`. Include `glyphSide(viewBox: "nonsense") == 200`.
- [ ] **Step 2:** `cd apps/ios && xcodegen generate`, then run `xcodebuild test … -only-testing:PebblesTests/LitStoneGeometryTests`. Expected: build failure on the missing type.
- [ ] **Step 3:** Implement `LitStoneGeometry`. The transforms compose as `translate`, then `scale`, in the web's order. Matrix values must equal the web strings' numbers.
- [ ] **Step 4:** Run the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): port the stone geometry from web`.

### Task 2: Tones

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneTones.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneTonesTests.swift`

**Interfaces:**
- Consumes: `StonePalette` (existing, `Features/Path/Stone/StonePalette.swift`).
- Produces:
  - `struct LitPaint { color: Color; opacity: Double; width: CGFloat?; offset: CGVector }`
  - `struct LitToneStop { offset: Double; color: Color }`
  - `enum LitStoneTones`, with:
    - `bodyRamp(_ polarity: ValencePolarity, _ palette: StonePalette) -> [LitToneStop]`
    - `glyphTones(_:_:) -> (lip: LitPaint, ink: LitPaint)`
    - `veinTones(_:_:) -> [LitPaint]`
    - `fossilTones(_:_:) -> [LitPaint]`
    - `edgeShade(_:_:) -> LitPaint`
    - `glintStrength(_:) -> Double`
    - `grainStrength(_:) -> Double`
    - `slotColor(_ slot: LitSlot, _ palette: StonePalette) -> Color`
    - `blankPalette: StonePalette`
    - `nightAdjust(_ polarity: ValencePolarity) -> (brightness: Double, saturation: Double)`: `(0.92, 0.96)` for river and gem, `(1.2, 1.05)` for lava
    - `mixSrgb(_ a: Color, _ pct: Double, _ b: Color) -> Color`
    - `mixOklab(_ a: Color, _ pct: Double, _ b: Color) -> Color`
  - `enum LitSlot { case primary, secondary, light, shaded, dark }`: the recipe's slot names.
- **Source to port:** `apps/web/lib/config/stone-tones.ts` and `StoneTones.kt` (its `mixSrgb`/`mixOklab` and night matrix are the browser's behaviour written out).

- [ ] **Step 1:** Write `LitStoneTonesTests`, transcribing every case of Android's `StoneTonesTest.kt` over the same fixture palette (`apps/web/lib/seed/sandbox-palettes.ts`, pride). Compare colours as 0–255 sRGB channels within ±1.
  - Also assert that `blankPalette` equals web `BLANK_PALETTE` hex for hex.
  - Also assert that `StonePalette.blank` is left untouched; it is not this palette.
- [ ] **Step 2:** Run the suite and confirm it fails.
- [ ] **Step 3:** Implement. Resolve colours in sRGB with `Color.resolve(in:)` and an explicit light environment. Never read the ambient scheme.
- [ ] **Step 4:** Run the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): port the stone tones from web`.

### Task 3: Recipe

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneRecipe.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneRecipeTests.swift`

**Interfaces:**
- Consumes: `LitStoneGeometry`, `LitSlot`.
- Produces:
  - Value types:
    - `struct LitDot { cx, cy, r, opacity: Double; slot: LitSlot }`
    - `struct LitFacet { points: [CGPoint]; slot: LitSlot; opacity: Double; edge: (CGPoint, CGPoint)? }`
    - `struct LitEllipse { cx, cy, rx, ry, rotate, opacity: Double }`
  - `enum LitStoneRecipe` with three cases:
    - `.river(dots: [LitDot], light: LitEllipse)`
    - `.lava(dots: [LitDot], light: LitEllipse)`
    - `.gem(facets: [LitFacet], dots: [LitDot], sparkles: [Path])`
  - `static func of(_: ValenceSizeGroup, _: ValencePolarity, seed: UInt32) -> LitStoneRecipe`
  - `struct LitGlintGeometry` and `static func glint(_: ValenceSizeGroup) -> LitGlintGeometry`
  - `static func starPath(cx:cy:r:) -> Path`
  - `static func random(seed: UInt32) -> () -> Double` (mulberry32, as web's `random`)
- **Source to port:** `apps/web/lib/utils/spine/stone-recipe.ts`, with `StoneRecipe.kt` as the shape. Rounding is web's `r2`, `floor(n * 100 + 0.5) / 100`.

- [ ] **Step 1:** Write `LitStoneRecipeTests`, transcribing every case of Android's `StoneRecipeTest.kt`, including the verbatim web numbers for the fixture seed and `mulberry32 matches JS for seed 1`. Add one case: `StableHash.seed(UUID("…fixture…"))` equals the web's `hashUUID` value given in that file.
- [ ] **Step 2:** Run the suite and confirm it fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the suite. Expected: PASS.
- [ ] **Step 5:** Commit: `feat(ios): port the stone recipe from web`.

### Task 4: Carvings

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneCarvings.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneCarvingsTests.swift`

**Interfaces:**
- Consumes: `StoneShapeParser.parse(_:)` (existing) over the bundled `stone-shape-<size>-<polarity>.svg` seeds. Load them the way `StoneCarvingArt` loads them today.
- Produces:
  - `struct LitStoneCarving { veins: [(path: Path, width: Double)]; fossil: (path: Path, evenOdd: Bool)? }`
  - `enum LitStoneCarvings { static func carving(_: ValenceSizeGroup, _: ValencePolarity) -> LitStoneCarving }`
  - The carvings are memoized in a fixed array indexed by size × polarity, not a dictionary walked at draw time.
- **Reference:** web `apps/web/lib/config/stone-carvings.ts` (the table) and `StoneCarvings.kt` (parses at load).

- [ ] **Step 1:** Write `LitStoneCarvingsTests`, transcribing Android's `StoneCarvingsTest.kt`. Add one case per seed: the vein `d` strings and the fossil `d` equal the web table's `STONE_CARVINGS[size][polarity]`, copied verbatim into the test.
- [ ] **Step 2:** Run the suite and confirm it fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the suite. Expected: PASS. Also run `ThemeLiteralsTests` and confirm it passes.
- [ ] **Step 5:** Commit: `feat(ios): read the stone carvings as web draws them`.
- [ ] **Step 6:** Push and open the Part 1 PR.
  - **Title:** `feat(ios): port the vector stone's geometry, tones, recipe and carvings`
  - **Body:** start with `Part of #1690`, and end with `Data safety: no change`.
  - **Labels and milestone:** `feat`, `ui`, `ios`, milestone `M70 · Record flow`.
  - **Stack:** this plan file rides in the docs PR (#1694) at the bottom of the stack.

## Part 2 — glyph line, grain, painter, cache, gallery

Branch `feat/1690-vector-stone-painter-ios`, stacked on Part 1 (`gh stack`). Nothing that ships changes.

### Task 5: Glyph line

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitGlyphLine.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitGlyphLineTests.swift`, plus `LitGlyphLineGolden.json` beside it

**Interfaces:**
- Consumes: the existing wobble primitives in `Features/Path/Render/Wobble/`: the path parser, the flattener, `WobbleParams` scaled parameters, centreline displacement and `SVGTurbulence`. These are the same primitives `WobbleRenderer.glyphInk` uses before it outlines.
- Produces: `enum LitGlyphLine { static func line(d: String, side: Double) -> Path? }`. It returns nil when `d` doesn't parse. It is memoized by `"\(side)|\(d)"`.
- **Source to port:** web `wobbleGlyphLine` (`apps/web/lib/wobble/renderer.ts:106`): flatten, displace the centreline, and return it as a stroke. Never outline it.

- [ ] **Step 1:** Generate the golden. From `apps/web`, run `npx tsx -e` and import `wobbleGlyphLine` from `./lib/wobble/renderer`. Print the output for the first two strokes of the `friends` domain glyph in `docs/seeds/domain-glyph-seed.json`, at its viewBox side. Save `{d, side, out}` triples to the JSON.
- [ ] **Step 2:** Write the tests:
  - every golden `out` path's points match iOS within 0.01;
  - `line(d: "garbage", side: 200) == nil`.
- [ ] **Step 3:** Run the suite and confirm it fails.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run the suite and confirm it passes. Run `WobbleRendererTests` and confirm it is unchanged.
- [ ] **Step 6:** Commit: `feat(ios): draw the stone glyph as web's wobbled line`.

### Task 6: Grain

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneGrain.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Render/Wobble/SVGTurbulence.swift`, adding a public fractal-noise sampler only if one is missing.
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneGrainTests.swift`

**Interfaces:**
- Produces: `enum LitStoneGrain { static func tile(scale: CGFloat) -> CGImage }`. The tile is cached per scale.
- **Recipe:** web's grain filter (`StoneSprite.tsx:99–110`).
  - `feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="7"`, sampled in screen px.
  - A dark layer: black with alpha `6·R − 3.5`.
  - A light layer: white with alpha `4·G − 2.75`.
  - The two layers merged, then clamped to [0, 1].
  - Only the R and G channels are read, so the spec's "all four channels" claim is wrong. Correct the spec line in this task's commit.
- Android's `drawGrain`/`grainTile` in `StonePainter.kt` is the reference port.

- [ ] **Step 1:** Generate goldens. In the browser pane, render a 64×64 `<svg>` with exactly that filter over a rect, `drawImage` it onto a canvas, and read 8 pixel RGBA values at fixed coordinates. Paste them into the test.
- [ ] **Step 2:** Write the tests:
  - the tile's same 8 pixels match within ±2 per channel;
  - two calls return the same `CGImage` instance.
- [ ] **Step 3:** Run the suite and confirm it fails.
- [ ] **Step 4:** Implement.
- [ ] **Step 5:** Run the suite and confirm it passes. Run `SVGTurbulenceTests` and confirm it is unchanged.
- [ ] **Step 6:** Commit: `feat(ios): grain the stone as web does`.

### Task 7: Painter

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStonePainter.swift`
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneSpec.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStonePainterTests.swift`

**Interfaces:**
- Consumes: Tasks 1–6. The silhouettes come from `WobbleRenderer.backdropArt(size:polarity:)` for lowlight (wobbled, D7) and `softBackdropArt` for the others.
- Produces:
  - `struct LitStoneSpec: Hashable`, with these fields:
    - `size: ValenceSizeGroup`
    - `polarity: ValencePolarity`
    - `palette: StonePalette?` (nil = blank)
    - `glyph: PebbleGlyph?`
    - `seed: UInt32`
    - `night: Bool`
    - `flat: Bool`
  - `enum LitStonePainter`, with:
    - `static func paint(_ spec: LitStoneSpec, in context: inout GraphicsContext, box: CGSize, scale: CGFloat)`
    - `static func shadowMargin(box: CGSize) -> EdgeInsets`
- **Order:** the spec's §Layers list, exactly. Android's `StonePainter.kt` is the line-by-line reference.
- **Blank:** a blank spec is neutral polarity with `blankPalette`, and draws no carving and no glyph.
- **Flat:** body fill with the ramp's middle stop, plus the glyph ink. Nothing else.

- [ ] **Step 1:** Write the tests, painting through `ImageRenderer` at scale 3 and reading pixels. They transcribe Android's `StonePainterTest.kt` cases:
  - the centre is filled and the corner is clear;
  - all 9 valences paint and cover the centre;
  - the same spec painted twice is byte-identical;
  - a blank spec is grey;
  - night differs, and lava's mean luminance rises at night.

  Add these cases:
  - **Flat:** a flat spec has no pixel outside the silhouette and no grain variance on a flat palette.
  - **Constant carve weight:** the glyph ink's stroke width measured across a straight segment is the same in points at box widths 44 and 240, within 0.25 pt.
- [ ] **Step 2:** Run the suite and confirm it fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the suite and confirm it passes.
- [ ] **Step 5:** Commit: `feat(ios): paint the vector stone`.

### Task 8: Cache and view

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneCache.swift`
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneImage.swift`
- Test: `apps/ios/PebblesTests/Stone/Lit/LitStoneCacheTests.swift`

**Interfaces:**
- Produces:
  - `final class LitStoneCache`, with:
    - `static let shared`
    - `func image(_ spec: LitStoneSpec, box: CGSize, scale: CGFloat) -> CGImage`
    - Backed by `NSCache` with a `countLimit` of 400, keyed by spec + pixel size + scale.
  - `struct LitStoneImage: View`, with `init(spec: LitStoneSpec)`:
    - lays out in the outline's aspect ratio;
    - draws the cached image unclipped, offset by `shadowMargin` (D6);
    - reads the scheme and `isLowPowerModeEnabled` once, into `night` and `flat`;
    - redraws only when its inputs change.

- [ ] **Step 1:** Write the tests:
  - the same spec, box and scale return an identical `CGImage` (`===`);
  - another seed returns another image;
  - the same pixel box at scale 2 vs 3 gives different keys;
  - the image is larger than the box by the margin, and the box's pixels sit where an unpadded paint draws them.
- [ ] **Step 2:** Run the suite and confirm it fails.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the suite and confirm it passes.
- [ ] **Step 5:** Commit: `feat(ios): cache the vector stone as an image`.

### Task 9: Gallery

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/SpineStoneGallery.swift`

- [ ] **Step 1:** Add a "Vector" column beside each existing stone: the 9 valences × the gallery's palettes, plus blank. It sits behind the existing `#if DEBUG`.
- [ ] **Step 2:** Visual check. In a temporary `ImageRenderer` test, render the gallery rows (no `ScrollView`) to PNGs on the host file system.
  - Compare them with the web `/sandbox/design` stones in the browser pane.
  - Note the differences in the PR body.
  - Delete the temporary test before committing.
- [ ] **Step 3:** Commit: `chore(ios): show the vector stone beside the metal one in the gallery`.
- [ ] **Step 4:** Push and open the Part 2 PR.
  - **Title:** `feat(ios): paint and cache the vector stone`
  - **Body:** `Part of #1690`, then the gallery PNGs, then `Data safety: no change`.
  - **Labels and milestone:** the same as Part 1.

## Part 3 — switch-over

Branch `feat/1690-vector-stone-switch-ios`, stacked on Part 2.

### Task 10: Every stone draws the vector stone

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/PebbleStoneView.swift`. Its body becomes `LitStoneImage`. `followsTilt`, the sweep, the motion and `StoneView` go. `seed: UInt32` becomes required (D4). `.blank(seed:)` stays.
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Stone/SpineStone.swift`. It passes the seed through unfolded.
- Modify: `apps/ios/Pebbles/Features/Path/Card/VersoStone.swift`.
- Modify: `apps/ios/Pebbles/Components/PebbleRow.swift`. It passes `StableHash.seed(pebble.id)`.
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteRicherStep.swift`. It passes `StableHash.seed(result.pebbleId)`, with no tilt.
- Modify: `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneView.swift` and `apps/ios/Pebbles/Features/Path/ValencePickerContent.swift`, for D8:
  - no `lean`, no `motion.start()`;
  - on selection, a `StonePress` plays `StoneMotion`'s dip + glint once;
  - seed is `LitStoneSeed.sample(valence)`;
  - glyph is the `stone-glyph.json` sample, as today.
- Create: `apps/ios/Pebbles/Features/Path/Stone/Lit/LitStoneSeed.swift`, with `enum LitStoneSeed { static func sample(_ valence: Valence) -> UInt32 }`. It returns `StableHash.fnv1a("sample-\(valence.rawValue)")`.
- Modify: every other file listed in the spec's Goals that constructs a stone. Grep `StoneView(`, `PebbleStoneView(`, `.blank(`, `SpineStone(`, `VersoStone(` and `ValenceStoneView(` to confirm none is missed.
- Test: update the existing stone tests that construct these views, so the seed is required.

- [ ] **Step 1: Baseline.** On the untouched Part 2 head, run `-only-testing:PebblesTests/References…` (every References suite). Save the failing list and copy `build/reference-diffs/` to `$SCRATCH/ref-baseline/`.
- [ ] **Step 2:** Make the changes. Build and confirm there are no warnings in the touched files.
- [ ] **Step 3:** Run the full unit suite, excluding References, and confirm it passes. Run References and confirm the failures are the expected moved stones. List them in the PR.
- [ ] **Step 4:** Append a decision-log entry, `2026-11-26 — iOS draws the web and Android stone; the tilt light goes (#1690)`. It supersedes both 2026-09-27 Metal render entries and covers D1, D4–D8.
- [ ] **Step 5:** Commit: `feat(ios): draw every stone with the vector stone`.
- [ ] **Step 6:** Push and open the Part 3 PR.
  - **Body:** `Part of #1690`, the moved references, the decision entry, and `Data safety: no change`.
  - **Re-baseline:** add the `rebaseline-ios-screenshots` label alone, once CI's References job has run red. Never re-record locally.

## Part 4 — deletion

Branch `chore/1690-metal-stone-removal-ios`, stacked on Part 3.

### Task 11: Delete the Metal stone

**Files (delete each only after a grep shows no caller outside the deletion set):**
- `apps/ios/Pebbles/Resources/Stone/stone.metal`
- In `apps/ios/Pebbles/Features/Path/Stone/`: `StoneView.swift`, `StoneMaterial.swift`, `StoneLook.swift`, `StoneLight.swift`, `StoneSweeps.swift`, `StoneShaders.swift`, `StoneTones.swift` and `StoneGlintInk.swift` (if only the shader reads it), plus whatever of `StoneCarving.swift` and `StoneCarvingArt.swift` only the shader path reads.
  - Keep `StoneCarvingArt`'s tables: the web glyph-zone drift test reads them.
- `apps/ios/Pebbles/Features/Path/Stone/Motion/` (the whole folder).
- `apps/ios/Pebbles/Features/StoneLab/` (the whole folder), plus its Settings → Developer entry.
- `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`, `PebbleStaticRenderView.swift`, `PebbleOutlineBackdropView.swift` and `PebbleAnimationTimings.swift`, if unused.
- The tests of every deleted subject: `PebblesTests/Stone/BodyTiltTests.swift`, `LeanMapperTests.swift`, `PebblesTests/StoneLab/*`, the `StoneMaterialTests` and kin.
- `ValenceStoneStyle` stays (`headlineInk`).

- [ ] **Step 1:** Delete. Then grep for each deleted type name across `apps/ios` and confirm there are 0 hits outside comments, and fix those comments.
- [ ] **Step 2:** Run `xcodegen generate` and clear the derived-data `Build` folder. Build, then run the full unit suite. Expected: PASS. Run References and confirm the same failing set as Part 3's head.
- [ ] **Step 3:** Commit: `chore(ios): delete the metal stone, its lab and the tilt motion`.
- [ ] **Step 4:** Push and open the Part 4 PR.
  - **Body:** `Resolves #1690`, then the deletion list, then `Data safety: no change`.
  - **Labels:** `chore`, `ios`, plus the M70 milestone.

## After Part 3

Write `docs/superpowers/plans/2026-11-26-ios-stone-reveal-ceremony.md` for #1691 against the APIs this plan produces (`LitStoneSpec`, `LitStoneImage`, `LitGlyphLine`, `StoneMotion`). Stack it on Part 3.
