# iOS draws the web and Android stone — design

**Date:** 2026-11-26 · **Issue:** #1690 · **Surface:** ios · **Size:** large (new renderer under every iOS pebble surface)
**Status:** approach approved by the maintainer 2026-11-26; this document awaits review.

## Intent

The same pebble must look like the same stone on every surface. Web (#1052) and Android (#1122) draw one stone: the vector construction in `apps/web/components/pebble/stone/LitStone.tsx`, which Android ported to Compose in `core/ui/render/stone/`, with tones mixed identically and each pebble's details drawn from the same seeded recipe. iOS kept its own stone: the Metal material of #974 (`Features/Path/Stone/`, `Resources/Stone/stone.metal`), with its own tone picks (`StoneTones.starting`, `StoneMaterial.starting`, `StoneLook.standard`) and a tilt-following light. What sits around the stone is already shared: the outline silhouettes, the seal, the rings (`StoneRingGeometry`), the halo, the ripple, the glint and the press. What is not shared is the stone itself: its paint, the canvas and carve transforms, and the carving data as drawn.

The maintainer wants iOS aligned with the other two. The end-of-record reveal (too big, not a ceremony) is a **separate design** that builds on this one, after the prototypes are judged.

## Goals

- iOS paints the stone from the same layers, tones and seeded recipe as web and Android, so a pebble id gives the same stone on all three.
- Every iOS surface that draws the lit stone changes in one release, with no screen showing the old stone next to the new one. Four entry types reach the Metal `StoneView` today: `PebbleStoneView` (incl. `.blank`), `SpineStone` (which wraps it), `VersoStone` and `ValenceStoneView` (which builds `StoneView` directly). Through them, 19 files draw a stone: `PebbleRow`, `CollectionsNote`, `CompleteRicherStep`, `ChecklistIllustrations`, `GuideIllustrationView`, `CardVerso`, `VersoStone`, `SpinePebble`, `WeekContents`, `MilestoneMarks`, `MonthSheet`, `WeekSheet`, `WeekTiles`, `PromptRow`, `ValencePickerContent`, `SpineStoneGallery`, and the three wrappers. Part 3 greps these again before switching, since a printed place may draw `StoneSeal` rather than the lit stone and needs no change.
- Nothing redraws at rest: one paint per (size, polarity, palette, glyph, seed, **pixel size**, display scale, colour scheme), cached as an image, as Android's `StoneBitmapCache` keys on `w×h` and density.
- The Metal stone, its tables, its lab and its tilt motion are deleted, together with the dead renderers #975 left behind.

## Non-goals

- The success-step ceremony and the stone's size there (own design, after the prototypes artifact).
- Any change to web or Android, the shape seeds, the carving tables or the server. Web's tones are the reference; iOS is tuned to them, never the reverse (`stone-tones.ts` header).
- The AGSL renderer (#979) or any shader stone on another surface.

## Anti-goals

- A "close enough" port tuned by eye. Parity is proven by tests on web's vectors, or it isn't parity.
- Two stones in one build, behind a flag, for longer than the parts that build the new one.
- A per-frame cost on the Spine: a week page holds dozens of stones.

## Constraints

- **Cross-surface rule:** test the shared shape against the other surfaces' real values, verbatim. The tone mixes, the recipe output for given seeds, and the geometry transforms are asserted against the Vitest vectors in `apps/web/lib/utils/spine/stone-recipe.test.ts`, `apps/web/lib/config/stone-geometry.test.ts` and `stone-carvings.test.ts` (Android's `StoneRecipeTest`, `StoneTonesTest` and `StoneGeometryTest` did the same).
- **Seed:** already identical. `StableHash.seed` is FNV-1a over the lowercase id, like web `hashUUID` and Android `UuidHash.fnv1a`. The 24-bit fold (`StoneView.shaderSeed`, through `crustSeed`) goes away with `StoneView`; the recipe takes the full 32-bit seed (web `stoneRecipe(size, polarity, seed)` → `random(seed)`, grain shift `seed % 97`).
- **Battery contract** (2026-09-27) carries over: no time input in any paint, finite animations only, and Low Power Mode draws flat (body + ink, no grain, no blur).
- Dark scheme: web dims the stone through `--spine-stone-filter` (`brightness(.92) saturate(.96)`) and brightens lava through `--spine-lava-filter` (`brightness(1.2) saturate(1.05)`) in `apps/web/app/globals.css`. Android carries the same in its painter. iOS does too.
- Swift Testing only. The reference renders will move on every stone surface. 27 references already fail locally on untouched main on this Mac, so the gate is a diff against a baseline run, with CI as the real judge.
- Palette slot convention: `dark` is the darkest tone, `shaded` the mid one (the seed INSERT lists them the other way round).

## Approaches

1. **Port Android's construction to SwiftUI `Canvas` (chosen).** A pure-Swift tones + recipe layer, a `Canvas` painter that draws the layers in web order, and an image cache, all behind `PebbleStoneView`'s existing API. It is the path Android already proved: same structure, file for file, the same tests. It costs roughly the Android port's size (about 3,100 lines including the seal. The seal, rings, motion and `SVGTurbulence` already exist on iOS, so this port is somewhat smaller.).
2. **Keep Metal and retune it to web's tones.** It's cheaper, but the material is still Perlin noise, not facets, dots and veins from the recipe. The stones would only look alike, never be the same, and every future web change would need a shader re-tune. Rejected: it keeps the divergence the maintainer wants gone.
3. **Render web's SVG on iOS** (a WebKit snapshot or an SVG library). Rejected: a web view per stone or an SVG dependency for one component, CSS `color-mix` and filter semantics to emulate anyway, and no control over caching.

## Decisions taken

- **D1 — The tilt light goes.** Two places still follow the phone: the richer step (`followsTilt: true` in `CompleteRicherStep`) and the valence picker (`ValenceStoneView` feeds `motion.lean` to the active stone and starts the motion source on appear). Neither web nor Android can match it, and it is the one place the battery contract was stretched. `Features/Path/Stone/Motion/` (BodyTilt, Lean, LeanMapper, StoneMotionSource) is deleted with it. The richer step's removal was approved 2026-11-26 with the plan; **the picker's is pending the maintainer** (see D8).
- **D2 — One switch-over.** All call sites move in one part, as #975 did, so the app never shows two stones.
- **D3 — The stone lab goes.** Its job was tuning the Metal uniforms; the tones now come from web. The Spine stone gallery (`SpineStoneGallery`) shows the new stone and stays as the Debug comparison page.
- **D4 — `PebbleStoneView`'s public API stays** (valence, glyph, palette, `blank(seed:)`), except that `followsTilt` is removed and **`seed:` becomes required**. A real pebble passes `StableHash.seed(pebble.id)` everywhere, so `PebbleRow` (souls, collections) and the richer step (`CompletionResult.pebbleId`) draw the same stone as the Spine. A sample stone with no pebble (the picker, the home illustrations) passes a fixed per-valence seed constant, so it never changes between launches. *(Builder's call, settled here.)*
- **D5 — Scale rule: Android's.** Web's px constants (`SHADOW_*_PX`, `LIP_PX`, `CARVE_STROKE_PX`, hairline, AO and edge widths, grain) are defined at `STONE_WIDTH.phone`. iOS applies them the way `StonePainter.kt` does: px × display scale for the hairline, shadow, edge and grain (fixed in points whatever the stone's size), and the carve and glyph strokes also × `carveGain` (Android's gain for stones drawn larger than phone width). The verso, the picker and the richer step therefore draw the same line weights as Android at the same sizes. *(Builder's call, settled here.)*
- **D6 — The shadow overflows the layout box** at every call site, as `StoneView.shadowLayer` does today and as Android's bitmap grown by a shadow margin does. The cached image is larger than the layout box and drawn unclipped; a site that clips today keeps clipping. *(Builder's call, settled here.)*
- **D7 — Lava keeps its wobbled body.** Web's `StoneSprite` wobbles only the lowlight silhouette (`wobbleBackdrop`), and the river pebble and the gem keep the soft outline; Android does the same, and iOS's `WobbleRenderer.backdropArt` already does. The body layer is that silhouette, not always the flat outline. The clip is the same wobbled silhouette.
- **D8 — The valence picker (pending the maintainer).** Proposal: the picker drops the tilt too, and the selection's one-off light sweep (`isSelected` + `stoneSweeps`) becomes the Spine press already on iOS (`StoneMotion` dip + glint), finite and shared. Its stones take the sample glyph from `stone-glyph.json`, as today, and the fixed per-valence seed from D4.

## Layers (the contract, in web order)

Bottom to top, inside the outline silhouette's clip unless noted:

1. Far and near shadows (outside the clip): the body path offset by `SHADOW_FAR_PX` / `SHADOW_NEAR_PX` and blurred, at the scheme's shadow tone and alpha.
2. Body: the silhouette (wobbled for lava, D7) filled with the polarity's `BODY_RAMP` radial gradient (cx .38, cy .3, r .9, fx .34, fy .24).
3. Gem: the recipe's facets (slot colour, opacity, light-edge stroke).
4. Lava: the recipe's dots.
5. Carving (skipped on a blank stone), through `carveTransform`: for lava, the fossil first; then the veins, one stroke per `VEIN_TONES` paint (offset, glow); for the other materials, the fossil after the veins.
6. River and gem: the dots after the carving. Gem: the sparkles.
7. Soft light (not gem): the recipe's blurred white ellipse.
8. Grain, if the polarity has any: turbulence noise over the box in screen px, shifted by `seed % 97`, at `GRAIN_STRENGTH`.
9. Inner edge shade: the body path stroked at `EDGE_SHADE` and blurred (ambient occlusion).
10. Glyph (not blank): the lip stroke offset by `LIP_PX`, then the ink, through `carveTransform` and `glyphTransform`, wobbled by glyph side.
11. Glint group, at `GLINT_STRENGTH`: the sheen, sweep and spark the Spine press animates. These already exist as SwiftUI layers (`StoneGlint`), drawn as fractions of the stone's frame and clipped to the outline. Web's `glintGeometry` is in seed-canvas units under `canvasTransform`. The numbers agree on a box-fit stone, so `StoneGlint` stays as it is unless the gallery shows a drift. If it does, it moves to `glintGeometry` under the ported transform.

## Parts (one part = one PR, stacked)

1. **Geometry, tones, recipe and carvings: pure Swift, no UI**, under `Features/Shared/Stones/Lit/`:
   - **Geometry.** Port `stone-geometry.ts` (`SEED_CANVAS`, `unitsPerPx`, `canvasTransform`, `carveTransform`, web's `glyphTransform` and `glyphSide`, `ringGeometry`, and the shadow, lip, carve and blur constants), as Android's 270-line `StoneGeometry.kt` did. None of it exists on iOS yet. The existing `glyphTransform` there has a different shape and is not reused.
   - **Tones.** Port `stone-tones.ts`: the sRGB and OKLab `color-mix` mixes, resolved to colours per palette.
   - **Recipe.** Port `stone-recipe.ts`: `random`, dots, facets, sparkles, soft light and glint geometry.
   - **Carvings.** The raw vein `d` strokes and the raw fossil `d`, parsed at load from the bundled `stone-shape-*.svg` with the existing `StoneShapeParser`, as Android parses its assets in `StoneCarvings.kt`. They are not `StoneCarvingArt`'s wobbled fills.
   - **Tests.** They run on web's own vectors, copied verbatim: `stone-geometry.test.ts`, `stone-recipe.test.ts`, and a carvings test against `stone-carvings.ts`'s table. A mix table is checked against values computed in a browser.
2. **Painter, cache and gallery.**
   - **Painter.** A `StonePainter` that draws the layers above into a `GraphicsContext`, with the dark-scheme filters.
   - **Glyph line.** Port Android's `wobbleGlyphLine` approach: flatten each stroke, displace it with the existing `WobbleParams.displace` and `SVGTurbulence`, and stroke it round-capped at a constant px width, as web's `non-scaling-stroke` does. It is not `WobbleRenderer.glyphInk`'s filled outline. No web vector covers the glyph today, so this part adds one: points generated by running web's `wobbleGlyphLine` on a fixture glyph, asserted on iOS within a tolerance.
   - **Grain.** Extend the existing `SVGTurbulence` (`Features/Path/Render/Wobble/`, golden-tested, R and G channels only today) to all four channels, the grain's baseFrequency and octaves, and stitching, so it matches web's `feTurbulence` grain as Android's does.
   - **Cache.** A `StoneImageCache` keyed as in the Goals. The gallery shows Metal and vector side by side for all 9 valences × the reference palettes. Nothing shipped changes yet.
3. **Switch-over.** `PebbleStoneView`, `SpineStone`, `VersoStone` and `ValenceStoneView` draw the cached vector stone, and the picker follows D8. `followsTilt` is removed and the 24-bit seed fold is dropped. References are re-baselined on CI. A decision-log entry supersedes the 2026-09-27 Metal render entries.
4. **Deletion.** `stone.metal`, `StoneMaterial`, `StoneLook`, `StoneLight`, `StoneSweeps`, `StoneShaders`, the old `StoneTones` and whatever of `StoneCarving` only the shader reads, `Stone/Motion/`, `Features/StoneLab/`, and the dead `PebbleAnimatedRenderView`, `PebbleStaticRenderView`, `PebbleOutlineBackdropView`, each after a grep proves no caller is left. `ValenceStoneStyle` stays: only `headlineInk` remains in it and `ValenceHeadlineView` reads it. `PebbleOutlineGeometry` and the parsers stay: the outline silhouette is the body. `StoneMaterialTests` and kin go with their subjects.

The ceremony design lands after Part 3 and can stack on it.

## Left to the builder

- File names and folder layout inside `Features/Shared/Stones/` (follow Android's names where it helps cross-reading).
- How the `color-mix` mixes are resolved: precomputed per palette at load, or on the fly, as long as the test values match.
- The cache's eviction policy and whether it paints off the main actor (Android paints off the main thread).
- Whether the Debug gallery keeps a Metal column until Part 4 or drops it in Part 3.
- Whether `PebbleOutlineGeometry` gains a per-polarity box like web's `getOutline(size, polarity)`. Both are the same today (337×270, 350×350, 335×400), so per size is enough until a seed changes.

## Map and records

- Arkaik: no view is added or removed. Move the affected iOS acceptances to `development` when Part 1 starts.
- Decision log: one entry at Part 3 (supersedes the 2026-09-27 Metal render entries, D1 tilt removal included).
- Data safety: no change (no new data, SDK or permission).
