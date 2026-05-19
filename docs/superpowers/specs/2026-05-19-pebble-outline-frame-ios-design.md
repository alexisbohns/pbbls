# iOS pebble outline frame

Resolves https://github.com/Bohns/pbbls/issues/474 (parent: #473 cross-platform; this slice is iOS only)

## Problem

Pebble renders today sit on a square/rounded-square backdrop applied at the consumer site (list row, detail view). The squared chrome reads as "interface" — heavy, generic — and fights with the organic, hand-drawn feel of the pebble itself. The intent is to replace that chrome with a silhouette that fits the pebble's shape so the rendering reads as a sculpted artifact rather than a card.

The parent issue (#473) proposes embedding the silhouette inside the server-composed SVG as a new layer. That approach cascades into canvas expansion, layer translation, `render_version` bump, backfill migration, and a second CSS token plumbed through web + iOS. The blast radius is large and a `render_version` bump risks breaking earlier iOS builds that pin the prior schema.

A simpler model: treat the silhouette as a **wrapper-level frame**, drawn as a separate view layered behind the existing pebble render at each consumer site. No compose pipeline change. No `render_version` bump. No backfill. Faster to iterate; no breaking change for older clients.

This spec is **iOS only**. Web gets its own issue once iOS validates the visual.

## Goal

Every iOS site that renders a single pebble layers a per-(size × valence) silhouette behind it. The silhouette is **fill-only** (no stroke), colored from the pebble's emotion palette using an intensity-driven rule. The pebble render is unchanged in geometry — it sits centered inside a backdrop that is ~1.35× its viewBox, achieved by scaling the pebble down in the `ZStack` (see Sizing below).

Per-intensity contract:

| Intensity | Backdrop fill         | Pebble strokes        |
|-----------|-----------------------|-----------------------|
| 3 (large) | `palette.primary`     | `palette.light`       |
| 1 / 2     | `palette.surface`     | `palette.secondary`   |

Palette colors are emotion-palette reference data — theme-neutral, identical in light and dark mode. There is no system-theme involvement at this layer.

## Approach

### Architecture

A new SwiftUI view, `PebbleOutlineBackdropView`, owns the silhouette. It renders one of the 9 outline SVGs (3 sizes × 3 valences) with fill driven by the pebble's intensity + emotion palette. It exposes nothing else — no glyph, fossil, or shape.

Every consumer wraps the existing `PebbleRenderView` in a `ZStack` (full pattern with sizing in the Sizing subsection below):

```swift
let colors = Palettes.pebbleFrameColors(intensity: pebble.intensity, palette: palette)

ZStack {
    PebbleOutlineBackdropView(size: pebble.size, valence: pebble.valence, fillHex: colors.fillHex)
    PebbleRenderView(svg: pebble.renderSvg, strokeColor: colors.strokeHex)
        .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: pebble.size))
}
.aspectRatio(PebbleOutlineGeometry.aspectRatio(for: pebble.size), contentMode: .fit)
```

### Sizing

The outline's viewBox is intentionally larger than the pebble canvas per size (small 337×270 vs 250×200; medium 350×350 vs 260×260; large 335×400 vs 260×310 — roughly 1.35× per side, ~12–13% margin on each edge). The aspect ratios across (outline, pebble) within a size match within 0.1%, so both fit into the same on-screen rectangle without distortion.

The pebble does **not** automatically render at the smaller relative size — `SVGView` fits its viewBox to the proposed frame regardless of the viewBox's absolute dimensions. The `ZStack` therefore lets the backdrop fill the container and applies an explicit scale to the pebble:

```swift
ZStack {
    PebbleOutlineBackdropView(size: ..., valence: ..., fillHex: ...)  // fills container

    PebbleRenderView(svg: ..., strokeColor: ...)
        .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))     // ~0.74
}
.aspectRatio(PebbleOutlineGeometry.aspectRatio(for: size), contentMode: .fit)
```

`PebbleOutlineGeometry` is a small static helper (next to the backdrop view) that returns:
- `pebbleScale(for: PebbleSize)` — the linear scale factor, computed as `pebbleViewBoxWidth / outlineViewBoxWidth` per size (small ≈ 0.742; medium ≈ 0.743; large ≈ 0.776). Because the per-size scales are close, a single constant (~0.74) is acceptable if simpler reads better; spec leaves the per-size form to preserve the designer's intent.
- `aspectRatio(for: PebbleSize)` — the outline's aspect ratio, so the outer `ZStack` adopts the silhouette's bounds.

Both helpers derive from constants matching the outline viewBoxes; no runtime SVG parsing.

### Palette helper

A single source of truth for the intensity → role mapping, exposed alongside the existing palette plumbing (exact module — `Palettes.swift` or its current equivalent — to confirm during implementation):

```swift
struct PebbleFrameColors {
    let strokeHex: String   // applies to pebble render strokes only
    let fillHex: String     // applies to outline backdrop fill only
}

extension Palettes {
    static func pebbleFrameColors(intensity: Int, palette: Palette) -> PebbleFrameColors {
        switch intensity {
        case 3:
            return PebbleFrameColors(strokeHex: palette.light, fillHex: palette.primary)
        default: // 1, 2
            return PebbleFrameColors(strokeHex: palette.secondary, fillHex: palette.surface)
        }
    }
}
```

This is the only place the rule lives. `PebbleRenderView` consumes `strokeHex`; `PebbleOutlineBackdropView` consumes `fillHex`.

### Outline assets

9 SVG files ship as bundle resources at `apps/ios/Pebbles/Resources/Outlines/`:

```
small-neutral.svg     small-lowlight.svg     small-highlight.svg
medium-neutral.svg    medium-lowlight.svg    medium-highlight.svg
large-neutral.svg     large-lowlight.svg     large-highlight.svg
```

Each file is a single `<path>` with `fill="#FF00FF"` (sentinel) and no stroke. The assets are provided verbatim in issue #473's "Assets" section — copy them in unchanged.

The backdrop view loads the SVG string at construction time and does one `replacingOccurrences`: `fill="#FF00FF"` → `fill="<fillHex>"`. The result is handed to `SVGView(string:)` — same pattern as the existing `PebbleRenderView.coloredSvg`. Color injection is cheap enough to run per construction; memoize later if profiling shows it.

### Animation in `PebbleAnimatedRenderView`

Backdrop springs in first, then the pebble draws over it. Sketch:

```swift
ZStack {
    PebbleOutlineBackdropView(...)
        .scaleEffect(backdropIn ? 1 : 0.6)
        .opacity(backdropIn ? 1 : 0)

    PebbleRenderView(...)
        .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))   // layout scale (constant)
        .opacity(pebbleIn ? 1 : 0)                                   // or existing draw-on hook
}
.aspectRatio(PebbleOutlineGeometry.aspectRatio(for: size), contentMode: .fit)
.task {
    withAnimation(.spring(response: 0.42, dampingFraction: 0.7)) {
        backdropIn = true
    }
    try? await Task.sleep(for: .milliseconds(180))
    withAnimation(.easeOut(duration: 0.25)) {
        pebbleIn = true
    }
}
```

The spring response/damping and the delay are tuneable during implementation — pair them with whatever animation primitives `PebbleAnimatedRenderView` already uses for the pebble's draw-in. Values listed are starting points.

In static list rows (`PathPebbleRow`, `PebbleRow`), the backdrop renders without animation. It's just present.

## Specifications

### New files

- **`apps/ios/Pebbles/Features/Path/PebbleOutlineBackdropView.swift`** — new SwiftUI view. Takes `(size: PebbleSize, valence: PebbleValence, fillHex: String)`. Loads the matching SVG resource, swaps the sentinel fill, hands the string to `SVGView`. `.aspectRatio(contentMode: .fit)`. Accessibility-hidden (the parent pebble already carries the label).
- **`apps/ios/Pebbles/Features/Path/PebbleOutlineGeometry.swift`** — small static helper exposing `pebbleScale(for:)` and `aspectRatio(for:)`. Constants only; no runtime parsing.
- **`apps/ios/Pebbles/Resources/Outlines/{small,medium,large}-{neutral,lowlight,highlight}.svg`** — 9 asset files. Contents copied verbatim from issue #473.

### Modified files

- **Palette module (`Palettes.swift` or current equivalent — confirm during implementation)** — add the `PebbleFrameColors` struct and `pebbleFrameColors(intensity:palette:)` helper.
- **`apps/ios/Pebbles/Features/Path/PebbleRenderView.swift`** — no API change. Callers begin passing `strokeColor` from the new helper instead of whatever they pass today. Verify any caller-side defaults still hold.
- **`apps/ios/Pebbles/Features/Path/Components/PathPebbleRow.swift`** — wrap the existing `PebbleRenderView(...)` in a `ZStack` with `PebbleOutlineBackdropView(...)` underneath. Remove the squared/rounded chrome currently providing the backdrop (exact modifier to remove — to identify during implementation). Source colors from the new helper.
- **`apps/ios/Pebbles/Components/PebbleRow.swift`** — same change as `PathPebbleRow`. (`PebbleRow` → `PathPebbleRow` consolidation is out of scope for this issue; both rows get the frame independently.)
- **`apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`** — same `ZStack` plus the staged spring-in animation. Verify integration with whatever animation primitives the file already uses.

### Resource bundling

Confirm that `Pebbles/Resources/Outlines/` is picked up by the Xcode build phase that copies bundle resources. `project.yml` is the source of truth — add the folder to the resource list there if it isn't already covered by an existing glob, then `xcodegen generate`.

### Color injection contract

The outline SVG carries `fill="#FF00FF"` as a sentinel. The view replaces it with the resolved hex. Sentinel choice matters: it must be a literal that does not appear anywhere else in the SVG. `#FF00FF` (the magenta the assets ship with) is safe. If a future outline asset accidentally uses `#FF00FF` for any other element, the swap would corrupt it — unit test on the asset (see below) catches this.

## Out of scope

- **Web equivalent.** Separate issue. iOS validates the visual first; web mirrors the model once stable.
- **Server compose pipeline changes.** No `render_version` bump. No backfill migration. The compositor is untouched.
- **`PebbleRow` → `PathPebbleRow` consolidation.** Flagged as a separate refactor.
- **Calendar header stack icons.** Different rendering (multi-pebble stack), not a single-pebble surface.
- **`PebbleFormView` form preview.** Keeps its current treatment in this issue. Follow-up if cross-screen consistency is needed.
- **Theme adaptation.** Palette is theme-neutral by design; this work introduces no light/dark variants.
- **Cross-platform parity checks.** Web is not touched; no parity to verify yet.

## Open questions to settle during implementation

- Exact spring response/damping/delay for the `PebbleAnimatedRenderView` entry — tune with the existing pebble draw-in animation, not in isolation.
- Whether to memoize the `(size, valence, fillHex)` → injected-SVG-string computation. Default: don't, until profiling shows it matters.
- Confirm the palette module's exact name and location (referenced here as `Palettes.swift`).
- Confirm the existing squared chrome modifier in `PathPebbleRow` / `PebbleRow` to remove. Locate during implementation; not blocking the design.

## Acceptance criteria

- [ ] 9 outline SVG resources exist under `apps/ios/Pebbles/Resources/Outlines/`, contents matching issue #473's asset section verbatim.
- [ ] `PebbleOutlineBackdropView` renders the correct silhouette for each (size, valence) combination, fill resolves to the resolved hex, no stroke is drawn.
- [ ] `Palettes.pebbleFrameColors(intensity:palette:)` returns `(palette.light, palette.primary)` for intensity 3 and `(palette.secondary, palette.surface)` for intensity 1 and 2.
- [ ] `PathPebbleRow` and `PebbleRow` render with the new backdrop behind the pebble. The previous squared backdrop modifier is removed. Visual review: rows match the design screenshot in issue #473.
- [ ] `PebbleAnimatedRenderView` shows the backdrop springing in before the pebble draws on top. Animation feels intentional, not stuttered.
- [ ] No `render_version` bump. No migration file. No change under `packages/supabase/`.
- [ ] iOS build is green. Existing tests pass.
- [ ] Visual check across all 9 (size × valence) combinations against the design screenshot.
