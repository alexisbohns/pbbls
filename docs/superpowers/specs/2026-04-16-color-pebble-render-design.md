# Color pebble strokes with emotion color (iOS)

**Issue:** #264
**Date:** 2026-04-16

## Context

The pebble engine composes monochrome SVGs with `stroke="currentColor"` and `fill="none"`. This design keeps the server output theme-agnostic and animation-friendly — the client resolves color at render time.

On the web, `render.ts` already does this via string replacement (`recolor()`). On iOS, slice 1 shipped `PebbleRenderView` without applying any color — strokes render in the SVGView default (black).

## Decision

Apply the emotion's hex color to the SVG via client-side string replacement on iOS, mirroring the web pattern. The monochrome `currentColor` SVGs stay untouched server-side.

**Why string replacement over SwiftUI `foregroundStyle`:** The exyte/SVGView library parses SVG independently of SwiftUI's color inheritance. There's no documented guarantee that `foregroundStyle` propagates to `currentColor` inside the parsed SVG tree. String replacement is deterministic and proven on the web side.

**Why not server-side baking:** Baking the hex into the SVG would break light/dark mode adaptation and complicate the animation consumer (which will need per-layer color control via the manifest).

## Design

### PebbleRenderView

Add an optional `strokeColor: String?` parameter. When provided, replace all occurrences of `currentColor` with the hex value in the SVG string before passing it to `SVGView`.

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

Default is `nil` so existing call sites (previews, future contexts without an emotion) continue to work.

### PebbleDetailSheet

Pass the loaded emotion color to `PebbleRenderView`:

```swift
PebbleRenderView(svg: svg, strokeColor: detail.emotion.color)
```

The emotion color is already fetched in the detail query (`emotion:emotions(id, name, color)`).

### PebbleFormView + EditPebbleSheet

`PebbleFormView` already accepts an optional `renderSvg`. Add a sibling `strokeColor: String?` parameter and pass it through to `PebbleRenderView`.

`EditPebbleSheet` passes `detail.emotion.color` alongside `detail.renderSvg`.

### CreatePebbleSheet

No changes needed. `CreatePebbleSheet` doesn't render the pebble — it calls `onCreated(pebbleId)` and dismisses. `PebbleDetailSheet` handles the post-create render and loads the emotion color itself.

## Files changed

| File | Change |
|------|--------|
| `PebbleRenderView.swift` | Add `strokeColor` param, `coloredSvg` computed property |
| `PebbleDetailSheet.swift` | Pass `detail.emotion.color` to `PebbleRenderView` |
| `PebbleFormView.swift` | Add `strokeColor` param, pass to `PebbleRenderView` |
| `EditPebbleSheet.swift` | Pass `detail.emotion.color` to `PebbleFormView` |

## Acceptance criteria

- Opening a pebble with "angry" emotion from the path shows strokes in `#EF4444`.
- Recording a pebble with "surprise" emotion (mapped to amazed/excited category) shows strokes in `#F97316`.
- Edit sheet displays the pebble with the correct emotion color.
- Pebbles without a render SVG still show the text fallback (no regression).
