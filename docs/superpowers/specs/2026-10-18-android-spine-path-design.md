# Android Path: the Spine — design

- **Issues:** [#1122 — The Android Path becomes the Spine](https://github.com/alexisbohns/pbbls/issues/1122) (android) and [#1121 — path_moments carries each pebble's souls](https://github.com/alexisbohns/pbbls/issues/1121) (supabase, Part 1).
- **Date:** 2026-10-18
- **Scope:** `apps/android` (the Path tab's week view, the stone render, tokens, strings) and one additive RPC change in `packages/supabase`. No schema change. iOS untouched.
- **Size:** Large (a six-part `gh stack`; replaces the Path's rows, header and stone).
- **Lineage / references:**
  - The shared rule set: `2026-10-11-web-path-spine-design.md` (#1052), §The rule set, §The lit stone on web, §Accessibility. Where this spec says "the rule set", it means that section; the numbers are not repeated here unless Android changes them.
  - The paper: `2026-10-18-android-spine-brand-scheme-design.md` (#1118), §The rule set: Spine tokens → M3 roles. Shipped in #1120.
  - The web implementation is the port source: `apps/web/components/path/spine/`, `apps/web/components/pebble/stone/`, `apps/web/lib/utils/spine/`, `apps/web/lib/config/stone-*.ts`, `apps/web/lib/i18n/number-words.ts`, and their Vitest files, whose vectors this spec reuses.
  - Visual reference: the final mock in the design hub (`final/index.html`, https://claude.ai/artifact/YWnrBsWLbbkTTkZ3ra561z) and the web `/sandbox/design` "Path · Spine" specimen.
  - Android architecture: `apps/android/CLAUDE.md` (ViewModel per screen, stateless content layer, `core/` vs `features/`, `ThemeLiteralsTest`, screenshot gate).
  - The lit stone on iOS and the AGSL mirror: `2026-09-27-ios-stone-material-lab-design.md`, #979 (stays open).

## Summary

The Android Path keeps its week pager and its data path, and each page stops being a list of rows. A week opens like a gathering: a title written in words, the persons of the week with a seven-dot strip of the days each one appears, a contents list. Below, one line runs down the left margin over raked paper. Each pebble's **lit stone** sits on that line, drawn in Compose from the same construction as the web's SVG stone over the geometry Android already carries. A pebble with a photo hangs a **Deck card** off its stone, sized to the photo's own ratio; a pebble without one is **set as type**. Each day starts at a hairline seam with the date in the margin. Pressing a stone dips it, ripples the ground, glints, and opens the pebble. The paper is the brand scheme's `surface`, so with wallpaper colours on the Spine takes the wallpaper's tint like every other screen.

## Decisions (maintainer, 2026-10-18)

| # | Decision | Rationale |
|---|---|---|
| D1 | **The margin stone is a Compose port of the web SVG construction**, bitmap-cached, drawn over the iOS geometry (backdrop silhouette as the body, engine seed scaled by `pebbleScale × 1.22` as the carving). Not the existing flat stone, not the AGSL render of #979. | Same stone as web at feed size, static light, no GPU work at rest. #979 stays open as a later render that can replace `LitStone` behind the same interface. |
| D2 | **Souls come from `path_moments`.** The pebble jsonb gains `souls: [{id, name, glyph_id}]`, additive. Android reads them from the timeline row; glyphs come from the souls list the app already loads. | The web reads `v_moments_full`; a timeline should not. Additive keys are safe for every installed client (supabase-kt and Codable ignore unknown keys), and iOS gets the field for free. |
| D3 | **The snap ratio is probed on device.** A card starts square and eases to the photo's ratio once the thumb's bounds are decoded, once per snap per session. No `snaps.width/height`. | Same rule as web (its D14). Stored dimensions stay a separate backend issue for all three clients. |
| D4 | **One column everywhere**, at the readable width; from 840 dp the existing list-detail pane shows the pressed pebble beside the Spine. No spread. | The pane is the Android second page already, and the spread would fight it for width. |
| D5 | **Chrome:** the week roll is restyled to the Spine's; the date pill goes (the opening is the header); the masthead and the mouth carry the week links; the FAB stays; no dock; no "carve a pebble" link in the mouth. | The four-tab bar and the FAB are the Android idiom (#852, #854); the dock's content has other homes on Android. |
| D6 | **The page grain is dropped; the rake ground stays.** | The grain is a 5–10 % noise wash over the whole sheet; on a phone it is at the edge of visibility and costs a full-screen texture. The rake is what reads as paper. |
| D7 | **Type and colour tokens live in `core/designsystem`** (`SpineTypography`, `SpineColors`), so `features/path` never names a size, a colour or a corner. Small caps and lining figures come from Ysabeau's own `smcp` / `lnum` / `tnum` features. | `ThemeLiteralsTest` (#853) forbids literals in `features/` and `core/ui`. The bundled `ysabeau.ttf` carries the features, so no new font files. |
| D8 | **Pure logic is tested against the web's own vectors.** Every ported module's JVM test replays the web Vitest file's inputs and expected outputs. | The cross-surface rule (root `CLAUDE.md`): a same-surface round-trip cannot catch a port drift; the web tests are the shared contract. |

## Non-goals

- The AGSL lit stone (#979), the tilt light, the sweep on appearance. iOS.
- The spread; stored snap dimensions; multiple photos per card; cards leaving the page (share, Cairn).
- Any change to Capture, Complete, Edit, the read page, the delete flow or the week-roll mechanics beyond its look.
- Karma and ripple status on the Path (none shows today on Android; unchanged).

## Architecture

### Component map

Under `features/path/spine/` (new):

| Composable | Role | Port of |
|---|---|---|
| `SpineWeek` | One pager page: a `LazyColumn` with the masthead, opening, persons, contents, days and mouth; owns the reading-day spy and the contents → press | `SpineWeek.tsx` |
| `WeekMasthead` | "week 40" back link, the week's ISO number | the masthead in `SpineWeek.tsx` |
| `WeekOpening` | Kicker, title in words, summary | `WeekOpening.tsx` |
| `PersonsOfWeek` | Rows of glyph, small-caps name, seven dots; weekday-letter header; eight-row cap with "and N others" | `PersonsOfWeek.tsx` |
| `WeekContents` | Rows of day, seal, name, time; the reading day's rows get the ink dot | `WeekContents.tsx` |
| `SpineGround` | The rake, drawn with `Canvas` behind the days, masked past the column | `@utility spine-ground` |
| `DaySeam` | Seam, teeth, margin date; *still* when empty | `DaySeam.tsx` |
| `SpinePebble` | Stone on the line, ripple host, leader, then card or entry; the press choreography | `SpinePebble.tsx` |
| `SpineEntry` | Name, meta, people line | `SpineEntry.tsx` |
| `SpineCard` | Index, rail, field with the photo, title, souls strip, painted edge | `SpineCard.tsx` |
| `PeopleLine` | "*with* [g] A & [g] B", the overflow rule | `PeopleLine.tsx` |
| `WeekMouth` | Small cairn, "the week ends here", "week 42" link | `WeekMouth.tsx` |
| `SpineEmptyWeek` | The current week with nothing in it: today's `EmptyWeek`, restyled | `PathEmptyState.tsx` |

Under `core/ui/render/stone/` (new; `core/ui` because it renders a Pebbles domain type):

| Unit | Role | Port of |
|---|---|---|
| `LitStone` | The pressable stone: rings and halo layer, stone layer (bitmap), glint layer | `LitStone.tsx` + `StoneSprite.tsx` |
| `StoneSeal` | The flat print: relief, line, engraving | `StoneSeal.tsx` |
| `StoneRipple` | Three stone-shaped waves, mounted for one press | `StoneRipple.tsx` |
| `StonePainter` | Pure drawing: given a `StoneSpec`, draws the layers into a `DrawScope` / native canvas; what the bitmap cache runs | the SVG tree of `LitStone.tsx` |
| `StoneTones` | Ramps, glyph, vein, fossil, edge, glint and grain tones over the five palette slots; `BLANK_PALETTE` | `stone-tones.ts` |
| `StoneRecipe` | Seeded facets, dots, sparkles, soft light; mulberry32 | `stone-recipe.ts` |
| `StoneGeometry` | Outline, canvas and seed spaces; `carveTransform`, `canvasTransform`, `glyphTransform`; ring and halo matrices; shadow and lip offsets; blurs | `stone-geometry.ts` |
| `StoneCarvings` | Veins and fossil parsed from `res/raw/shape_{size}_{polarity}.svg` | `stone-carvings.ts` |
| `StoneMotion` | Dip, rings, waves, sweep, spark timings; reach; delays | `stone-motion.ts` |
| `StoneBitmapCache` | `LruCache<StoneKey, ImageBitmap>` of painted stones | the browser's raster cache |

Under `features/path/spine/logic/` (pure Kotlin, no Compose):

| Object | Port of |
|---|---|
| `SpineWeekBuilder` (`spineWeek`, `readingOrder`) | `spine-week.ts` |
| `PersonsOfWeek` (`personsOfWeek`, `PERSONS_CAP`) | `persons-of-week.ts` |
| `PeopleLine` (`peopleLine`, `PEOPLE_LINE_MARKS`) | `people-line.ts` |
| `EntryType` (`entryType`, `contentsNameType`, `cardTitleCut`) | `entry-type.ts` |
| `CardSize` (`cardSize`, `cardCap`, `cardEdge`, chrome constants) | `card-size.ts` |
| `WeekWords` (`weekWords`, `summaryWords`, `weekdayLetters`, `dotDays`, `sentenceCase`) | `week-opening.ts` |
| `LocalDays` (`dayIndexInWeek`) | `local-day.ts` |

Under `core/common/`: `NumberWords` (`cardinalWords`, `ordinalWords`, `dayOfMonthWords`, EN and FR with gender), port of `number-words.ts`.

Under `core/designsystem/`: `SpineTypography.kt`, `SpineColors.kt` (§Tokens).

Under `core/data/`: `SnapAspectCache` (§Data).

Modified: `PathScreen.kt` / `PathContent` (renders `SpineWeek` per page, no `WeekHeader`), `PathViewModel` (souls map in `Content`), `WeekRoll.kt` (restyled), `PathPebble` (+ `souls`), `MomentPreviewFixtures` (souls, glyph-less pebble, blank moment, 14-soul pebble), strings.

Deleted, with what replaces each: `PathPebbleRow.kt` → `SpinePebble`; `WeekPebbleList.kt` → `SpineWeek`; `WeekHeader.kt` and `WeekHeaderFormatting.kt` → the opening and masthead; `PathSnapThumb.kt` → the card's image; `PathRowFormatting.kt` → `WeekWords` and the entry meta. `PebbleRow` (souls and collections lists), `PebbleStone` (read page, valence fan) and `BlankStone` stay.

### Data

| Need | Source | Note |
|---|---|---|
| Moments of the week | `path_moments()` → `WeekRollBuilder` | Re-sorted ascending by `SpineWeekBuilder`, as the web does; `WeekRollBuilder` keeps its order for the roll. |
| Souls per pebble | `PathPebble.souls` (new, #1121) | `[{id, name, glyph_id}]`; default empty so old payloads decode. |
| Soul glyphs | `SoulsServicing.list()` once per load, held in `PathUiState.Content.soulsById` | The persons rows and people lines draw `GlyphView` from it; a soul absent from the list (deleted) is skipped, as on web. |
| Pebble glyph strokes | `PathPebble.glyph` | Already there; null means no carving. |
| Palette | `LocalEmotionPaletteService` | Already carries the five tones. |
| Photo | `SnapURLCache.signedUrls(firstSnapPath)` | The card shows `original` with `thumb` as the Coil placeholder; the row's `thumb`-only load goes with `PathSnapThumb`. |
| Photo ratio | `SnapAspectCache` | Below. |

**`path_moments` change (#1121, Part 1).** In `20261012090200_moments_split_readers.sql`'s successor migration, the pebble object adds `'souls', coalesce((select jsonb_agg(jsonb_build_object('id', s.id, 'name', s.name, 'glyph_id', s.glyph_id) order by s.name) from public.pebble_souls ps join public.souls s on s.id = ps.soul_id where ps.pebble_id = p.id), '[]'::jsonb)`, mirroring the `v_moments_full` subquery. `create or replace` re-emits the whole function body: check that no other open migration re-emits `path_moments` (none does today) before applying. `verify-moments.ts` gains: a completed moment with two souls lists both, sorted by name, each with a `glyph_id`; an uncompleted moment still has `pebble: null`.

**`SnapAspectCache`** (`@Singleton`, `core/data`): `suspend fun ratio(storagePrefix: String): Float?`. It signs the thumb through `SnapURLCache`, decodes bounds only (`BitmapFactory.Options.inJustDecodeBounds` over Coil's disk cache, or Coil `ImageRequest` with `size(Size.ORIGINAL)` and `allowHardware(false)` reading the drawable's intrinsic size), caches by prefix, times out after 4 s with a `Log.w`. Lives for the process. `SpineCard` observes it with `produceState(initialValue = cached ?: null)`; the field's aspect ratio animates from 1 to the ratio over 240 ms (`animateFloatAsState`, `tween(240, easing = EaseOut)`), snapping under reduced motion. A failed probe keeps the square field and sets "photo unavailable" in `error` (orange, never red).

### Ownership and state

`PathViewModel.Content` gains `soulsById: Map<String, SoulWithGlyph>` loaded in `fetch()` beside the timeline (one `SoulsServicing.list()`; a failure logs and yields an empty map, the Spine then shows no persons rather than failing the timeline). `SpineWeek` holds only UI state: the `LazyListState`, the reading day derived from it, the in-flight press. Presses are per pebble, in `SpinePebble`: a `MutableState<PressToken?>` that mounts the ripple and runs the animations in a `LaunchedEffect`, then clears. Opening the detail goes through the existing `onPebbleTap(moment)` lambda after `OPEN_DELAY_MS`.

## The lit stone in Compose

### Spaces, from `StoneGeometry` (verbatim from web)

Three spaces meet in one stone, as on web: **outline units** (the backdrop's view box: 337×270, 350×350, 335×400), **canvas units** (the engine canvas: 250×200, 260×260, 260×310) fitted and centred into the outline box, and **seed units** (the canvas again, scaled by `pebbleScale × 1.22` and centred: the carve transform). Android has the first two already (`PebbleOutlineGeometry`); `StoneGeometry` adds the transforms as `android.graphics.Matrix` values, `unitsPerPx(size)`, the glyph zones (`StoneComposer.glyphSlot`, same table), `SHADOW_FAR_PX (3.86, 6.95)`, `SHADOW_NEAR_PX (0.93, 2.01)`, `LIP_PX (0.32, 0.54)`, `BLUR { near 5, far 15, ao 7, soft 12, glow 4 }` in canvas units, `CARVE_STROKE_PX 1.25`, ring steps `{ small 4.8, medium 4.4, large 4.2 } px`, `RING_COUNT { 1, 2, 3 }`, `HALO_STEP 0.78`, and `silhouetteFrame` (area centroid and box of the outer contour, computed once per shape from the flattened path).

On-screen width is the rule set's phone column: 34 / 44 / 54 dp (the "wide" column does not apply, D4). Density is handled by painting at the composable's pixel size.

### Body, clip, rings

The body is the outline asset's path (`OutlineAssets`), wobbled through `WobbleOutlineBuilder` **only for lowlight** (lava is cut rough; the pebble and the gem keep their soft edge), as the web's `StoneSprite` does. The clip is the same path. The rings and halo use the **flat** outline scaled about its centroid by the ring matrices. All strokes on the body (rings, edge shade) are in screen px.

### Layers, bottom to top (`StonePainter`)

1. Halo (filled `SpineColors.halo`) and rings (1 px, `SpineColors.ring`; `onSurfaceVariant` when touched under reduced motion), in their own layer so they can swell without touching the stone bitmap.
2. Two blurred body copies, `shadow` colour, offset by the far and near px, blur `far` and `near` scaled to outline units; opacity `shadowAlpha` and `× 1.35`.
3. The body filled with the polarity's **radial ramp** (`Brush.radialGradient`, centre `(.38, .3)`, radius `.9` of the box; Compose has no focal point, so the focus `(.34, .24)` is approximated by nudging the centre to `(.36, .27)`, a documented deviation).
4. Clipped to the body:
   - gem: five facets around the jittered pentagon plus the face, each a polygon in a palette slot at its opacity, with the hairline edges in `light` @ .3; river and lava: nothing here;
   - lava: the pits (dots) before the carving;
   - the carving in the carve transform: lava's fossil first; the veins per `VEIN_TONES` (lava's wide `primary` stroke blurred by `glow`, its narrow mix on top; the river's lit bed and shadowed far wall offset by (1.5, 3.5); the gem's light seams); the river's and gem's fossil after;
   - river and gem dots; the gem's two white sparkles; the river's and lava's soft light (ellipse blurred by `soft`);
   - grain where `GRAIN_STRENGTH > 0`: an `SVGTurbulence` tile (`baseFrequency .9`, two octaves, seed 7) sampled at a per-stone shift, as dark and light specks (the two colour matrices of the web filter), in screen px;
   - the inner edge shade: the body stroked in `dark` at the polarity's width and opacity, blurred by `ao`;
   - the glyph: the strokes twice through `glyphTransform` inside the carve transform, the lip first (offset by `LIP_PX`, `lip` tone), then the ink; stroke width a constant 1.25 px (divide by the accumulated scale), round caps and joins.
5. The glint layer, clipped to the body, opacity `GLINT_STRENGTH`: a sheen ellipse (radial white, opacity 0 at rest), a sweep band (linear white gradient, parked off the stone), a four-point spark (scale 0). Drawn live, not cached, because a press animates them.

Blur uses `android.graphics.Paint.setMaskFilter(BlurMaskFilter(radiusPx, NORMAL))` on a native canvas (`drawIntoCanvas { it.nativeCanvas }`); the glow and soft blurs are the same mechanism. Layer 2 to 4 are painted once per key into an `ImageBitmap` the size of the stone at the current density, held by `StoneBitmapCache` (key: size, polarity, palette hex quintuple, glyph id and content hash, seed, shadow colour and alpha, density; capacity 64 entries). A still week does no drawing after its first frame.

**Tones** come from `StoneTones`, the web file transcribed: `BODY_RAMP` per polarity with its `color-mix` stops computed in sRGB (or OKLab for the lowlight ramp, through a small `oklabMix` helper), `GLYPH_TONES`, `VEIN_TONES`, `FOSSIL_TONES`, `EDGE_SHADE`, `GLINT_STRENGTH { 1, .3, .34 }`, `GRAIN_STRENGTH { 0, .14, .32 }`, `SLOT_COLOR`, `BLANK_PALETTE` (the shame greys). A null palette (a moment with no emotion) draws a neutral river pebble in the blank ramp, uncarved, whatever its polarity. In Night the stone is drawn through the web's filters (`brightness(.92) saturate(.96)`; lava `brightness(1.2) saturate(1.05)`) as a `ColorMatrix` on the bitmap paint.

**Recipe** (`StoneRecipe`): the web file transcribed, including mulberry32 and the `r2` rounding, seeded by the same `hashUUID(pebble.id)` as web (port `lib/engine/seed.ts`'s hash to `core/common`), so facets and specks land where the web's do. `glintGeometry(size)` too.

**Carvings** (`StoneCarvings`): from the bundled seeds, parsed once per shape with the existing SVG parser: among the stroked paths the one with the largest extent is the outline and is dropped; the other stroked paths are veins; the filled path is the fossil (`fill-rule` kept). The counts are the web test's: veins 0/0/0, 1/1/2, 1/2/6 for small/medium/large × lowlight/neutral/highlight, no fossil on large highlight, and `large-neutral` lists its outline third.

### The seal (`StoneSeal`)

A flat print in one colour, for the contents list and the card index, sizes 20 / 24 / 27 dp by intensity (contents) and `4.4u` (card). Relief for highlight (solid spot, glyph knocked out to the ground), line for neutral (15 % tint, rim and glyph line), engraving for lowlight (9 % wash, 45° hatch clipped to the body, glyph with a ground halo). Spot is `primary` in Paper and `secondary` in Night; the lowlight deep is `shaded` in Paper and `secondary` mixed 80 % toward the ground in Night. Drawn directly (no bitmap; it is small and flat).

### Press, ripple, glint (`StoneMotion`, verbatim timings)

| Element | Animation |
|---|---|
| Stone layer | scale 1 → .9 at 26 % → 1.03 at 60 % → 1, 520 ms, `CubicBezierEasing(.3f, .7f, .2f, 1f)` |
| Rings layer | scale 1 → 1.08 at 35 % → 1, 1150 ms, delay 110, `(.2, .7, .2, 1)` |
| Wave k = 0, 1, 2 | the flat outline, 1 px `SpineColors.ripple`: scale 1.15 @ α 0 → 1.45 @ .85 at 7 % → `REACH[size] − .5k` @ 0; `1650 + 170k` ms, delay `120 + 230k`, `(.12, .55, .3, 1)` |
| Sweep | translate 0 → `1.6 × canvas width`, 900 ms, delay 140, `(.37, 0, .63, 1)` |
| Spark | scale 0 / rot 0 → 1 / 40° / α 1 at 35 % → 0 / 90°, 760 ms, delay 300, `(.3, .6, .3, 1)` |

The keyframes are `keyframes {}` specs on `Animatable`s started in one `LaunchedEffect(pressToken)`, all finite; the ripple composable is in the tree only while a token is live. Haptic: `rememberTapHaptics()(TapHaptic.SELECTION)` on press (one mechanism, `apps/android/CLAUDE.md`). Open after `OPEN_DELAY_MS 360`; a card or entry press opens at once and plays its stone's press. Reduced motion (`rememberReduceMotion()`): no waves, no sweep; rings stroke in `onSurfaceVariant` and the sheen holds at .9 for 900 ms; open immediately. The contents row scrolls (`animateScrollToItem`, centred, instant under reduced motion), then presses after 620 ms without opening.

## The feed

Everything follows the rule set's phone numbers in dp: `spineX 40`, `colX 82`, `padR 18`, day padding 58 / 34, gap 38 between pebbles, stone anchor 31 for a card and `0.6 × name size` for an entry, hit area the stone plus 10 dp, seam 8 dp tall with the two tooth periods, day label 46 × 52 at −14, numeral 25 sp at weight 330.

- **`SpineGround`:** a `Canvas` the height of the day sections, drawing the six grooves of the 36 × 240 tile (the mock's path data, meandering in phase by 1.15 px) with a 0.75 px shadow line (`onSurface` @ .20 in Paper, black @ .34 in Night) and a 0.9 px-offset lit line (white @ .55 / `#DCE2EA` @ .075), tiled; masked with a horizontal gradient `BlendMode.DstIn` from full at `colX − 6` to 22 % at `colX + 110`. Drawn once into a tile bitmap and repeated with a `ShaderBrush(ImageShader(REPEAT))`.
- **`DaySeam`:** three `drawRect`s with linear and repeating gradients (`ShaderBrush` over a 1-px-wide bitmap for the teeth periods), and the label box over it with the ground colour.
- **`SpineCard`:** `u = min(cardWidth / 70, 4.4.dp)`. Width `min(column, (cap − 123) × ratio + 95)` with the 180 dp floor, caps 330 / 370 / 400 by intensity. Photo in a `1.8u` mat on the tinted field (`primary` mixed `tintK` into the stock; pattern by valence drawn with gradients: conic rays, radial ripples, elliptical contours, at `patK`), `ContentScale.Fit`, never cropped. Index (day numeral `5.3u` at 600, weekday small caps `1.8u` at 650, seal `4.4u`), rail (vertical: time | emotion in small caps, emotion ink), title (Ysabeau `4.4u`, 600 / 480 / 360-italic by valence), souls strip. Printed border inset `1.6u`, radius `1.3u`. Card radius `shapes.medium`; painted edge: a `drawBehind` rect of thickness 2 / 3.5 / 5.5 in the valence edge colour (Paper: `primary` / `secondary` / `dark`; Night: `secondary` / `primary` / `shaded`), plus the card shadow (`shadow(elevation)` is wrong here; use two `drawBehind` blurred rects with the mock's offsets and alphas). A 1 px leader from the line to the card at the anchor.
- **`SpineEntry`:** name at 19 / 24 / 31 sp, weight 600 / 460 / 340-italic by valence, line height 1.12, tracking −0.012 em, max 22 ch; meta 8 dp below (time in lining tabular 13 sp at 560 in ink; emotion small caps 12 sp at 600, 0.12 em, emotion ink); people 6 dp below, 15 sp, ink-2.
- **`PeopleLine`:** the rule set's table (0 none; 1 A; 2 A & B; 3 A, B & C; n ≥ 4 A, B & *{n−2 in words}* others, then up to five glyph marks, then `+k`), ordered by the persons-of-the-week order. Built as an `AnnotatedString` with `InlineTextContent` for the glyphs.
- **Reading day:** derived from `LazyListState`: the last day header whose item offset is above 35 % of the viewport. The letter in the persons header and the contents rows of that day turn ink.

## The opening

- **`WeekMasthead`:** small caps 11.5 sp: "week 40" as a `TextButton`-less clickable (48 dp target) on the start side when a previous week exists; the week's ISO number in ink on the end side.
- **`WeekOpening`:** kicker (small caps 12 sp, 600, 0.2 em, ink-2), title (Ysabeau 40 sp, weight 300, line height 1.02, tracking −0.026 em, `lnum`, balanced through `LineBreak.Heading`), summary (italic 380, 18 sp, ink-2). Words from `WeekWords` and `NumberWords`.
- **`PersonsOfWeek`:** label (small caps 11.5 sp, 0.2 em, ink-2), header letters (9.5 sp small caps, ink-3; the reading day's in ink) right-aligned over the dots column, rows 44 dp min: `GlyphView` 24 dp, name 15 sp small caps at 600 with 0.1 em, dots 7 dp with 5 dp gaps (filled: ink; empty: 1 dp inset ring in ink-3 at .7). After eight rows, a row "and *N* others" that expands.
- **`WeekContents`:** rows 42 dp min, columns 40 / 36 / fill / auto: the day (short weekday, lowercase, trailing period stripped, small caps 10.5 sp, 0.18 em, only on a day's first row, with a rule above), the seal, the name (16.5 sp, weight 620 / 470 / 400 by intensity, lowlight italic 350), the time (12.5 sp, tabular, ink-3). The reading day's rows get a 5 dp ink dot at −12.
- **`WeekMouth`:** the line's last 26 dp, a three-stone cairn in `outline` strokes on the ground (a `Canvas` of the three flat outlines, 14 / 19 / 25 dp), "the 41st week ends here" (small caps 12 sp), "The 42nd begins on Monday the 12th." (italic 16 sp, ink-2), "week 42" link when a next week exists.

## Chrome

- **`WeekRoll`:** keeps its `LazyRow`, centring and a11y label; each cell becomes the ISO numeral in Ysabeau small caps (`SpineTypography.rollNumeral`), ink-2, the focused one in ink with a 1 dp underline in `onSurface`; `cairn_static.xml` goes with it (the roll is its only user; the `apps/android/CLAUDE.md` "week-roll cairn" bullet goes in Part 6). Cell width 56 dp, height 44 dp.
- `WeekHeader` and its formatting are deleted. `PathContent` is roll, then the pager, with `readableWidth()` on the column. The FAB is unchanged.
- The `HorizontalPager` keeps `beyondViewportPageCount = 0`; a neighbour week's stones are painted when it comes into view.

## Tokens (`core/designsystem`)

**`SpineColors`** (`@Immutable data class`, built once per theme by `SpineColors.from(scheme, dark)` in `PebblesTheme()` and read through `PebblesTheme.spine.colors`; `rememberSpineColors()` is the same object): `ground = surface`, `stock = surfaceContainerLowest / surfaceContainerLow (Night)`, `halo = surfaceContainerLow`, `ink = onSurface`, `ink2 = onSurfaceVariant`, `ink3 = outline`, `rule`, `cardRule`, `line`, `seam`, `teeth`, `ripple` as `onSurface` at the rule set's alphas, `ring`, `shadow` + `shadowAlpha`, `tintK .09 / .13`, `patK .34 / .40`, the night stone `ColorMatrix` filters, and `spot(palette)` / `deep(palette)` / `edge(palette, polarity)` as functions of a palette (these three and the night stone filters ship with `StoneTones` in Part 3, since they need the palette type; `SpineColors` itself is palette-free). Emotion palettes are never scheme roles (#853).

**`SpineTypography`** (`@Immutable`, `PebblesTheme.spine.type`): every text rank above as a `TextStyle` on `YsabeauFamily`: `kicker`, `title`, `summary`, `sectionLabel`, `masthead`, `personName`, `dayLetter`, `contentsDay`, `contentsName(intensity, valence)`, `contentsTime`, `entryName(intensity, valence)`, `entryTime`, `entryEmotion`, `people`, `soulName`, `cardIndexNumeral(u)`, `cardIndexDay(u)`, `cardRail(u)`, `cardTitle(u, valence)`, `cardSoul(u)`, `dayNumeral`, `dayWeekday`, `mouthLabel`, `mouthLine`, `rollNumeral`. Small caps are `fontFeatureSettings = "smcp"` (with `"c2sc"` where the source is uppercase), lining tabular figures `"lnum, tnum"`, proportional lining `"lnum, pnum"`. Sizes in `sp`; the card ranks take `u` in dp and return the style with `fontSize = (k × u).value.sp` computed from the density, which is a computed size, not a literal `.copy(fontSize = …)` in `features/`.

Both objects are the only places the Spine's numbers live; `ThemeLiteralsTest` keeps forbidding them in `features/` and `core/ui`. Corners: `shapes.medium` for the card; the day label's bottom corners `shapes.medium.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)`; computed radii (`1.3u`) are drawn, not shaped.

## i18n

`res/values/strings.xml` and `values-fr/strings.xml` gain the `path.spine.*` set from the web, as `path_spine_*` (`kicker`, `title_same_month`, `title_cross_month`, `summary_*` as plurals, `persons`, `persons_more`, `contents`, `with`, `others`, `still`, `untitled`, `photo_unavailable`, `week_ends`, `next_begins`, `prev_week`, `next_week`, `day_dots_a11y`, `stone_a11y`, `card_a11y`, `blank_a11y`, nine `stone_{size}_{polarity}`, `glance_a11y`, `feed_a11y`). Android has no ICU `select` in resources: the number words come from `NumberWords`, the plural forms from `<plurals>`, the rest from `String.format`. `LocalizationParityTest` enforces EN/FR parity as today. Weekday letters (`TextStyle.NARROW`), short weekdays and month names come from `java.time.format` in the active locale. `path_week_cell_a11y` and the empty-week strings stay; `path_week_previous` / `path_week_next` move to the masthead and mouth links.

## Accessibility

- The stone: `Role.Button`, `contentDescription` = `stone_a11y` (name, date, time, stone kind, emotion, people, "opens the pebble"); 48 dp minimum touch target through `minimumInteractiveComponentSize()` on the hit box (the stone plus 10 dp is smaller for the small stone: the target grows invisibly).
- The card or entry: one `clickable` with merged semantics carrying the same description; the index and rail are `clearAndSetSemantics {}`.
- Contents rows: buttons named by the pebble, action "scrolls to and presses".
- Day dots: `contentDescription` from `dotDays` ("On four days: Monday 5, …"); the letter header is decorative.
- The week: `liveRegion = LiveRegionMode.Polite` on the title so a page change announces the week.
- Colour is never the sole signal: valence is also the name's cut; intensity also the size and rank.
- Contrast as the rule set: ink-2 on paper ≥ 4.5:1 by the scheme test; ink-3 only for the time and empty dots, which are repeated in text or in the stone's description.

## Performance and battery

- A stone paints once per key; the cache holds a week and its neighbours. A still Path does no drawing.
- Presses animate one stone's layers and one ripple; the bitmap is untouched (the dip is a `graphicsLayer` scale).
- Photos: Coil with `crossfade`, thumb placeholder from the memory cache, original at `size(Size.ORIGINAL)` capped by the field width.
- The rake is one tiled bitmap per density and scheme.
- Budget on the Pixel 7 AVD: first frame of a 20-pebble week ≤ 60 ms of main-thread drawing after the bitmaps exist; 60 fps on scroll; a press stays at 60 fps.

## Testing

JVM (`testDebugUnitTest`), one class per port, each replaying the web test's vectors:

| Test | Asserts |
|---|---|
| `SpineWeekBuilderTest` | `spine-week.test.ts`: seven days Monday-first, oldest first in past and current weeks, still days, future days omitted, reading order |
| `PersonsOfWeekTest` | `persons-of-week.test.ts`: dots across a DST change, 23:59 Sunday, ordering ties, the cap |
| `PeopleLineTest` | n = 0, 1, 2, 3, 4, 7, 14; marks ≤ 5; `+k`; order |
| `EntryTypeTest`, `CardSizeTest` | all 3 × 3 combinations; the sizing table rows; the 180 floor |
| `NumberWordsTest` | EN/FR at 1, 21, 31, 41, 53; feminine forms |
| `StoneRecipeTest` | deterministic per seed; counts (facets 5 + 1, glitter 10, specks 14, pits 9); the web's expected coordinates for its two fixture seeds, verbatim |
| `StoneCarvingsTest` | the vein and fossil counts above; `large-neutral` outline third |
| `StoneGeometryTest` | glyph zones equal `StoneComposer`'s; ring matrices for small neutral equal the web's rounded values |
| `PathMomentDecodingTest` | a verbatim `path_moments` row with `souls` present, absent, and empty; with `glyph: null` (the foreign-payload rule) |
| `SnapAspectCacheTest` | caches by prefix; timeout yields null once and does not retry within the session |

Screenshot previews (`@PreviewTest`, re-baselined through the label): `StoneScreenshots` gains the nine stones × Paper / Night / touched; `SpineWeekScreenshots` renders the fixture week (Mon 5 to Sun 11 Oct 2026, eleven pebbles, five souls, three photos with primed ratios, plus a 14-soul pebble, an empty day, a glyph-less pebble and a blank moment) at phone width, Paper and Night, plus the large-font and French variants; `PathScreenScreenshots` re-baselines. Robolectric: the existing Path journey (open a pebble from the timeline) keeps passing through `JourneyTags.PATH_PEBBLE_ROW` moved onto `SpinePebble`.

Backend: `verify-moments.ts` (#1121) runs in CI on the migration.

Manual: Pixel 8, wallpaper colours off and on, Paper and Night, a real week with photos of three ratios; a 14-soul pebble; TalkBack over a stone, a card and the contents.

## Delivery: stacked parts (`gh stack`)

| # | Branch / PR | Contents | Lab Note |
|---|---|---|---|
| 1 | `feat/1121-path-moments-souls` | the migration, `database.ts`, `verify-moments.ts` | `no-lab-note` |
| 2 | `feat/1122-spine-foundations` | `SpineTypography`, `SpineColors`, `NumberWords`, the seven logic objects with tests, strings EN/FR, `PathPebble.souls`, `soulsById` in the ViewModel, `hashUUID` (Android has no UUID hash today; port `lib/engine/seed.ts`) | `no-lab-note` |
| 3 | `feat/1122-lit-stone` | `StoneGeometry`, `StoneCarvings`, `StoneRecipe`, `StoneTones`, `StonePainter`, `StoneBitmapCache`, `LitStone`, `StoneSeal`, `StoneRipple`, `StoneMotion`, the stone gallery preview | `no-lab-note` |
| 4 | `feat/1122-spine-feed` | `SpineGround`, `DaySeam`, `SpinePebble`, `SpineEntry`, `SpineCard`, `PeopleLine`, `SnapAspectCache`, the press choreography, the feed preview | `no-lab-note` |
| 5 | `feat/1122-spine-opening` | `WeekMasthead`, `WeekOpening`, `PersonsOfWeek`, `WeekContents`, `WeekMouth`, `SpineEmptyWeek`, `SpineWeek`, reading-day spy, contents → press, the week preview | `no-lab-note` |
| 6 | `feat/1122-path-becomes-spine` | `PathContent` renders `SpineWeek`, `WeekRoll` restyle, deletions, fixtures, re-baseline, Arkaik, decision log, Lab Note (or `no-lab-note` if Arkaik coverage replaces it, per the maintainer's standing rule) | yes / per rule |

Each part lints, typechecks and passes `testDebugUnitTest` alone. Parts 2–5 are visible only in previews. Only Part 6 says `Resolves #1122`; Part 1 says `Resolves #1121`. Labels: Part 1 `feat`, `api`, `supabase`; Parts 2–6 `feat`, `ui`, `android`; milestone M64 · New Path. Stack rules: fetch and fast-forward before stack pushes, add `rebaseline-screenshots` alone, approve the held runs, rebase and re-run before merging (bot commits break linearity).

## Arkaik

Hosted map, MCP only. At Part 6: `V-timeline`'s Android platform status to `development`, and the six acceptances the web spec names (`AC-pebble-snap-framing`, `AC-pebble-unique-artwork`, `AC-path-row-emotion-tint`, `AC-pebble-emotion-palette-ink`, `AC-touch-feedback-and-haptics`, `AC-path-week-roll`) plus the two the web added (`AC-the-week-opens-with-who-was-there-and-what-happened`, `AC-a-photo-keeps-its-own-shape`) get `android: development` (send gherkin, values and platformStatuses back together: `update_node` replaces metadata wholesale). Never `live` by hand.

## Decision-log entry (Part 6)

"2026-10-18 — The Android Path becomes the Spine: a Compose port of the web stone over the iOS geometry, souls on `path_moments`, one column with the detail pane" (#1122, #1121). Extends 2026-10-13 (#1052). Records D1–D6.

## Risks

- **Blur fidelity.** `BlurMaskFilter` is a box-ish Gaussian; the web's `feGaussianBlur` σ maps to a radius of about `σ × 2.5`. Tune once on the gallery against the web specimen in Part 3, record the factor in `StoneGeometry`.
- **Radial focal point.** Compose gradients have no `fx/fy`; the nudged centre is close but not identical. If the gem's hot spot reads wrong, draw the ramp through a native `RadialGradient` on an `android.graphics` canvas, which also lacks a focus; the fallback is two stacked radial brushes.
- **Bitmap memory.** 64 stones at ≤ 54 dp × 3× density is under 2 MB. Fine; keep the cap.
- **Souls payload size.** A 14-soul pebble adds about 1 KB to its row; the timeline loads whole, and the roll already does. Acceptable.
- **Type features on old renderers.** `smcp` through `fontFeatureSettings` works on API 33's text stack; the screenshot gate (layoutlib) renders it too. If a preview shows full caps, the fallback is `ysabeau_sc.ttf` from `google/fonts`, bundled like the others.
- **The 20-pebble week on the Pixel 7 AVD.** Measure in Part 4 before Part 6; if the first frame misses the budget, paint neighbour weeks' stones on a background dispatcher into the cache before they page in.
