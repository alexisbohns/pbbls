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

## Revisions (2026-08-24, after first review)

Three corrections from reviewing the built screen. They supersede the
corresponding paragraphs above.

**1. The stone is a backdrop plus artwork, not a stroked silhouette.** The
first cut filled *and* stroked the outline silhouette, so the wash and the line
landed on the same edge — which is not how the Path or the read sheet draws a
pebble. A real stone is a soft-filled silhouette *behind* the artwork, with the
artwork scaled down by `PebbleOutlineGeometry.pebbleScale` so the backdrop
frames it. `ValenceStoneView` now does the same: the wobbled silhouette is
filled and never stroked, and the vector `Valence/valence-*` asset — the
pebble's own outline plus its creature and fossil, the artwork the *previous*
picker showed — is tinted and drawn inside it. `ValenceStoneStyle`'s two roles
are renamed `backdrop` / `ink` to match, mirroring
`EmotionPalette.pebbleFrameColors`.

The mesh keeps both roles: soft wash behind, full strength on the ink.

**2. Valence commits in place; `Continue` advances.** The step no longer
auto-advances on tap. `RecordFlowModel.select(valence:)` writes the draft and
fires the selection haptic without moving, and `.valence` gains a
`.primary("Continue", enabled: model.isAnswered)` action in `RecordFlowView`'s
table. This is a deliberate exception to D3 ("tile steps commit on tap and
advance") for one step: the fan is a comparison, and a tap that leaves the
screen denies the user the look at what they chose next to the eight they did
not. The other tile steps are unchanged, as is `ValencePickerSheet`, which
still writes back and dismisses. Selection also reads harder now — dim 0.45,
scale 1.14, and a shadow that survives Reduce Motion.

**3. The canvas is fixed, not scaled to the proposed width.** Deriving a scale
needs a `GeometryReader`, whose ideal height is unspecified; inside the record
step's `ScrollView`, which proposes no height, the fan is at the mercy of that.
The reference canvas is instead authored at 341 × 324 — exactly the content
width of the narrowest supported device (375pt − 2 × `Spacing.lg`) — and
rendered at that size, gaining side margin on wider phones. All the constants
in the geometry table scale up by the same factor; the invariants and their
tests are unchanged.

Note for whoever verifies this visually: `ImageRenderer` captures a `ScrollView`'s
content as empty, so `RecordStepScaffold` cannot be rendered directly. Mimic it
without the ScrollView.

## Revision 2 (2026-08-24) — the caption becomes a lockup

The one-line caption ("A small highlight.") is replaced by a three-line
typographic lockup, from designs supplied after the first build. It supersedes
the **Caption** paragraph above.

```
        A BIG          ← large events only; uppercase, foreground
      Highlight        ← hand font, sized by size group, coloured by polarity
   OF MY MONTH         ← uppercase, secondary
```

- **Prefix** — `A BIG` for lowlight and highlight, `BIG` for neutral (the
  neutral word does not take the article in the designs). Nothing on medium or
  small: their size is carried by the word's own size.
- **Word** — Caveat at weight 700, 34 / 44 / 56pt for small / medium / large,
  lowercased on small. Highlight wears the same `MeshGradient` its stone does,
  neutral takes `AccentPrimary`, lowlight takes `SystemForeground` (darker than
  its stone's ink: at headline size a grey word reads as disabled).
- **Span** — `OF MY DAY` / `OF MY WEEK` / `OF MY MONTH`, reusing the existing
  `cardHeading` token.

`Valence.caption` and its nine strings are removed; `Valence.Headline` carries
the three parts separately, so each line is typeset independently and a
translator can move them independently. The picker reserves the tallest
lockup's height (116pt) so picking a stone does not shove the fan up the page.

Two mechanical notes for whoever touches this next:

- **Caveat is a variable font.** `UIFont(name: "Caveat-Bold")` does not resolve;
  the weight axis has to be set explicitly through `kCTFontVariationAttribute`.
- **`pebblesFont` owns `textCase`.** The token sets that environment value
  itself, so a `.textCase(.lowercase)` layered on at the call site is overridden
  by the token's `nil`. Lowercase the string instead.
- **Caveat Bold overhangs its advance width.** The terminal `t` flicks up and
  to the right past where the glyph officially ends, and SwiftUI clips `Text`
  to the advance — so "Moment" and "Lowlight" lose the end of their last letter
  to a hard vertical cut. `PebblesFont.inkOverhang` carries the horizontal
  padding that fixes it (roughly a quarter of the font size), applied by the
  caller so `pebblesFont` stays a pure type modifier. The same hazard applies
  to any future Caveat token: `.nameInputHand` (the pebble name field) has the
  same exposure and is left alone here.

## Revision 3 (2026-08-24) — the lockup becomes a two-axis roll

The lockup is now the picker's second input: swipe left and right to change
polarity, up and down to change size. The fan stays tappable; the two inputs
drive the same value and animate the same way.

**Feel.** The roll is 1:1 with the finger (the content travels exactly as far
as the hand) and detents at the half step, so the answer changes under the
thumb rather than on release. Each detent springs the new value to centre and
plays `TapHaptics.selection` — that pairing is what reads as magnetic rather
than as a slider. The ends clamp with rubber-band resistance instead of
wrapping, so a hard swipe cannot loop the user past the end and back to where
they started.

**Direction.** Content follows the finger, so the index moves *against* the
travel: dragging left brings the value on the right to centre. On the size
axis the ladder runs large at the top to small at the bottom
(`ValenceSizeGroup.ladder`, deliberately not `allCases`), so dragging up rolls
toward smaller events — scrolling down a list whose big end is at the top.

**Affordances are the state.** Faded neighbour words sit one step out on each
side and bleed off the screen edges. Below the span, a pyramid of three marks —
widest at the top, a dot at the bottom — lights the current size.

**Nothing moves that is not changing.** The block is anchored to its bottom
edge and the pyramid is always three marks tall, so rolling between sizes never
shifts the layout: the span and the pyramid hold their exact position, the word
swaps size in place, and the `BIG` overtitle grows upward into space the
reservation already accounts for. On the polarity axis only the word row
travels — the span reads the same for all three polarities, so sliding it would
be motion that says nothing. The size axis does not translate at all; the
detent, the spring resize and the pyramid are the feedback.

(The first cut counted *remaining* sizes with marks above and below the lockup,
which changed the block's height on every size step and shoved the whole thing
up and down the page. `Valence.sizesAbove` / `sizesBelow` existed for that and
are gone with it.)

**The step arrives answered.** `RecordFlowModel.seedValenceIfNeeded()` parks
the draft on neutral-medium when the step appears, so the roll has something
under the finger and its affordances are readable on arrival. It seeds without
a haptic — nothing happened that the user did — and never overwrites an
existing answer, which is what makes it safe on the resume path. Continue is
therefore enabled on arrival.

**The edit sheet had to change with it.** `ValencePickerSheet` used to commit
and dismiss on pick; the roll changes the value at every detent, so that sheet
would have closed on the first swipe. It now stages locally and commits on a
new **Done** button.

**Constants** (all eye-tuned, all in `ValenceRollView`): 220pt of travel per
polarity step — also the distance the neighbour words sit out at, because the
two have to agree for the roll to feel 1:1 — 90pt per size step, half-step
detents, 34pt of overscroll.

Two mechanics worth keeping:

- **The axis is locked on the first movement of each drag.** Without the lock a
  diagonal swipe alternates axes frame to frame and the roll shakes.
- **The drag is a `highPriorityGesture`.** The step's `ScrollView` otherwise
  claims every vertical drag, and the size axis is dead on any screen tall
  enough to scroll.

Roll behaviour is index arithmetic on `Valence` (`polarityIndex`, `sizeIndex`,
`at(polarityIndex:sizeIndex:)`, `sizesAbove` / `sizesBelow`), so
`ValenceRollTests` asserts stepping, clamping and the ladder marks without a
gesture. What tests cannot cover — and what needs a device — is the feel: the
detent spacing, the spring, and whether the vertical axis really wins against
the ScrollView.

## Revision 4 (2026-08-24) — no overtitle, and the clipping fix that actually holds

**The `A BIG` / `BIG` overtitle is gone**, along with its strings and
`Valence.Headline.prefix`. The lockup is two lines: the word and the span. Size
is carried by the word's own size and by the pyramid, which is what the two
axes were always meant to say without a third line saying it again.

**The terminal-letter clipping needed a second fix.** Revision 2 added
horizontal frame padding (`PebblesFont.inkOverhang`). That was measured against
`ImageRenderer`, where it works — and it does not hold on device, because a
frame is not what glyphs are clipped to.

The real numbers, from CoreText (`CTLineGetImageBounds` against
`CTLineGetTypographicBounds`) across all three sizes and both cases:

| size | right overhang | top overhang |
|---|---|---|
| 34pt | 3.0 – 4.7pt | none (ink stays 8–13pt below the ascent) |
| 44pt | 3.9 – 6.0pt | none |
| 56pt | 5.0 – 7.7pt | none |

So the cut is only ever on the right, and never more than 8pt — which the
padding already covered, proving the padding was not what was failing.

The fix that holds is to pad the **string**, with a space on each side
(`PebblesFont.needsInkPadding`). A space carries real advance width, so the
room is part of the line the glyphs are clipped to and travels with the text no
matter what measures its bounds; padding both sides keeps the word centred.
`inkOverhang` stays as the frame-level belt to that's braces, now set from the
measurement rather than by eye.

The lesson for the next hand-font token: **measure the ink, don't eyeball the
render, and don't trust a frame to hold glyphs that escape their advance.**

## Revision 5 (2026-08-24) — selection inverts, and the mesh joins the palette

**The shadow behind the selected stone is gone.** What replaces it is stronger:
selection now **inverts the stone's two roles**. The wash becomes the solid and
the ink goes pale, so the chosen stone reads as filled in rather than as merely
less faded than its neighbours — lowlight turns a solid `SystemSecondary` with
`SystemBackground` artwork, neutral a solid `AccentPrimary` with `AccentLight`,
highlight the full mesh with `AccentLight`. It is the same treatment
`EmotionPalette.pebbleFrameColors(forIntensity: 3)` already gives a hero pebble
on the Path, so the picker is borrowing a language the app speaks rather than
inventing one. Dimming (0.45) and the scale-up (1.14) stay.

**The mesh is sampled from a reference image.** Several attempts failed on
looking at them: the original purple / pink / orange / yellow read as a photo
filter; a mesh built from three emotion-category secondaries (Joy, Pride,
Peaceful) was no better; a full-hue-wheel pastel reference was clownish however
its ink was tuned. The gradient now comes from a **warm** reference — rose at
the top left, blush across the top right, gold rising from the bottom left —
sampled programmatically at each of the mesh's own control points with a patch
average, so no single noisy pixel decides a corner.

**Hue range is the setting that mattered, not saturation or lightness.** Every
sample in the warm reference lands between 3° and 41°. That is what makes a
saturated ink affordable: a gradient this narrow reads as ember when you push
it, where a full-wheel one reads as a clown's palette at the same saturation
and as mud if you darken it instead. Two rounds were spent tuning the wrong
knob before the reference itself changed.

**Highlight needs two gradients, not one.** The wash is far too light to draw
with: a stone inked in it disappears against the page. So the sampled wash
fills, and a twin draws — each sample keeping its hue and taking a fixed
saturation and lightness (HSL 0.90 / 0.52), which runs gold through orange to
coral.

Highlight inverts like the others, but with its own materials: the selected
stone takes a **third** gradient — the same hues at HSL 0.92 / 0.58 — and draws
its artwork in **white**. The resting wash could not carry a white outline
(white on its gold corner has almost no contrast), and selection is exactly
where the stone should shout, so the wash goes to full strength rather than
merely to full opacity.
