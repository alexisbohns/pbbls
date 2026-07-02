# iOS glyph stroke consistency (Path vs Detail) — issue #509

## Problem

On iOS, the same composed pebble renders with a **thinner glyph stroke on the
Path** than on the Pebble detail page. On the Path the glyph reads lighter than
the pebble outline; on the animated detail page the glyph and outline strokes
read as the same weight. The expectation is for the Path to look as consistent
as the detail page.

## Root cause

The two screens use two different renderers for the same `render_svg`:

| Screen | Renderer | Stroke behavior |
|---|---|---|
| Path (`PathPebbleRow`) | `PebbleRenderView` → `SVGView` | Honors each layer's **authored** stroke-width |
| Detail (`PebbleAnimatedRenderView`, animated) | `LayerShape` + `.stroke(lineWidth: 2)` | Overrides **all** layers to one uniform width |

In the composed SVG the outline (`layer:shape`) is authored at `stroke-width="6"`,
but the glyph (`layer:glyph`) is authored thinner: the engine emits
`stroke-width = 6 * scale` where `scale` fits the glyph's square into the
140/150/160 glyph zone (`packages/supabase/supabase/functions/_shared/engine/glyph.ts:255`).
So the glyph's effective stroke is ~4.2–4.8 against the outline's 6.

- **Path** (`SVGView`) faithfully renders that difference → glyph looks thinner.
- **Detail (animated)** strokes every layer at a flat `lineWidth: 2`, which
  discards the authored widths and happens to equalize glyph and outline → looks
  "perfect."

The animated view is not "more correct"; it simply ignores authored widths. The
decision for this issue is that the glyph **should** read as the same weight as
the outline everywhere, matching the detail page.

`render_svg` is persisted per-pebble in the DB (composed once at create/edit,
with a `backfill-pebble-render` function available). This is why an engine-side
change would require recomposition — and why this fix stays on the render side.

## Decisions

- **Target weight:** the glyph is drawn at the **outline's authored width (6 in
  viewBox space)**. The outline keeps its current weight; only the glyph thickens
  to match. Proportional at every size — no magic constant.
- **Fix locus:** **iOS render only.** No engine / DB / web / widget change, no
  backfill. Scope matches the issue's `ios` label.

## Design

**Core idea.** The Path row stops rendering the pebble artwork through raw
`SVGView` and instead renders it through the same layer-tracing path the Detail
view already uses — statically (no animation) and with a stroke width equal to
the outline's.

### 1. Shared `LayerShape` (small extraction)

`LayerShape` is currently `private` inside
`apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`. Move it to
its own file (`apps/ios/Pebbles/Features/Path/Render/LayerShape.swift`) with
internal visibility so both the animated view and the new static view share one
implementation of the viewBox→rect + layer-transform math. This is the only
refactor; it is the code being worked in, and it avoids duplicating the
transform composition. `PebbleAnimatedRenderView` behaves exactly as before.

### 2. New `PebbleStaticRenderView`

New file `apps/ios/Pebbles/Features/Path/Render/PebbleStaticRenderView.swift`. It:

- Parses the SVG into `PebbleSVGModel` once on appearance (same as the animated
  view).
- Renders every parsed layer via `LayerShape` at **full trim** (no `.trim`),
  stroked with
  `StrokeStyle(lineWidth: outlineWidth * fitScale, lineCap: .round, lineJoin: .round)`,
  honoring each layer's `opacity`.
- `outlineWidth = 6`, a named constant mirroring the engine's authored
  shape/glyph stroke width. `fitScale = min(size.width / viewBox.width,
  size.height / viewBox.height)`, computed from a `GeometryReader` so the
  on-screen weight is proportional at any thumbnail size.
- **Falls back to `PebbleRenderView` (SVGView)** on parse failure — the same
  safety net the animated view uses.

Inputs: `svg: String`, `strokeColor: Color` (for the tracer),
`strokeColorHex: String` (for the fallback SVGView). No `ValenceSizeGroup` is
needed: `fitScale` derives from the `GeometryReader`'s proposed frame size and
the parsed model's `viewBox`. The backdrop is **not** this view's concern — the
row keeps composing `PebbleOutlineBackdropView` behind it.

### 3. `PathPebbleRow` swap

In `PathPebbleRow.thumbnail`, replace

```swift
PebbleRenderView(svg: svg, strokeColor: frameColors?.strokeHex ?? Color.accent.primaryHex)
    .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.valence.sizeGroup))
```

with `PebbleStaticRenderView(...)`, passing the stroke as a `Color` (converted
from `frameColors?.strokeHex`) plus the hex string for the fallback path.
Backdrop, `scaleEffect`, aspect ratio, and frame all unchanged.

### Why this lands the fix

- The outline (`layer:shape`) is authored at 6, so tracing it at
  `6 * fitScale` reproduces **exactly today's Path outline** — no perceptual
  change there.
- The glyph, previously thin (`6 * zoneScale`), is now also traced at
  `6 * fitScale` → **glyph weight == outline weight**, matching the Detail view's
  look.
- Proportional width means the thumbnail reads as a clean scaled-down pebble, not
  a bold one.

### Deliberately unchanged

- **The Detail animated view** keeps its tuned `lineWidth: 2`. It looks correct
  and stays internally consistent on its own terms; retuning it is out of scope.
- **Engine, DB, web, widget** — untouched. No backfill.

## Testing

- `PebbleStaticRenderView` parse-failure fallback: a malformed SVG renders via
  the `SVGView` path rather than blanking.
- Previews at intensities 1 / 2 / 3 confirming the glyph and outline read as
  equal weight, and that the outline is visually unchanged from the current Path.
- Existing `PebbleOutlineGeometryTests` remain green.

## Out of scope

- Any change to the composed-SVG stroke widths in the engine.
- Web / widget consistency (would be a separate engine-side follow-up if wanted).
- Retuning the animated detail view's stroke.
