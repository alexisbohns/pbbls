# iOS valence fan picker — design

**Date:** 2026-08-24
**Issue:** #727
**Surface:** iOS only (`apps/ios`)
**Size:** medium — one feature, ~4 new files, no schema or contract change

## Problem

Step 3 of the record flow asks "How did it land?" and answers it with nine
tiles. Each tile carries a flat template icon from `Assets.xcassets/Valence/`,
a polarity label, and a rounded rectangle background; the nine are grouped
under three headers ("Day event" / "Week event" / "Month event") with a
sentence of description each. The screen is busy and formal, and — the part
that matters — the thing you are choosing looks nothing like the thing you
get. Every other pebble surface (Path rows, `PebbleRow`, the read sheet, the
success step) draws a real wobbled stone silhouette. The picker draws an icon.

The same `ValencePickerContent` also backs `ValencePickerSheet`, reached from
`PebbleFormView`'s valence row, so both surfaces change together.

## What we are building

A fan of nine real stones. No tiles, no headers, no icon assets.

```
                    ◯  neutral-large
        ◯                          ◯      -34° / +34°
   lowlight-large            highlight-large

        ◯          ◯          ◯           medium ring
             ○     ○     ○                small ring
                    ▲
              origin (bottom-centre)

              A small highlight.
```

Polarity picks the angle, size picks the radius and the stone's height. Small
stones sit nearest the origin and are smallest; large stones sit farthest and
are biggest, so the gaps open up as the fan rises. Directional, but not
aggressively so.

## Structure

Three new files under `apps/ios/Pebbles/Features/Path/Valence/`, plus a
rewrite of `ValencePickerContent.swift`, which stays where it is next to
`ValencePickerSheet.swift` so the diff is a rewrite and not a move.

| File | Responsibility |
|---|---|
| `ValenceFanLayout.swift` | Pure geometry: `stoneHeight(for:)`, `offset(for:in:)`, `canvasHeight`. No SwiftUI beyond `CGSize` / `CGPoint`, so it is unit-testable. |
| `ValenceStoneStyle.swift` | Polarity → `(fill, stroke)` shape styles, including the `MeshGradient` availability branch. |
| `ValenceStoneView.swift` | One stone: wobbled silhouette, filled and stroked, at a given height. Knows nothing about selection or layout. |
| `ValencePickerContent.swift` | The fan: nine `ValenceStoneView`s in a `ZStack`, dimming, caption, accessibility, tap handling. |

### Why not extend `PebbleOutlineBackdropView`

That view is fill-only by construction, and its non-wobble branch renders
through `SVGView` from a colour-substituted string — there is no `CGPath` to
stroke. Adding an optional stroke parameter would give it a param only the
picker passes and a branch that silently ignores it.

`ValenceStoneView` instead calls `WobbleRenderer.backdropArt(size:polarity:)`
directly, which returns a real `CGPath` in viewBox space that
`WobbledBackdropShape` can both `.fill()` and `.stroke()`. That art is already
`NSCache`-memoized behind a lock, so nine stones cost nine parses once for the
process, never per frame.

Fallback: if `backdropArt` returns nil (missing or unparseable asset — a setup
bug, already logged by `WobbleRenderer`), the stone renders as nothing rather
than crashing, matching `PebbleOutlineBackdropView`'s existing posture. The
`ValenceStoneStyleTests` do not cover this path; the empty cell makes the bug
visible the way the existing outline log line does.

`Assets.xcassets/Valence/` and `Valence.assetName` become unused by the picker.
They are left in place — removing them is not part of this change.

## Fan geometry

Origin is the bottom-centre of the canvas. A stone's centre is
`origin + (sin θ, −cos θ) · radius`.

| | angle from vertical | small | medium | large |
|---|---|---|---|---|
| radius (pt) | — | 78 | 150 | 232 |
| stone height (pt) | — | 48 | 78 | 110 |
| lowlight | −34° | | | |
| neutral | 0° | | | |
| highlight | +34° | | | |

Stone *width* follows from height via
`PebbleOutlineGeometry.aspectRatio(for:)`, so each stone keeps its real
silhouette proportions (small is wider than tall, large is taller than wide).

Canvas height is fixed at 300pt — not aspect-derived — so the record step and
the sheet get identical geometry inside their respective `ScrollView`s.

These constants are eye-tuned. Two invariants are asserted rather than left to
the eye (see Testing):

1. Every stone's bounding box stays inside the canvas with ≥8pt margin at
   320pt width, the narrowest supported.
2. Radius is strictly increasing in size, and centre-x is strictly ordered
   lowlight < neutral < highlight within every size ring.

If tuning breaks invariant 1, the angles come in before the radii shrink — the
vertical rhythm is what carries the fan.

## Colour

| Polarity | Fill | Stroke |
|---|---|---|
| lowlight | `SystemMuted`, soft | `SystemSecondary` |
| neutral | `AccentSurface` (already carries a low alpha) | `AccentPrimary` |
| highlight | `MeshGradient` at ~0.25 opacity | the same `MeshGradient` at full strength |

The highlight mesh is a 4×4 grid running purple → indigo → pink → orange →
yellow, with the interior control points pulled off-grid so the colour
wanders rather than banding. The stone is the only place in the app that uses
it; there is no rainbow token anywhere in the repo today, and this change does
not create one — the mesh lives in `ValenceStoneStyle` until a second surface
needs it.

Fill and stroke are the same gradient at two intensities: the line reads as the
vivid edge of the wash.

Stroke width is ~2.5pt at the large stone and scales with stone height, so a
small stone does not read as heavier-lined than a large one.

### iOS 17

`MeshGradient` is iOS 18+. The deployment target stays 17.0, and
`ValenceStoneStyle` carries the app's first `#available(iOS 18, *)`:

```swift
if #available(iOS 18, *) {
    MeshGradient(width: 4, height: 4, points: …, colors: …)
} else {
    LinearGradient(colors: [.purple, .pink, .orange, .yellow],
                   startPoint: .topLeading, endPoint: .bottomTrailing)
}
```

Both branches are used for fill *and* stroke, so an iOS 17 device gets a
coherent stone rather than a mesh outline around a linear wash.

## Selection, caption, accessibility

**Selection.** The selected stone keeps full fill and stroke and scales to
1.08×; the other eight drop to 0.35 opacity. Under Reduce Motion the opacity
change still animates and the scale does not. With nothing selected all nine
render at full strength.

The two hosts keep their existing commit semantics (D5): the record step
commits on tap and advances, the sheet writes back and dismisses. Neither
gains a confirm button.

**Caption.** One line under the fan, `.subhead` / `SystemSecondary`:

- nothing selected → "Pick the one that fits."
- selected → a new `Valence.caption`, e.g. "A small highlight." /
  "Un petit temps fort."

Ten new EN+FR entries in `Localizable.xcstrings` (nine captions plus the
empty-state line). Per the formatting-sensitive-catalogue rule they are
inserted as text at their alphabetical anchors, never by re-dumping the JSON.

**Accessibility.** `ValenceSizeGroup.name` and `Valence.shortLabel` combine
into the VoiceOver label exactly as they do today ("Day event, Highlight"), so
the day/week/month meaning survives for screen readers even though the wording
leaves the screen. `.isSelected` trait as today. Hit targets are padded to a
minimum 44×44 regardless of stone size, via `.contentShape` on the padded
bounding rect — the small stones are 48pt tall but under 44pt wide.

`ValenceSizeGroup.description` stops being rendered anywhere. It stays on the
model (its copy is asserted by `ValenceMetadataTests`); this change does not
delete it.

## Wobble in Release

`WobbleFlags.isEnabled` is `#if DEBUG` today, so the wobble experiment (#555)
compiles to `false` in Release and every stone ships smooth. The picker's whole
point is that it renders real stones, so the flag is promoted to `true`
unconditionally as part of this work.

This affects every stone surface on iOS, not just the picker: Path rows,
`PebbleRow`, the read sheet, the success step, and the outline backdrops. It is
consistent with Android, which already bakes the wobble into internal-testing
releases (decision log, 2026-07-14).

It lands as **its own commit** so it can be reverted without touching the
picker, and gets a `docs/decisions/log.md` entry recording that #555 is now on
in Release on iOS.

## Testing

Swift Testing (never XCTest), in `PebblesTests/`:

- `ValenceFanLayoutTests` — the two geometry invariants above, at 320 / 390 /
  430pt canvas widths; `stoneHeight` strictly increasing; `offset` deterministic
  (same input, same output).
- `ValenceMetadataTests` (extended) — `caption` copy for all nine cases, the
  way `shortLabel` and `name` are already covered.

Visual verification follows the established iOS harness: a temporary
`ImageRenderer` test writes the picker to PNGs on the host filesystem for both
colour schemes and for iOS 17 / 18 gradient branches, checked by eye, then
deleted. No `ScrollView` or `containerRelativeFrame` in the harness.

Gate: `npm run lint --workspace=@pbbls/ios` and
`npm run test --workspace=@pbbls/ios`, with `rm -rf DerivedData/Pebbles-*/Build`
first if a build has already run this session.

## Out of scope

- Android and web parity. Both track the record flow separately under #725;
  this is an iOS-only visual change with no contract impact.
- Deleting `Assets.xcassets/Valence/` or `Valence.assetName`.
- Turning the mesh into a design-system token.
- Any change to `Valence`'s `positiveness` / `intensity` mapping, the draft
  payload, or the publish path.
