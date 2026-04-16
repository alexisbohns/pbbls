# Color Pebble Render Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Color the pebble's SVG strokes with the emotion's hex color on iOS.

**Architecture:** Client-side string replacement — swap `currentColor` with the emotion hex in the SVG string before passing to SVGView. Mirrors the web's `recolor()` pattern. Server SVGs stay monochrome.

**Tech Stack:** SwiftUI, exyte/SVGView

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift` | Modify | Accept optional `strokeColor`, apply to SVG |
| `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` | Modify | Pass emotion color to render view |
| `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` | Modify | Accept and forward `strokeColor` |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | Modify | Pass emotion color to form view |

---

### Task 1: Add strokeColor to PebbleRenderView

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleRenderView.swift:1-26`

- [ ] **Step 1: Add strokeColor parameter and coloredSvg computed property**

Replace the full struct body with:

```swift
struct PebbleRenderView: View {
    let svg: String
    var strokeColor: String? = nil

    private var coloredSvg: String {
        guard let color = strokeColor else { return svg }
        return svg.replacingOccurrences(of: "currentColor", with: color)
    }

    var body: some View {
        SVGView(string: coloredSvg)
            .aspectRatio(contentMode: .fit)
            .accessibilityHidden(true)
    }
}
```

The default `nil` keeps the preview and any future call sites without an emotion working unchanged.

- [ ] **Step 2: Update preview to exercise coloring**

Replace the `#Preview` block with:

```swift
#Preview {
    PebbleRenderView(
        svg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="2"/>
            </svg>
            """,
        strokeColor: "#EF4444"
    )
    .frame(width: 260, height: 260)
}
```

- [ ] **Step 3: Build to verify**

Run: `npm run build --workspace=@pbbls/ios` (or `xcodebuild build` for the Pebbles scheme)
Expected: builds with no errors.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleRenderView.swift
git commit -m "feat(ios): add strokeColor param to PebbleRenderView"
```

---

### Task 2: Pass emotion color in PebbleDetailSheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift:52-54`

- [ ] **Step 1: Pass strokeColor to PebbleRenderView**

Replace line 53:

```swift
                        PebbleRenderView(svg: svg)
```

with:

```swift
                        PebbleRenderView(svg: svg, strokeColor: detail.emotion.color)
```

`detail.emotion` is an `EmotionRef` with a `color: String` field (hex like `"#EF4444"`), already loaded by the query on line 89.

- [ ] **Step 2: Build to verify**

Run: `npm run build --workspace=@pbbls/ios`
Expected: builds with no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift
git commit -m "feat(ios): color pebble render in detail sheet"
```

---

### Task 3: Add strokeColor to PebbleFormView and wire EditPebbleSheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift:9-28`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift:70-79`

- [ ] **Step 1: Add strokeColor parameter to PebbleFormView**

In `PebbleFormView.swift`, after line 16 (`var renderSvg: String? = nil`), add:

```swift
    var strokeColor: String? = nil
```

- [ ] **Step 2: Pass strokeColor to PebbleRenderView inside PebbleFormView**

Replace line 21:

```swift
                PebbleRenderView(svg: svg)
```

with:

```swift
                PebbleRenderView(svg: svg, strokeColor: strokeColor)
```

- [ ] **Step 3: Pass strokeColor from EditPebbleSheet**

In `EditPebbleSheet.swift`, replace the `PebbleFormView` call (lines 71-79):

```swift
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                renderSvg: renderSvg
            )
```

with:

```swift
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                renderSvg: renderSvg,
                strokeColor: strokeColor
            )
```

`EditPebbleSheet` needs a `strokeColor` state property. After line 26 (`@State private var renderSvg: String?`), add:

```swift
    @State private var strokeColor: String?
```

And in the `load()` function, after line 137 (`self.renderSvg = detail.renderSvg`), add:

```swift
            self.strokeColor = detail.emotion.color
```

- [ ] **Step 4: Build to verify**

Run: `npm run build --workspace=@pbbls/ios`
Expected: builds with no errors.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "feat(ios): color pebble render in edit sheet"
```

---

### Task 4: Final verification and lint

- [ ] **Step 1: Run full build and lint**

```bash
npm run build --workspace=@pbbls/ios
npm run lint
```

Expected: both pass with no errors.

- [ ] **Step 2: Verify no regressions**

Confirm `CreatePebbleSheet` still compiles without changes — it doesn't pass `renderSvg` or `strokeColor` to `PebbleFormView`, and both default to `nil`, so the existing call site is unaffected.

- [ ] **Step 3: Commit (if lint required changes)**

Only if lint/build surfaced fixable issues:

```bash
git add -A
git commit -m "quality(ios): lint fixes for pebble color render"
```
