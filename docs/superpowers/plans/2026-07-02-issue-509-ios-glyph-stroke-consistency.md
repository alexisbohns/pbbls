# iOS Glyph Stroke Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On iOS, make the Path row draw the pebble's glyph at the same stroke weight as the outline, so it matches the detail page instead of rendering thinner.

**Architecture:** Render the Path pebble through the same layer-tracing path the animated detail view already uses — but statically (no animation) and with a stroke width equal to the outline's authored weight (`6` in viewBox space), scaled to the frame. Replace the raw `SVGView` renderer (which honors the SVG's thinner authored glyph width) in `PathPebbleRow`. No engine, DB, web, or widget change.

**Tech Stack:** SwiftUI (iOS 17), Swift Testing, SVGView (fallback only), xcodegen.

**Task-size note:** Small/medium iOS-only change. Workspace-scoped lint + build + test (`--workspace=@pbbls/ios`). No Arkaik update (no screen/route/data-model/endpoint change). Full-repo build not needed.

**Reference spec:** `docs/superpowers/specs/2026-07-02-issue-509-ios-glyph-stroke-consistency-design.md`

---

## File Structure

- **Create** `apps/ios/Pebbles/Features/Path/Render/LayerShape.swift` — the shared viewBox→rect + layer-transform `Shape`, moved out of `PebbleAnimatedRenderView.swift` and made internal so both renderers use it.
- **Create** `apps/ios/Pebbles/Features/Path/Render/PebbleStroke.swift` — small namespace holding the outline stroke constant and the frame-proportional `lineWidth` helper.
- **Create** `apps/ios/Pebbles/Features/Path/Render/PebbleStaticRenderView.swift` — static (non-animated) layer-tracing renderer with SVGView fallback.
- **Create** `apps/ios/PebblesTests/PebbleStrokeTests.swift` — unit tests for the `lineWidth` helper and the parse-failure fallback trigger.
- **Modify** `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift` — delete the now-shared `private struct LayerShape` (behavior unchanged).
- **Modify** `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift` — swap `PebbleRenderView` for `PebbleStaticRenderView` in `thumbnail`.

---

## Task 1: Extract `LayerShape` into a shared file

Move the `LayerShape` struct out of `PebbleAnimatedRenderView.swift` (where it is `private`) into its own internal file so `PebbleStaticRenderView` can reuse it. Pure mechanical move — no behavior change.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/LayerShape.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift:153-179` (remove the `// MARK: - Layer shape` section + `private struct LayerShape`)

- [ ] **Step 1: Create the shared `LayerShape.swift`**

```swift
import SwiftUI

/// Draws one parsed `PebbleSVGModel.Layer` into a SwiftUI `Shape`.
///
/// Combines the layer's SVG-space transform with the viewBox→rect fit so the
/// resulting path draws at the right size and position inside the Shape's
/// drawing rect. Shared by `PebbleAnimatedRenderView` (animated trim) and
/// `PebbleStaticRenderView` (static, full trim).
struct LayerShape: Shape {
    let layer: PebbleSVGModel.Layer
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        // Composition order (CG row-vector math):
        //   p' = p * layer.transform * scale * translate
        // ⇒ apply layer.transform first, then fit-scale, then center-offset.
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let dx = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let dy = (rect.height - scaledHeight) / 2 - viewBox.minY * scale

        var transform = layer.transform
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: dx, y: dy))
        guard let transformed = layer.combinedPath.copy(using: &transform) else {
            return Path(layer.combinedPath)
        }
        return Path(transformed)
    }
}
```

- [ ] **Step 2: Remove the old `LayerShape` from `PebbleAnimatedRenderView.swift`**

Delete this block (lines ~153-179), including the `// MARK: - Layer shape` comment:

```swift
// MARK: - Layer shape

private struct LayerShape: Shape {
    let layer: PebbleSVGModel.Layer
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        // ... entire body ...
    }
}
```

Leave everything else in the file (the `PebbleAnimatedRenderView` struct and the two `#Preview` blocks) untouched.

- [ ] **Step 3: Regenerate the Xcode project and build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `xcodegen` picks up the new file; build succeeds (`** BUILD SUCCEEDED **`). The animated view still references `LayerShape` — now resolved from the shared file.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/LayerShape.swift \
        apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift
git commit -m "refactor(ios): extract shared LayerShape from animated render view"
```

---

## Task 2: Add the `PebbleStroke` line-width helper (TDD)

A pure helper that converts the outline's authored viewBox stroke width (`6`) into an on-screen `lineWidth` proportional to the rendered frame — `6 × min(frame/viewBox)`. This is the value both the glyph and outline get stroked at, making them equal weight.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleStroke.swift`
- Test: `apps/ios/PebblesTests/PebbleStrokeTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/PebbleStrokeTests.swift`:

```swift
import Testing
import CoreGraphics
@testable import Pebbles

@Suite
struct PebbleStrokeTests {

    @Test func outlineWidthIsSix() {
        #expect(PebbleStroke.outlineWidth == 6)
    }

    @Test func lineWidthEqualsOutlineWidthAtOneToOne() {
        let viewBox = CGRect(x: 0, y: 0, width: 260, height: 260)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 260, height: 260))
        #expect(width == 6)
    }

    @Test func lineWidthScalesDownWithFrame() {
        let viewBox = CGRect(x: 0, y: 0, width: 260, height: 260)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 52, height: 52))
        // scale = 52/260 = 0.2 → 6 * 0.2 = 1.2
        #expect(abs(width - 1.2) < 0.0001)
    }

    @Test func lineWidthUsesMinAxisForNonSquareViewBox() {
        // Small pebble canvas is 250×200; fit uses the smaller per-axis scale.
        let viewBox = CGRect(x: 0, y: 0, width: 250, height: 200)
        let width = PebbleStroke.lineWidth(viewBox: viewBox, frame: CGSize(width: 125, height: 125))
        // scaleX = 125/250 = 0.5, scaleY = 125/200 = 0.625 → min = 0.5 → 6 * 0.5 = 3
        #expect(abs(width - 3) < 0.0001)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: compilation failure — `PebbleStroke` is not defined.

- [ ] **Step 3: Write the minimal implementation**

Create `apps/ios/Pebbles/Features/Path/Render/PebbleStroke.swift`:

```swift
import CoreGraphics

/// Stroke geometry for the pebble layer-tracing renderers.
///
/// The composed pebble SVG authors the outline (`layer:shape`) at
/// `stroke-width="6"` in viewBox units, but the glyph (`layer:glyph`) is
/// authored thinner (`6 × zoneScale`). Tracing every layer at the outline's
/// weight makes the glyph read the same weight as the outline — the fix for
/// issue #509.
enum PebbleStroke {

    /// The outline's authored stroke width in viewBox units. Mirrors the
    /// engine's shape/glyph stored stroke (`GLYPH_STROKE_WIDTH`, and the `6`
    /// on every shape path in the engine's shape templates).
    static let outlineWidth: CGFloat = 6

    /// On-screen `lineWidth` for a layer traced into `frame`, proportional to
    /// how the viewBox fits the frame (uniform min-axis scale, matching
    /// `LayerShape`'s fit). Keeps the stroke's absolute weight consistent with
    /// the outline at any thumbnail size.
    static func lineWidth(viewBox: CGRect, frame: CGSize) -> CGFloat {
        guard viewBox.width > 0, viewBox.height > 0 else { return outlineWidth }
        let scale = min(frame.width / viewBox.width, frame.height / viewBox.height)
        return outlineWidth * scale
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=@pbbls/ios`
Expected: `PebbleStrokeTests` all pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleStroke.swift \
        apps/ios/PebblesTests/PebbleStrokeTests.swift
git commit -m "feat(ios): add PebbleStroke line-width helper for layer tracing"
```

---

## Task 3: Create `PebbleStaticRenderView`

The static renderer: parse the SVG into a `PebbleSVGModel` once, trace every layer at `PebbleStroke.lineWidth(...)` (glyph == outline weight), fall back to `PebbleRenderView` (SVGView) if parsing fails.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Render/PebbleStaticRenderView.swift`
- Test: `apps/ios/PebblesTests/PebbleStrokeTests.swift` (add the fallback-trigger test to the existing suite)

- [ ] **Step 1: Write the failing test for the fallback trigger**

Add to `apps/ios/PebblesTests/PebbleStrokeTests.swift` (inside the `PebbleStrokeTests` suite). This documents the contract that a malformed SVG yields a nil model, which is what drives `PebbleStaticRenderView`'s SVGView fallback:

```swift
    @Test func modelIsNilForMalformedSvgSoStaticViewFallsBack() {
        // No viewBox / no layers → PebbleSVGModel returns nil, and
        // PebbleStaticRenderView renders the PebbleRenderView fallback.
        #expect(PebbleSVGModel(svg: "not svg at all") == nil)
    }

    @Test func modelParsesAWellFormedPebbleSvg() {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260">
          <g id="layer:shape"><path d="M 20 20 L 240 240" fill="none"/></g>
        </svg>
        """
        let model = PebbleSVGModel(svg: svg)
        #expect(model != nil)
        #expect(model?.viewBox == CGRect(x: 0, y: 0, width: 260, height: 260))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=@pbbls/ios`
Expected: `modelIsNilForMalformedSvgSoStaticViewFallsBack` and `modelParsesAWellFormedPebbleSvg` compile (they only use existing `PebbleSVGModel`) and PASS immediately — this step confirms the `PebbleSVGModel` contract the new view relies on. If they fail, the parsing assumption is wrong; stop and re-check `PebbleSVGModel` before building the view.

> Note: these two tests exercise an existing type, so they pass without new production code. They are guardrails for the view built in Step 3, not red-then-green TDD. The view itself (a SwiftUI body) is verified via previews in Task 5.

- [ ] **Step 3: Create `PebbleStaticRenderView.swift`**

```swift
import SwiftUI
import os

/// Static (non-animated) counterpart to `PebbleAnimatedRenderView`, used by
/// `PathPebbleRow`.
///
/// Traces each parsed `PebbleSVGModel` layer at a uniform stroke width equal to
/// the outline's authored weight (`PebbleStroke.lineWidth`), so the glyph reads
/// the same weight as the outline — matching the settled detail view and fixing
/// issue #509. Falls back to `PebbleRenderView` (SVGView) when the SVG cannot be
/// parsed into a model.
struct PebbleStaticRenderView: View {
    let svg: String
    /// Stroke for the traced layer paths.
    let strokeColor: Color
    /// Hex equivalent injected into the raw SVG for the SVGView fallback.
    let strokeColorHex: String

    @State private var model: PebbleSVGModel?
    @State private var parseAttempted = false

    var body: some View {
        Group {
            if let model {
                GeometryReader { proxy in
                    let lineWidth = PebbleStroke.lineWidth(viewBox: model.viewBox, frame: proxy.size)
                    ZStack {
                        ForEach(Array(model.layers.enumerated()), id: \.offset) { _, layer in
                            LayerShape(layer: layer, viewBox: model.viewBox)
                                .stroke(
                                    strokeColor,
                                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round)
                                )
                                .opacity(layer.opacity)
                        }
                    }
                }
            } else {
                PebbleRenderView(svg: svg, strokeColor: strokeColorHex)
            }
        }
        .accessibilityHidden(true)
        .onAppear {
            guard !parseAttempted else { return }
            parseAttempted = true
            model = PebbleSVGModel(svg: svg)
            if model == nil {
                Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                    .info("PebbleStaticRenderView: parse failed; using SVGView fallback")
            }
        }
    }
}

#Preview("Static · glyph matches outline weight") {
    PebbleStaticRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
          <g id="layer:shape">
            <path d="M 20 130 C 20 70 70 20 130 20 C 190 20 240 70 240 130 C 240 190 190 240 130 240 C 70 240 20 190 20 130 Z" fill="none"/>
          </g>
          <g id="layer:glyph" transform="translate(78, 78) scale(0.52)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none"/>
          </g>
        </svg>
        """,
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA"
    )
    .frame(width: 96, height: 96)
    .padding()
}
```

- [ ] **Step 4: Build and run tests**

Run: `npm run test --workspace=@pbbls/ios`
Expected: build succeeds, all `PebbleStrokeTests` pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleStaticRenderView.swift \
        apps/ios/PebblesTests/PebbleStrokeTests.swift
git commit -m "feat(ios): add PebbleStaticRenderView tracing layers at outline weight"
```

---

## Task 4: Use `PebbleStaticRenderView` in `PathPebbleRow`

Swap the raw `SVGView`-based `PebbleRenderView` for the new tracer in the Path thumbnail. The backdrop, `scaleEffect`, aspect ratio, and frame stay exactly as they are.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift:84-87`

- [ ] **Step 1: Replace the renderer in `thumbnail`**

Find this block (inside the `ZStack` in `private var thumbnail`):

```swift
            if let svg = pebble.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: frameColors?.strokeHex ?? Color.accent.primaryHex)
                    .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
            }
```

Replace it with:

```swift
            if let svg = pebble.renderSvg {
                let strokeHex = frameColors?.strokeHex ?? Color.accent.primaryHex
                PebbleStaticRenderView(
                    svg: svg,
                    strokeColor: Color(hex: strokeHex) ?? Color.accent.primary,
                    strokeColorHex: strokeHex
                )
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
            }
```

- [ ] **Step 2: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: `** BUILD SUCCEEDED **`. (`Color(hex:)` returns an optional — the `?? Color.accent.primary` fallback mirrors `PebbleReadBanner.swift:163`.)

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift
git commit -m "fix(ios): render Path glyph at outline stroke weight (#509)"
```

---

## Task 5: Verify end-to-end

- [ ] **Step 1: Lint the workspace**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no new SwiftLint violations in the added/modified files.

- [ ] **Step 2: Full workspace test + build**

Run: `npm run test --workspace=@pbbls/ios`
Expected: build succeeds; `PebbleStrokeTests` and all existing suites (incl. `PebbleOutlineGeometryTests`, `PebbleOutlineBackdropViewTests`) pass.

- [ ] **Step 3: Visual confirmation in Xcode previews**

Open `PebbleStaticRenderView.swift` and `PathPebbleRow.swift` previews in Xcode. Confirm, against issue #509's screenshots:
- The glyph stroke and the pebble outline read as the **same weight**.
- The outline itself looks **unchanged** from the current Path (only the glyph thickened).
- Verify at intensity 1 (small), 2 (medium), and 3 (large) — e.g. by tweaking the `PathPebbleRow` preview's `intensity` and supplying a `renderSvg` with a glyph layer, or adding size variants to the `PebbleStaticRenderView` preview.
- Confirm the malformed-SVG path still renders (SVGView fallback) rather than blanking.

- [ ] **Step 4: Confirm nothing else regressed**

Scroll the Path in the running app (or preview `PathView`) and confirm rows with and without photos, and the pebble detail sheet, all still render correctly (the detail sheet is untouched and should look identical to before).

---

## Notes for the implementer

- **Do not touch** `PebbleAnimatedRenderView`'s stroke logic (it keeps its tuned `lineWidth: 2`). The only change to that file is deleting the moved `LayerShape` struct.
- **Do not touch** the engine, migrations, web, or widget. This is an iOS render-only fix; `render_svg` is untouched, so no backfill.
- If the `GeometryReader` in `PebbleStaticRenderView` causes any layout drift versus the old `SVGView` placement (e.g. the artwork no longer centers within the backdrop), verify the outer `thumbnail` ZStack's `.aspectRatio(PebbleOutlineGeometry.aspectRatio(for:))` and `.frame(width:height:)` are still applied — those, plus the `.scaleEffect(pebbleScale)`, own the sizing; `LayerShape` centers the pebble within whatever rect it receives.
```
