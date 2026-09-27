# iOS stone material lab — design

**Date:** 2026-09-27
**Issue:** #974
**Status:** approved for planning
**Scope:** one Debug-only iOS screen. Nine stones, three materials, seven
emotion palettes, a movable light. No shipped surface changes.

## Purpose

Pebbles are drawn today as flat ink on a soft wash. This lab is where the
polished render is judged before it reaches any real surface: a stone that
reads as lava, river pebble or gem depending on its polarity, coloured by its
emotion, with the veins, the fossil and the glyph cut into it rather than
printed on it.

Two later steps depend on what this page settles. The feed will take the
material as a still picture. The pebble detail page will take it live, with
the phone's tilt moving the light, using the motion source already built in
the Femfolk native card POC. Both are out of scope here; the lab only has to
make the still picture good and keep the render cheap enough that the tilt
can be added without a redesign.

## The orthogonal model

A stone is the product of four independent axes. The lab shows the first two
crossed with the third and lets the fourth follow.

| Axis | Values | What it drives |
|---|---|---|
| Size | small, medium, large | Which of the nine shapes; how many veins the seed carries (see below) |
| Polarity | lowlight, neutral, highlight | Which of the nine shapes; the material; which palette tone is the body |
| Emotion group | anger, fear, joy, peace, pride, sadness, shame | The palette every tone is taken from |
| Glyph | any carved glyph | The carving inside; fixed to one sample glyph in the lab |

Material by polarity:

- **Lowlight — lava stone.** Coarse crust plates separated by dark cracks, a
  matte surface, almost no sheen. Harsh.
- **Neutral — river pebble.** Fine, low-contrast grain with a faint sediment
  banding along one axis, a smooth soft sheen. Calm.
- **Highlight — gem.** A cellular field cut into facets, each with its own
  normal so it blinks as the light crosses it, plus sparse glitter points.
  Chroma stays inside the emotion hue: no rainbow, this is not a foil card.

Colour by polarity × emotion, from the seeded six-tone palette
(`primary`, `secondary`, `light`, `surface`, `dark`, `shaded`):

| Polarity | Body tone | Carving ink | Lip highlight | Lip shadow |
|---|---|---|---|---|
| lowlight | `dark` | `shaded` | `secondary` at low opacity | `shaded` |
| neutral | `secondary` | `dark` | `light` | `dark` at low opacity |
| highlight | `primary` | `dark` | `light` | `dark` |

These are starting values; every one of them is a slider on the page.

## Where the stone comes from

Nothing new is drawn. The lab reuses the two wobbled layers every pebble
surface already composes, and gives them new roles:

- **The body** is the backdrop silhouette, `Outlines/<size>-<polarity>.svg`,
  wobbled through `WobbleRenderer.backdropArt`. Today it carries the soft
  wash; here it carries the material.
- **The carving** is the engine shape for the same size and polarity, copied
  verbatim from `docs/seeds/shape-seeds/<size>-<polarity>.svg` into
  `Resources/StoneLab/`, plus one sample glyph. The engine's convention makes
  the parts distinguishable by order without ids: the first stroked path is
  the outline, every following stroked path is a vein, the single filled path
  is the fossil. Outline and veins go through `WobbleRenderer.glyphInk`, the
  fossil through `backdropArt(fromAsset:)`, exactly as `ValenceArt` does.
  The seeds do not follow the 0/2/3-by-size rule the brief assumed. Measured
  on 2026-09-27 (lowlight / neutral / highlight): small 0/0/0 veins, medium
  1/1/2, large 1/2/6; and `large-highlight` carries no fossil at all. The lab
  draws what the seeds hold; changing the seeds is engine work, not lab work.
- **The glyph** is the first glyph of `docs/seeds/domain-glyph-seed.json`
  (the `identity` strokes), copied into the same resource folder, inked with
  `glyphInk` at its stored width and placed with the engine's zone transform
  from `layout.ts` (`GLYPH_SIZE`, `GLYPH_POSITION`) for that size.

The carving is scaled by `PebbleOutlineGeometry.pebbleScale` inside the body,
as `ValenceStoneView` does, so the outline sits ~12% in from the body's edge.
That inner outline is therefore a carved line on the stone's face, not the
stone's edge — the same reading the current wash-and-ink render gives, now
with depth.

All nine carvings are built once, off the main actor, and cached in a
`StoneLabArt` store mirroring `ValenceArt` (lock, cache, `prewarm()`).

## Layer model

Per stone, bottom to top. Every layer is a SwiftUI view; the shader work
happens in two `layerEffect` passes.

1. **Body fill.** `WobbledBackdropShape` filled with the body tone.
2. **Material.** A `.layerEffect(ShaderLibrary.stone(...), maxSampleOffset: .zero)`
   over the body. The shader reads the body's premultiplied colour, so it
   paints only where the silhouette is, and returns the lit material in the
   emotion's hue. One entry point, a `material` integer selects lava, river
   or gem inside it (three functions, one dispatch, one pipeline state).
3. **Carving.** The outline, veins, fossil and glyph inks merged into one
   `Shape`, drawn as a `Canvas` and passed through a second layer effect,
   `carve`, which turns the flat ink into a groove: it samples the ink a
   couple of points toward the light and away from it and emits the lip
   highlight on the far side, the lip shadow on the near side, and the ink
   itself in the carving tone between them. `maxSampleOffset` is the lip
   width, a slider. Sampling in the shader rather than drawing the ink three
   times offset keeps the lip perpendicular to the light at every pixel,
   which is what makes a curve read as cut.
4. **Sheen.** A radial pool centred on the light, `.screen` blended, masked
   by the body silhouette, with a strength per material (lava ≈ 0.05, river
   ≈ 0.18, gem ≈ 0.35 plus the facet blink already inside the material pass).
   This is the layer the tilt will move most, so it stays a plain SwiftUI
   gradient the way Femfolk's `LightPool` is.

Reduce Motion changes nothing on this page (nothing moves by itself). Low
Power Mode, and the page's own Low Power toggle, drop layers 2 and 4 and
draw the carving flat: the body tone, the ink, nothing else. That flat path
is what the feed will draw regardless, so the lab shows both side by side.

## The shader

`Resources/StoneLab/stone.metal`, one file. XcodeGen picks `.metal` files up
as sources for the app target; no build setting changes.

Carried over verbatim from `femfolk/ios/FemfolkCard/Sources/Shaders/foil.metal`:
`hash21`, `grad2`, `pnoise`, `fractal`. Seeded, never time-based, and
sampled in a stone-space unit (the body's viewBox unit scaled to points) so a
grain size means the same thing on every stone and at every on-screen size.

Uniforms, all packed in a Swift `StoneMaterial` struct with a `shaderArgs`
builder so the views never spell the argument order out:

| Uniform | Meaning |
|---|---|
| `size` | body frame in points |
| `material` | 0 lava, 1 river, 2 gem |
| `light` | light direction, a unit `float2` in the stone plane, plus elevation |
| `body`, `ink`, `lipLight`, `lipShadow` | the four palette tones, as `float4` |
| `seed` | per shape, so two stones never share a crust |
| `scale` | grain frequency |
| `relief` | height-field depth used for the normal |
| `contrast` | how far the material may darken or lighten the body tone |
| `sheen` | specular strength |
| `facetDensity`, `glitter` | gem only |
| `crack` | lava only: crack threshold |
| `banding` | river only: sediment anisotropy |

Each material computes a height field from the noise, derives a normal by
finite differences, lights it with a Lambert term plus a Blinn specular
against `light`, and mixes the result into the body tone within `contrast`.
Lava thresholds the field into plates and paints the low band as cracks in
`ink`. River keeps the field continuous and stretches one axis by `banding`.
Gem replaces the height field with a cellular (Worley-style) distance and
uses the cell id to pick a flat normal per facet, then adds glitter where a
high-frequency noise passes a threshold that tightens as the specular term
falls.

`carve` is a separate entry point in the same file:
`carve(position, layer, offset, lipLight, lipShadow, ink)`. It samples the
layer at `position + offset` and `position - offset`, where `offset` is the
light direction times the lip width in points, and composes: shadow where the
ink lies ahead, highlight where it lies behind, the ink itself where it is.

## The page

`Features/StoneLab/StoneLabView.swift`, reached from a `#if DEBUG` "Stone
lab" row in the settings sheet's Legal section (the only place a debug row
can sit without redesigning the sheet). Pushed on its own `NavigationStack`
as a full-screen cover.

- **Grid.** Three columns (lowlight, neutral, highlight) by three rows
  (small, medium, large), each cell a `StoneView` at a shared height, the
  same `ValenceFanLayout` proportions as the picker so the sizes read
  against each other.
- **Emotion switcher.** A horizontal picker of the seven groups, in
  `EmotionCategoryOrdering` order, chips tinted with the group's primary.
- **Light.** A drag anywhere over the grid sets the light direction from the
  finger's offset relative to the grid centre; release springs it back to
  top-left at 55° elevation. The page redraws while the finger moves and
  not after.
- **Knobs.** A bottom drawer with one slider per `StoneMaterial` field for
  the material under the finger's last stone, a "Low Power" toggle, and a
  "Flat" toggle that shows the feed rendering. A "Reset" restores the
  starting table above. Values are in-memory only; the maintainer reads the
  numbers off the sliders and writes them into the table when a look is
  settled.
- **Detail.** Long-press a stone to see it alone at detail size (the read
  sheet's heading slot) with the same light and knobs.

The palette is a hard-coded table of the seven seeded sextets
(`StoneLabPalettes`) so the page works offline and signed out.

## Battery contract

Every shader is a pure function of its uniforms. There is no `TimelineView`,
no timer, no animation on this page except the light's spring-back on
release, which ends. A still page issues no GPU work after its last change.
Low Power Mode, read from `ProcessInfo.isLowPowerModeEnabled` and its
notification, selects the flat path. The detail page will inherit these two
rules, and add the Femfolk dead band on the tilt so a still phone is a still
page.

## Testing

Unit tests in `PebblesTests/StoneLab/`:

- `StoneShapeParserTests`: every one of the nine seeds parses to one outline,
  the measured vein count per valence, a fossil everywhere but
  `large-highlight`, and the outline is the first stroked path.
- `StoneLabPalettesTests`: the seven sextets equal the literals in
  `20260912120000_seed_emotion_reference_data.sql`, so a palette edit in the
  migration fails here rather than drifting.
- `StoneMaterialTests`: the starting tables per polarity, and that
  `shaderArgs` emits the uniforms in the declared order.

Visual judgement is on the phone and the simulator: a PNG per emotion at the
rest light and one at a raked light, captured through a temporary
`ImageRenderer` test as the project already does, dropped in the scratchpad
and not committed.

## Out of scope

The gyroscope and any motion source, the feed, the read sheet, the valence
picker, Android and web, any change to `render_svg`, `render_version` or the
engine, and the wobble renderer. No Lab Note: the PR is Debug-only and
carries `no-lab-note`.
