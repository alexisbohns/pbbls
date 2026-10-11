# Web Path: the Spine — design

- **Issue:** [#1052 — The web Path becomes the Spine, a week set like a book's margin](https://github.com/alexisbohns/pbbls/issues/1052)
- **Date:** 2026-10-11
- **Scope:** `apps/web` only. One provider projection change (two existing columns read, no new RPC). One recommended schema addition (snap dimensions), proposed to the moments/pebbles split rather than shipped here.
- **Size:** Large (replaces the Path's feed, stone and chrome; six stacked parts).
- **Lineage / references:**
  - Design: the final iteration, `final/index.html` and `final/DIRECTION.md` in the private hub artifact https://claude.ai/artifact/YWnrBsWLbbkTTkZ3ra561z. It is the visual reference, built as plain HTML/CSS/SVG. Its parents in the same hub are `spine/` (round 2, the base) and `stream/` (lit stones, seven-dot strip).
  - Replaces: `2026-08-22-path-polaroid-sandbox-design.md` (#720, the polaroid wall). Keeps the week mechanics of `2026-05-14-web-week-path-view-design.md` (#421).
  - Stone contract: `2026-09-27-ios-stone-material-lab-design.md` (#974) and `apps/ios/Pebbles/Features/Path/Stone/`.
  - Reconciled with: `2026-10-11-moments-and-pebbles-split-design.md` (same day; §Moments split below).

## Summary

The web Path stops being a wall of polaroids and becomes **the Spine**. Each week opens like a gathering: a title written in words, the persons of the week with a seven-dot strip of the days each one appears, and a contents list. Below it, one line runs down the left margin over raked paper. Each pebble's **lit stone** sits on that line (lava, river pebble or gem by valence, emotion palette, glyph carved). Each day starts at a hairline seam with the date set in the margin. A pebble with a photo hangs a **Deck card** off its stone. The card is sized to the photo's own ratio and never crops it. A pebble without a photo is **set as type**, with no box. Pressing a stone dips it, sends three stone-shaped ripples across the ground and runs a glint over it. From 900 px the week becomes a spread: the opening and contents stay pinned on the left page, the line runs down the gutter, and the entries fill the right page.

## Background and motivation

The maintainer's verdict on the current surfaces (2026-10-11, FACTS §2):

> I'm very unsatisfied with the polaroid display of the web app, and very unsatisfied with the rows display in the mobile apps. […] What I don't like with the rows is that it's not enough "precious" or "collectable", the polaroids are far better. But the polaroids look cheap, something is very amateur in the design: between interface (soulless) and skeumorphic (unrealistic). The pebbles are ugly on web in comparison with iOS […]

Two rounds of directions followed (Vitrine, Riverbed, Journal, Deck, Constellation, then Stream vs Spine). The verdict on round 2 (FACTS §7):

> The Spine is truly a masterclass. I think I'll only pick the realistic pebbles stones render + the 7 circles for the week in the persons of the week, both from the Stream.

The final iteration is the Spine plus those two Stream pieces, with two polish decisions: (a) the card keeps one corner index and one rail, so the upside-down twins and the stone pip are gone; (b) the ground is raked paper, not wood. This spec turns that mock into a web implementation plan.

## Locked decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | The Spine replaces both `PathWall` (polaroids) and the `list` display (`PathPebbleRow`). `WeekPath`'s `display` prop goes. | One Path. The list was kept in #720 only "until a setting exists", and the Spine supersedes both. |
| D2 | **The mock wins on look. The repo wins on construction.** Tokens go in `globals.css`. Components use Tailwind utilities, plus a few `@utility` blocks for multi-gradient surfaces. There are no new CSS files and no mock class names. | `apps/web/CLAUDE.md`: "Do not add CSS files". Tailwind 4's `@utility` keeps the seam and ground readable without a stylesheet. |
| D3 | **Stone geometry follows iOS, not the mock.** The body is the backdrop silhouette (`getOutline` + `wobbleBackdrop`). The carving (veins, fossil, glyph) is the engine seed scaled by `pebbleScale × 1.22`. The engine outline is not drawn. | The mock's body is the engine seed's *outline*, an inner contour. The 2026-09-27 decision makes "the tone slots and the layer order" the port contract, and the three surfaces must share one geometry. The mock's on-screen sizes already match the backdrop aspect ratios (34/27 ≈ 337/270, 54/64.4 ≈ 335/400). |
| D4 | **Tone slots follow the artifact (the final mock), not iOS `StoneTones`, for now.** The body ramp, carving ink and lip colours are read from the mock's eight gradients (table in §Lit stone); web does not import the iOS tables. | Maintainer decision, 2026-10-11: keep the artifact tones for now; the whole palette may be redefined later, and iOS will then be re-tuned against the same tables rather than web against iOS. The mock's ramp is how SVG fakes the Lambert term. |
| D5 | **The stone is SVG, not WebGL.** It uses one shared sprite (nine body/carving symbols and the colourless filters) and a per-stone gradient with palette CSS variables. | See §Lit stone. It is cheap, still, themeable in CSS and needs no context limit. A WebGL bake stays an option (open question). |
| D6 | **`render_svg` is never read.** The glyph comes from the resolved glyph's strokes (`v_pebbles_full.glyph`). | The moments split (D6 there) drops `render_svg`. A flattened composite can't be carved with a lip anyway. |
| D7 | The lit stone appears only where it can be pressed (the margin line). Anything printed uses the flat **seal**: the contents list, and later a shared card or Cairn export. | DIRECTION §1: "at 20 px, lit beads would turn a printed table into a sweet jar." |
| D8 | Pressing a stone plays the ripple and glint, then opens `PebblePeek` 360 ms later (immediately with reduced motion). Pressing a card or entry opens the peek and plays its stone's ripple at the same time. A contents link scrolls to the stone and ripples it, without opening it. | The mock's stone only ripples, but the app needs an open target. Making every open ripple keeps the ripple meaningful rather than decorative. |
| D9 | Days read **oldest first in every week**, including the current one. Past days with no pebble collapse to a seam marked *still*. The current week's future days are not drawn. | It is a book. `buildWeekRollEntries` keeps its newest-first current-week sort for its other consumers. The Spine sorts on its own. |
| D10 | Paper is `:root`, Night is `.dark`. The `--spine-*` tokens are neutral and **do not follow the colour worlds**. The mock's edition toggle is dropped. | Stone palettes were tuned on neutral grounds. The app already follows the system/theme setting (`AC-app-follows-system-appearance`). |
| D11 | The week roll stays as navigation across weeks. The opening replaces any week header. The dock stays. On wide screens both move into the verso. | §Chrome. |
| D12 | The first snap only (sort_order 0). The card never crops it. | The design has one photo per card, and a carousel is out of scope. |
| D14 | **A card starts square and grows to the photo's ratio.** Until the ratio is known, the field is 1:1 (`object-fit: contain`, never cropped); when the probe lands, the field's height transitions to the real ratio over 240 ms (`ease-out`), instantly with reduced motion. No 4:3 reservation. | Maintainer decision, 2026-10-11: "start square and expand smoothly to prevent glitch". A square is the one shape no format looks wrong in while it waits, and a transition reads as the card settling rather than as a jump. |
| D13 | Fonts: add `@fontsource-variable/ysabeau-sc` and `@fontsource-variable/ysabeau-office` (both 5.3.0, latin), imported in `app/path/layout.tsx`. Caveat (`--font-hand`) is no longer used on the Path. | The mock sets small caps and lining figures with those two families. Faked small caps would look amateur, which is the verdict's word. |

## Non-goals

- iOS and Android implementations. The shared spec is §Rule set, and each surface gets its own issue.
- Tilt or a moving light on web. Multiple photos per card. Cards leaving the page (share, export, weekly Cairn), which get their pip back.
- The web Capture and Complete flows from the moments split, and uncompleted moments (§Moments split).
- A soul "role" line ("partner", "son, six"). `souls` has no such column and the maintainer dropped it on 2026-10-11; the persons-of-the-week row is glyph, name and the day dots.

## Architecture

### Component map

New, under `apps/web/components/path/spine/`:

| Component | Role | Data |
|---|---|---|
| `SpineWeek` | The week page: `<article>` with verso (opening) and recto (feed), grid at ≥ 900 px; scroll container; scroll-spy | `WeekRollEntry`, `soulMap`, `markMap` |
| `WeekOpening` | Kicker, title in words, summary, prev-week link | entry + `number-words` |
| `PersonsOfWeek` | Persons list with glyph, small-caps name, 7-dot strip, weekday header | `personsOfWeek()` |
| `WeekContents` | `<ol>` of day / seal / name / time links | `spineWeek()` |
| `SpineFeed` | Ground layer, spine line, days | `spineWeek()` |
| `DaySeam` | Seam and margin date; *still* when empty | day |
| `SpinePebble` | One pebble: stone button, ripple host, then `SpineCard` or `SpineEntry` | pebble, palette, glyph, souls |
| `SpineEntry` | The no-photo entry: name, meta, people line | — |
| `SpineCard` | The Deck card: index, rail, field and photo, title, souls strip | `useSnapAspect` |
| `PeopleLine` | "*with* [g] A & [g] B" and the overflow rule (entry and card variants) | `peopleLine()` |
| `WeekMouth` | Small cairn, "the week ends here", next-week link; current week: "carve a pebble" link to `/record` | — |

New, under `apps/web/components/pebble/stone/`:

| Component | Role |
|---|---|
| `StoneSprite` | Rendered once per Path, hidden. Holds nine `<symbol>`s (wobbled body, veins, fossil per size × polarity), their `clipPath`s, the colourless filters (`near` σ5, `far` σ15, `ao` σ7, `soft` σ12, `glow` σ4, in viewBox units) and the glint gradients. |
| `LitStone` | The pressable stone (§Lit stone). Takes `size`, `polarity`, a `palette`, a `glyph` and a `seed`. |
| `StoneSeal` | The flat print: relief, line or engraving. |

New pure modules, under `apps/web/lib/utils/spine/` unless noted: `card-size.ts`, `entry-type.ts`, `people-line.ts`, `persons-of-week.ts`, `spine-week.ts`, `stone-recipe.ts`. Under `apps/web/lib/i18n/`: `number-words.ts`. Under `apps/web/lib/config/`: `stone-carvings.ts`, generated verbatim from `docs/seeds/shape-seeds/*.svg` like `valence-art.ts`; `stone-geometry.ts`, the ring and halo transforms and glyph zones; `stone-tones.ts`. Under `apps/web/lib/data/`: `useSnapAspect.ts`.

Modified:

- `PathScreen.tsx`: drop `max-w-md`, own the phone/wide split, place roll and dock.
- `WeekPager.tsx`: unchanged mechanics. It renders `SpineWeek` instead of `WeekPath` (±1 neighbours kept).
- `WeekRoll`, `WeekRollCairn`, `PathBottomBar`: restyled to the paper tokens, with the same behaviour.
- `PathEmptyState`: restyled. It is still the fresh-week invitation.
- `useEmotionPalettes.ts`: also select `shaded_color, dark_color`. This closes the #720 follow-up "`dark_color` unreachable in production".
- `supabase-provider.ts` and `lib/types.ts`: `Pebble` gains `glyph: Mark | null`, mapped from `v_pebbles_full.glyph`, and `name` is parsed tolerantly (§Moments split).
- `globals.css`: the `--spine-*` tokens, `@utility spine-ground`, `spine-seam` and `spine-field-*`, and `body.path-route` background set to `--spine-ground`.
- `app/path/layout.tsx` (new, thin): font imports. Check the nested-layout CSS import rules in `node_modules/next/dist/docs/` first (AGENTS.md).

Deleted, each with what replaces it:

| Deleted | Replaced by |
|---|---|
| `PathWall.tsx`, `PathPolaroid.tsx` | `SpineFeed`, `SpinePebble`, `SpineCard`, `SpineEntry` |
| `PathStone.tsx`, the `.path-stone-fill` rules | `LitStone` and `StoneSeal` |
| `WeekPath.tsx`, `PathPebbleRow.tsx` and their design specimens | `SpineWeek` (specimen "Path · Spine") |
| `lib/utils/path-layout.ts` and its test | `spine-week.ts` and its test (§Testing) |
| `lib/utils/polaroid-chaos.ts` (already unused) | — |
| `app/sandbox/path/`, `components/sandbox/SandboxPathScreen.tsx`, `SandboxToolbar.tsx`, `stone-sizes.ts`, and `lib/seed/sandbox-pebbles.ts` if no other consumer | A fixture week on `/sandbox/design` (§Testing) |

`PebbleCard` (souls and collections lists), `QuickPebbleEditor` (`/record`) and `PebblePeek` are untouched.

### Data: what each component needs, and from where

| Need | Source | Note |
|---|---|---|
| Pebbles of the week | `usePebbles()` → `buildWeekRollEntries` | Re-sorted ascending by `spineWeek()`. |
| Souls per pebble, soul glyphs | `useSouls()`, `useMarks()`, `useLookupMaps()` | As `WeekPath` does today. `SoulGlyphThumbnail` keeps its dashed fallback. |
| Pebble glyph strokes | `pebble.glyph` (new projection of `v_pebbles_full.glyph`) | `markMap` holds only the user's own and entitled glyphs. Domain-default glyphs are system glyphs and may be missing from it. |
| Palette (five tones) | `useEmotionPalettes()` + `shaded_color`, `dark_color` | Single-table read, so no RPC. |
| Photo URL | `pebble.instants[0]` | Signed `original.jpg`, 1 h TTL. |
| Photo ratio | `useSnapAspect(snap)` | See below. |

No new RPC is needed.

**The snap ratio.** The web does not know a snap's ratio before load: `snaps` stores `id, storage_path, sort_order` only, and a HEAD returns no dimensions.

- **Durable fix (recommended, not shipped here):** nullable `snaps.width/height`. Every client knows them at write time (`process-pebble-image.ts`, iOS `ImagePipeline`, Android). Carry them in `save_moment`'s snap items and the read views. Backfill by reading JPEG SOF markers with a service-role script. It belongs in the moments split's Part 1, which already rewrites `snaps`.
- **Ships now, and stays as the fallback for null rows:** `useSnapAspect` signs `thumb.jpg` in the same `createSignedUrls` batch (≤ 300 KB, same ratio), decodes it with `new Image()` + `decode()`, and caches the ratio by snap id. It probes the focused week on mount and times out after 4 s with a `console.warn`.
- Layout-on-load from the full image is rejected: slowest to settle, largest bytes.

**Layout shift.** Until the ratio is known, the field is square (D14). When the probe lands, the field's height transitions to the photo's ratio over 240 ms (instantly under reduced motion), which is one animated settle per card per session rather than a jump. `overflow-anchor: auto` absorbs that on Chrome and Firefox, not reliably on Safari. A contents jump re-scrolls once if its target moved within 800 ms.

## The rule set (from `final/DIRECTION.md` and `final/index.html`)

Phone means < 900 px, wide means ≥ 900 px, and ≥ 1240 px is noted where it differs.

### Page geometry

| Token | Phone | Wide | Meaning |
|---|---|---|---|
| `--spine-x` | 40 px | 62 px | The line's x in the recto |
| `--col-x` | 82 px | 116 px | Content column start |
| `--pad-r` | 18 px | 48 px | Right padding |
| Day padding | 58 / 34 px | 70 / 44 px | Top / bottom |
| Gap between pebbles | 38 px | 48 px | |
| Spread grid | — | `minmax(320px,380px) minmax(0,740px)`, max 1200 px, centred; ≥ 1240: `400px 760px`, gap 24 px | |

The line is 1 px `--spine-line` at `--spine-x`, running the full length of each day section.

### Stone sizes

| Intensity | Phone w × h | Wide w × h | Rings | Card cap phone / wide | Card edge |
|---|---|---|---|---|---|
| 1 small | 34 × 27.2 | 40 × 32 | 1 | 330 / 340 px | 2 px |
| 2 medium | 44 × 44 | 52 × 52 | 2 | 370 / 400 px | 3.5 px |
| 3 large | 54 × 64.4 | 58 × 69.2 | 3 | 400 / 460 px | 5.5 px |

Nothing exceeds 70 px, and the glyph carve is a constant 1.25 px. The stone's centre sits on the line at `--anchor`. That is 31 px for a card, and `0.6 × name font-size` for an entry, so the name's first line is centred on the stone. The hit area is the stone plus 10 px all round. The rings are the body silhouette scaled about its centre in about 4.5–5.5 px steps. The halo (the clearing, filled `--spine-halo`) sits about 0.85 of a step outside the last ring. Lift the nine ring and halo matrices verbatim from the mock's markup into `stone-geometry.ts`.

### Card sizing

The card chrome is fixed in card units: `u = min(cardWidth / 70, 4.4px)` (a container query, `100cqw / 70`). The photo sits uncropped in a `1.8u` mat. The formula treats the chrome as 123 px vertical and 95 px horizontal:

- `H = 123 + photoWidth / ratio`
- **`W = min(column, (cap − 123) × ratio + 95)`**, expressed in CSS exactly as the mock does: `width: min(100%, calc((var(--cap) - 123px) * var(--ar) + 95px))`. No JavaScript is needed once `--ar` is known.
- Floor: if the formula gives < 180 px (ratio < ~0.5 at a small cap), use 180 px and let the card exceed its cap. A title needs that width.

Computed values, width × approximate height in px (phone column 290 at 390 px; wide column 576, 596 at ≥ 1240):

| Ratio | Phone i1 / i2 / i3 | Wide i1 / i2 / i3 |
|---|---|---|
| 16:9 | 290×233 all | 481×340 / 576×394 / 576×394 |
| 3:2 | 290×253 all | 420×340 / 510×400 / 576×444 |
| 1:1 | 290×318 all | 312×340 / 372×400 / 432×460 |
| 2:3 | 233×330 / 260×370 / 280×400 | 240×340 / 280×400 / 320×460 |
| 9:16 | 211×330 / 234×370 / 251×400 | 217×340 / 251×400 / 285×460 |

The formula is exact only at `u = 4.4` (card ≥ 308 px). On a phone (u ≈ 4.14) and with two-line titles, the real height drifts by up to about 10 px. That is accepted, as in the mock. **The photo on a 390 px phone is about 200 px wide** (290 − 21.6u). See the open questions.

**Card anatomy:**

- Printed border inset `1.6u`, 1 px `--spine-card-rule`, radius `1.3u`. Padding `4.6u 9u 6.2u`.
- **Index**, top left: day numeral (Ysabeau Office 600, `5.3u`) over weekday (Ysabeau SC 650, `1.8u`). No pip.
- **Rail**, top right, vertical: time | emotion (small caps, emotion ink). No upside-down twins.
- **Field**: tint `color-mix(spot 9%, stock)` (13% Night), with a pattern at 34% (40% Night) by valence:
  - high: conic rays from the stone's corner (`from 90deg at -6% -14%`, 0.3° on 2.2°)
  - neutral: radial ripples (`at -4% -10%`, 0.8 px every 6.3 px)
  - low: elliptical contours from the bottom right (`150% 110% at 112% 122%`)
- Title, centred: Ysabeau `4.4u`, weight 600 / 480 / 360-italic by valence.
- Souls strip, centred: glyph `4u` and small-caps name `2.5u`.
- Card radius 11 px. Painted edge `box-shadow: 0 var(--t) 0 0 var(--edge)`, plus `--spine-card-shadow`.
- **Edge colour.** Paper: high `primary`, neutral `secondary`, low `dark`. Night: high `secondary`, neutral `primary`, low `shaded`.
- A 1 px leader in `--spine-line` runs from the line to the card at `--anchor`.

### No-photo entry

No box. All three lines sit in the content column.

| | Intensity 1 | 2 | 3 |
|---|---|---|---|
| Name size, phone / wide | 19 / 21 px | 24 / 27 px | 31 / 35 px |

| Valence | Name cut |
|---|---|
| +1 | 600 |
| 0 | 460 |
| −1 | 340 italic, tracking −0.004em |

- **Name**: line-height 1.12, tracking −0.012em, `text-wrap: balance`, max 22ch.
- **Meta**, 8 px below: time (Ysabeau Office 560, 13 px, lining tabular, ink) · emotion (Ysabeau SC 600, 12 px, 0.12em, emotion ink).
- **People**, 6 px below, 15 px, ink-2: "*with* [glyph] NAME & [glyph] NAME".
- A solitary pebble has no people line. It is not marked "alone".

### Many people (1–14 and beyond), `peopleLine(souls)`

| Souls | Named | Then |
|---|---|---|
| 0 | — | no line |
| 1 | A | — |
| 2 | A & B | — |
| 3 | A, B & C | — |
| n ≥ 4 | A, B | "& *{n−2 in words}* others", then up to five glyph marks of the others, then `+{n−2−5}` if positive |

Example at 14: "with MARA, ELIO *& twelve others* [5 marks] +7".

Order is the persons-of-the-week order (days present desc, then pebbles desc, then name by locale collation). The most present people are named, and the order is deterministic across surfaces. Cards follow the same rule, with the "with" kept visually hidden.

### Seam and ground

- **Seam**: one element, `@utility spine-seam`, 8 px tall:
  - a 1 px hairline in `--spine-seam`
  - teeth `repeating-linear-gradient(90deg, teeth 0 1px, transparent 1px 7px)`, 3.5 px tall, offset 2 px
  - a second period, 1 px every 19 px offset 4 px, 6.5 px tall
- **Day label** in the margin: a 46 px box (52 wide) centred on the line at top −14 px (−17). Numeral Ysabeau 330 at 25 px (30). Weekday small caps 10 px. Background ground plus grain, radius `0 0 10px 10px`.
- **Empty day**: the seam, the label and an italic *still* in ink-3 in the column, with no padding.
- **Ground**: `@utility spine-ground`, an absolute layer behind the recto. It tiles the 36 × 240 rake SVG data URI: six grooves 6 px apart, meandering in phase by 1.15 px, each a 0.75 px shadow line plus a 0.9 px-offset lit line. Its mask runs full strength to `col-x − 6px`, then down to 22% by `col-x + 110px` (wide: 20% by `+150px`).
- **Grain**: a 180 px `feTurbulence` tile (`baseFrequency .85`, two octaves) over the whole sheet at 10% Paper and 5% Night.
- Lift both data URIs verbatim from the mock into the token block.

### Ripple and glint (WAAPI, finite)

| Element | Keyframes | Duration / delay / easing |
|---|---|---|
| Stone (HTML wrapper) | scale 1 → 0.9 (26%) → 1.03 (60%) → 1 | 520 ms / 0 / `cubic-bezier(.3,.7,.2,1)` |
| Rings | scale 1 → 1.08 (35%) → 1 | 1150 ms / 110 ms / `cubic-bezier(.2,.7,.2,1)` |
| Wave k = 0, 1, 2 (stone-shaped) | scale 1.15, op 0 → 1.45, op .85 (7%) → `REACH[i] − 0.5k`, op 0; `REACH = {1: 7.2, 2: 5.6, 3: 4.4}` stone widths | `1650 + 170k` ms / `120 + 230k` ms / `cubic-bezier(.12,.55,.3,1)` |
| Glint sweep (clipped to body) | translateX 0 → 1.6 × viewBox width | 900 ms / 140 ms / `cubic-bezier(.37,0,.63,1)` |
| Spark (four-point) | scale 0, rot 0 → scale 1, rot 40°, op 1 (35%) → scale 0, rot 90° | 760 ms / 300 ms / `cubic-bezier(.3,.6,.3,1)` |

- Glint strength by material: gem 1, pebble 0.62, lava 0.34.
- Haptic: `useHaptics().vibrate(6)`.
- **Reduced motion**: no waves and no sweep. The rings stroke in ink-2 and a still sheen at 0.9 holds for 900 ms.
- The ripple SVG (three `<use>` of the body symbol) is **mounted on press and unmounted on the last `finish`**. It is never resident.

### Persons of the week, `personsOfWeek(pebbles, weekStart)`

- **Rows**: every soul appearing in the week, ordered as in the people-line rule, each with `days: boolean[7]` (Monday first, local day of `happened_at`).
- **Row content**: glyph 24 px, small-caps name 15 px, and the strip of seven 7 px dots with 5 px gaps (79 px). A filled dot is ink; an empty one is an inset 1 px ring in ink-3 at 0.7.
- **Header**, right-aligned on the label line in the same column: weekday letters from `Intl.DateTimeFormat(locale, { weekday: "narrow" })`, 9.5 px small caps ink-3. The letter of the day being read turns ink.
- **Cap**: eight rows, then a `<details>` row "and *N* others" that expands the rest. Fourteen-person pebbles must not push the contents off the first screen.

### Contents

- An `<ol>`, one row per pebble. Grid `40px 36px 1fr auto`, min-height 42 px (36 wide).
- **Day**: short weekday, small caps, only on the first row of a day, with a rule above. Lowercase, trailing period stripped (FR "lun.").
- **Seal**: 20 / 24 / 27 px by intensity.
- **Name**: 16.5 px (15.5 wide), weight 620 / 470 / 400 by intensity; lowlight italic 350.
- **Time**: Ysabeau Office 12.5 px, tabular, ink-3.
- The rows of the day being read get a 5 px ink dot at −12 px.
- **Seal recipe**: relief (highlight, solid spot, glyph knocked out to the ground), line (neutral, 15% tint, rim and glyph line), engraving (lowlight, 9% wash, hatch clipped to the body, glyph with a ground halo). Spot is `primary` on Paper and `secondary` on Night; lowlight deep is `shaded` on Paper and `color-mix(secondary 80%, ground)` on Night.

### Type ranks of the opening

| Element | Phone | Wide |
|---|---|---|
| Kicker | Ysabeau SC 600, 12 px, 0.2em | same |
| Title | Ysabeau 300, 40 px, line 1.02, −0.026em, balanced | 44 px |
| Summary | italic 380, 18 px, ink-2 | same |
| Section labels | small caps 11.5 px, 0.2em, ink-2 | same |
| Gaps (top / persons / contents) | 46 / 40 / 40 px | 64 / 44 / 36 px |

## The lit stone on web

**iOS** (`StoneView`): contact shadow, then the backdrop body under a Metal `stone` effect (lava pits, river grain, gem Worley facets and glitter, lit by Lambert + Blinn from `StoneLight.rest`, top-left at 55°), then an edge line, the carving through `carve` (lip light and lip shadow sampled along the light), and a screen-blended sheen. The battery contract: no time input, Low Power draws body plus ink, and a still view does no GPU work.

**The mock**, pure SVG per stone, bottom to top:

1. halo and rings
2. two blurred offset copies of the body as shadow (≈ 7 px and 2 px down-right)
3. the body in a radial gradient (`cx .38 cy .3 r .9`, focus `.34 .24`)
4. clipped material:
   - gem: five facets around a pentagon, light vein strokes, ten glitter dots, two sparks
   - pebble: fossil as a light and a primary copy, a soft ellipse
   - lava: pits, a dark fossil, a glowing crack
5. an inner AO stroke (σ7)
6. the glyph as a lip copy, then ink
7. the glint group

It has no per-stone `feTurbulence` (grain is page-level) and bakes eight gradients by emotion. In Night: `brightness(.92) saturate(.96)`, lava `brightness(1.2)`.

**Assessment.** The mock reads as a stone at 34–58 px and paints once. It differs from iOS in body geometry (D3) and in having no moving light; its tone slots are kept as they are (D4). None of that matters on a still feed. Per-pixel lighting is what SVG lacks, and the feed does not need it.

**Options considered:**

| Construction | Fidelity | Cost | Verdict |
|---|---|---|---|
| SVG (gradients, facet polygons, blurred copies) | Good at feed size; static light | ~80 nodes and 4–5 small filters per stone; one raster on first paint | **Chosen** |
| CSS only (gradients + masks) | Cannot carve or facet | — | Rejected |
| WebGL port of `stone.metal` | iOS-exact, tilt-ready | A GLSL port (about 2–3 days); browsers cap live contexts (~16), so it must bake: one offscreen context renders each stone once to an `ImageBitmap` keyed by shape, emotion, polarity, glyph, theme and DPR | Deferred. The maintainer confirmed SVG on 2026-10-11 ("same render as the artifact is already a great step"). It stays the right tool for a future web read page with a moving light. |

**Construction.**

- **`StoneSprite`**, once per page, holds the nine body symbols (wobbled once per shape and cached at module level), the veins and fossils from `stone-carvings.ts`, and the filters. `<use>` shadow trees inherit custom properties, so symbol paints such as `fill: var(--d)` follow each stone. Paint servers do not: a gradient referenced by `url(#…)` resolves colours in its own tree.
- **Each `LitStone`** therefore declares its own `<radialGradient>` with a React `useId()` id, its stops written as `style={{ stopColor: "var(--p)" }}` and resolved from the stone's inline `--p --s --l --sh --d`.
- **Body ramp** (derived from the mock's eight gradients):

| Polarity | Stop 0 | Mid stop | Stop 1 |
|---|---|---|---|
| highlight | `color-mix(secondary 75%, white)` | `primary` @ .4 | `color-mix(primary 45%, dark)` |
| neutral | `color-mix(secondary 53%, white)` | `secondary` @ .48 (the mock's slot, D4) | `color-mix(secondary 50%, dark)` |
| lowlight | `color-mix(primary 85%, black)` | `color-mix(primary 35%, dark)` @ .5 | `dark` |

- **Carving** (D4, the mock's values; re-derive them from `final/index.html` rather than from iOS `StoneTones` if they drift): ink is `dark` for lowlight and neutral and `secondary` for highlight. Lip light is `secondary` / `secondary` / `light` @ .84. Lip shadow is `shaded` / `secondary` @ .45 / `dark` @ .75. The lip is the carve drawn twice, offset ±1.25 px along the rest light.
- **Glyph**: `pebble.glyph.strokes`, placed with the engine glyph zones. `GLYPH_SIZE` and `GLYPH_POSITION` are copied from `packages/supabase/supabase/functions/_shared/engine/layout.ts` into `stone-geometry.ts`, with a Vitest drift test reading that file. The stroke stays a constant 1.25 px via `vector-effect: non-scaling-stroke`. When `glyph` is null, the stone has no carving.
- **Seeded detail**: facet points, pits and glitter positions come from `stoneRecipe(size, polarity, seed)`, a pure function seeded by `hashUUID(pebble.id)` (`lib/engine/seed.ts`). The same pebble always draws the same stone.
- **The press runs on the HTML wrapper**, not on an SVG group. That transform is compositor-only, so the filtered SVG is never re-rasterised during the dip. The rings live in a separate sibling SVG so their swell does not touch the stone raster. The glint animates inside the stone's SVG, which costs a main-thread repaint of one 54 px element for 900 ms.

**Battery contract on web:**

- Nothing animates unless pressed: no infinite CSS animation, no rAF loop, no scroll listener. Scroll-spy uses one `IntersectionObserver` with `rootMargin: "-35% 0px -64% 0px"`.
- Every WAAPI animation is finite and its host unmounts.
- A still page therefore does zero work, which is the web equivalent of "a still device publishes nothing".
- The web cannot read Low Power Mode. There is no API, and Safari throttles on its own. Because nothing runs unattended, the contract holds without detection.
- `prefers-reduced-motion` selects the still variant.

**`render_svg`: not reused.** It is the engine's flattened composite (outline, veins, fossil and glyph in `currentColor`). The glyph can't be lifted out of it to carve, the outline it draws is the one D3 removes, and the moments split drops the column (§Moments split).

## Responsive model and chrome

**Phone (< 900 px).** `PathScreen` is a full-height column:

- **Week roll** (top, restyled): ISO numerals in Ysabeau SC on the ground; the focused one in ink with a 1 px underline.
- **`WeekPager`**: each week is a vertical scroll container holding `SpineWeek`. The opening comes first: a masthead with "← week 40" (calls `onFocusChange`), then kicker, title, summary, persons and contents. The recto follows, then the mouth with "week 42 →" or, on the current week, "carve a pebble".
- **Dock** (bottom, sticky): `PathBottomBar` on a translucent stock pill with an 8 px backdrop blur, as the mock's edition pill.

**Wide (≥ 900 px).**

- The scroll container holds the spread grid.
- The **verso** is `sticky top-0 max-h-full overflow-y-auto`, with no scrollbar. It holds, top to bottom: roll, masthead, opening, persons, contents, and the dock at its foot. The verso is always visible, so the dock is always reachable without spanning the page.
- The **recto** gets the full height.
- ≥ 1240 px: `400px 760px`, with a 24 px gap.

**Each piece of chrome, argued:**

- **The opening replaces the week header.** There is no header today, and the title in words is one.
- **The roll stays, restyled.** It is the only way back months (`AC-path-week-roll`). The masthead and mouth links cover ±1 week.
- **The dock stays.** Profile, bounce and karma (`AC-path-stats-bar`) have no other home. The recto keeps 96 px of bottom padding to clear it.
- **Paging stays.** Keyboard, roll, links, and touch drag on coarse pointers. Neighbours render fully.
- **`DraftsEntry`** is parked in the masthead until the split removes drafts.

## Theming

Paper is `:root` and Night is `.dark`. The tokens are defined once each in `globals.css`. The colour worlds do not define them, so the Path paper is the same in every world.

| Token | Paper | Night |
|---|---|---|
| `--spine-ground` | `#ECE8DF` | `#141416` |
| `--spine-stock` (card) | `#FBF9F4` | `#1E1D1B` |
| `--spine-ink` / `-2` / `-3` | `#1F1D1A` / `#686255` / `#9C9588` | `#ECE6DA` / `#A59E91` / `#6E685E` |
| `--spine-rule` / `--spine-card-rule` | ink @ .14 / .22 | ink @ .12 / .18 |
| `--spine-line` / `--spine-seam` / `--spine-teeth` | ink @ .34 / .30 / .20 | ink @ .30 / .24 / .14 |
| `--spine-ring` / `--spine-ripple` | `rgba(76,64,46,.30)` / `rgba(52,44,32,.55)` | `rgba(220,222,230,.17)` / `rgba(236,232,225,.5)` |
| `--spine-halo` (clearing and labels) | `#E6E2D8` | `#19191B` |
| `--spine-photo-filter` / `--spine-stone-filter` | none / none | `brightness(.9) contrast(1.02)` / `brightness(.92) saturate(.96)` |
| `--spine-shadow-rgb` / `-a` | `52,40,26` / .3 | `0,0,0` / .62 |

Plus `--spine-card-shadow`, `--spine-rake` and `--spine-grain`, verbatim from the mock.

**Errors are orange, never red** (2026-09-28, #990). A failed photo, such as an expired signed URL or a timed-out probe, keeps the tinted field at the reserved ratio and sets "photo unavailable" in `text-destructive` (`--pbbls-error`). No red anywhere, including the Night lava, which stays a palette tone.

## Accessibility

- **Landmarks and headings.** The week is an `<article>` labelled by its title (`<h1>`). The verso is an `<aside>` "The week at a glance"; the recto is a `<section>` labelled with the date range. Each day is a `<section>` with a visually hidden `<h2>` ("Monday 5 October"). Each pebble is an `<article>` labelled by its `<h3>` name.
- **The stone** is a `<button>` whose name says what it is and does: "Flag march with Nadia. Monday 5 October, 10:15. Large gem, pride. With Nadia. Opens the pebble." Its focus ring is 1.5 px ink, offset 9 px, round.
- **The card or entry** carries an invisible full-surface `<button>`, the `PathPolaroid` pattern (a `<button>` may not contain the `<figure>`). Time and emotion repeat as visually hidden text, because the index and rail are `aria-hidden`.
- **Contents** rows are real links (`href="#pebble-{id}"`). On activation they scroll with `smooth`, or `auto` under reduced motion, then focus the stone with `preventScroll` and press it.
- **Day dots**: `role="img"` with an `aria-label` from `Intl.ListFormat`, e.g. "On four days: Monday 5, Tuesday 6, Thursday 8, Saturday 10". The letter header is `aria-hidden`.
- **Live region**: `aria-live="polite"` announces the focused week on paging.
- Colour is never the sole signal: valence is also the name's cut, intensity also the size and rank.
- Contrast to verify: ink-2 on ground is about 5.6:1. Ink-3 (`#9C9588`, about 2.6:1) is used only for time and empty dots, which carry no information that is not also given in text. The time is also in each button's name.

## Performance

- **Ground**: one tiled SVG data URI with a CSS mask. **Seams**: one element with three gradients. Neither needs SVG nodes or JS.
- **Stones**: a 20-pebble week is about 1,600 SVG nodes and about 90 blur primitives, all small, rasterised once. Budget on a Pixel 7: first render of a focused 20-pebble week ≤ 60 ms scripting; ripple frames ≤ 16 ms (the dip is compositor-only).
- **Neighbour weeks**: render inside `requestIdleCallback` after the focused week paints.
- **Long weeks**: day sections below the first get `content-visibility: auto` with `contain-intrinsic-size: auto 600px`. Verify that it does not break the sticky verso or the IntersectionObserver.
- **Photos**: `loading="lazy" decoding="async"`, with explicit width and height once the ratio is known. The thumb probe costs ≤ 300 KB per photo, once per session.
- **Fonts**: two latin variable subsets, loaded only on `/path`.

## i18n (next-intl, `path.spine.*`, EN and FR)

| Key | EN | FR |
|---|---|---|
| `kicker` | the {ordinal} week · {year} | la {ordinal} semaine · {year} |
| `titleSameMonth` | The {from} to the {to} of {month} | Du {from} au {to} {month} |
| `titleCrossMonth` | The {from} of {fromMonth} to the {to} of {toMonth} | Du {from} {fromMonth} au {to} {toMonth} |
| `summary` | {pebbles} {pebbles, plural, one {pebble} other {pebbles}}{people, plural, =0 {} other {, {peopleWords} {people, plural, one {person} other {people}}}}. | same shape, *galet(s)*, *personne(s)* |
| `persons` / `contents` / `with` | persons of the week / contents / with | les personnes de la semaine / sommaire / avec |
| `others` | & {words} {count, plural, one {other} other {others}} | & {words} {count, plural, one {autre} other {autres}} |
| `still` | still | calme |
| `dayDotsAria` | {count, plural, one {On one day: {days}} other {On {count} days: {days}}} | {count, plural, one {Un jour : {days}} other {{count} jours : {days}}} |
| `weekEnds` / `nextBegins` | the {ordinal} week ends here / The {ordinal} begins on Monday the {day}. | la {ordinal} semaine s'arrête ici / La {ordinal} commence le lundi {day}. |
| `prevWeek` / `nextWeek` | ← week {n} / week {n} → | ← semaine {n} / semaine {n} → |
| `stone.{small,medium,large}.{lowlight,neutral,highlight}` | e.g. Large gem, Small river pebble, Medium lava stone | Grande gemme, Petit galet de rivière, Pierre de lave moyenne (nine keys, because of gender agreement) |
| `stoneAria` / `cardAria` | {name}. {date}, {time}. {stone}, {emotion}. {with} Opens the pebble. | … Ouvre le galet. |
| `photoUnavailable` / `untitled` | photo unavailable / untitled | photo indisponible / sans titre |

Numbers in words come from `lib/i18n/number-words.ts`, a pure module with tables:

- EN: cardinals 0–60 and ordinals 1–53, used for week numbers and dates ("fifth", "forty-first")
- FR: cardinals with "premier" for the 1st of a month, and ordinals ("quarante et unième")

ICU covers plurals but not spelled-out numbers. Weekday letters, short weekdays and months come from `Intl`, so they need no keys.

## Testing

Vitest, pure functions, one file each:

| File | Asserts |
|---|---|
| `card-size.test.ts` | Every row of the sizing table; the 180 px floor; cap selection by intensity × breakpoint |
| `entry-type.test.ts` | Size, weight and italic for all 3 × 3 combinations, phone and wide |
| `people-line.test.ts` | n = 0, 1, 2, 3, 4, 7, 14: named count, others in words, marks ≤ 5, `+count`; order follows persons-of-week |
| `persons-of-week.test.ts` | Dots by local day across a DST change; a pebble at 23:59 Sunday stays Sunday; ordering ties; the eight-row cap |
| `spine-week.test.ts` | Seven days Monday-first; oldest-first in past *and* current weeks; still days; future days of the current week omitted; reading the days in order returns chronological input. This is the successor of the round-robin invariant in `path-layout.test.ts`, which is deleted with its module. |
| `stone-recipe.test.ts` | Deterministic per seed; counts per shape (facets 5 + 1, glitter 10, pits) |
| `stone-carvings.test.ts` | Equals `docs/seeds/shape-seeds/*.svg`. It must reproduce iOS `StoneShapeParser` on `large-neutral`, where the round-1 agents reported the outline is not the first stroked path. Vein counts are 0/0/0, 1/1/2, 1/2/6 and `large-highlight` has no fossil. |
| `stone-geometry.test.ts` | Glyph zones equal `_shared/engine/layout.ts` |
| `number-words.test.ts` | EN/FR tables at the boundaries (1, 21, 31, 41, 53) |
| Provider test | A verbatim `v_pebbles_full` row with `name: null`, `glyph: null`, a fractional-second `happened_at` and a whole-second one (the foreign-payload rule) |

**Visual check.** `/sandbox/design` gains a "Path · Spine" view specimen rendering the real components over the brief's fixture week (Mon 5 – Sun 11 Oct 2026, eleven pebbles, five souls, photos already in `public/sandbox/`). To that it adds a 14-soul pebble, an empty day, and one pebble with no glyph. The #720 lesson is that a fixture must exercise every data branch the product takes. Theme and width are checked at 390, 900 and 1240 px, Paper and Night, against the mock.

## Moments split: what it changes

- **D6 (no render cache).** The Spine never reads `render_svg`. It draws `glyph` strokes, which the compat `v_pebbles_full` keeps as the *resolved* glyph (collection, then domain default). The Pebble projection of `glyph` lands in Part 1 here and serves both.
- **D3 (name or photo).** A moment created on iOS or Android after the split can have `name: null` and still reach web through the compat view. The provider maps null to `""`. The card then shows no title line, and the entry and contents use an italic "untitled" in ink-3.
- **D7 (drafts retired).** `DraftsEntry` is not restyled, only parked in the masthead, and leaves with the compat part.
- **Uncompleted moments** (`path_moments.pebble = null`) are invisible to web while it reads the compat view. When the parked web composer lands, `LitStone` must accept `palette: null`: a blank river pebble in a neutral grey ramp, no carving, no emotion in the meta, and the name in ink-2. The prop is shaped for that now.
- **Snap dimensions**: proposed for the split's Part 1 (§Data).

## Delivery: stacked parts (`gh stack`)

Each part lints, typechecks and passes `npm run test --workspace=@pbbls/web` on its own. Order is by dependency. Parts 1–5 are visible only on `/sandbox/design` and carry `no-lab-note`. Part 6 is the switch.

| # | Branch / PR | Contents | Estimate |
|---|---|---|---|
| 1 | `feat/N-spine-foundations` | `--spine-*` tokens and `@utility` blocks; fonts and `app/path/layout.tsx`; palette hook adds `shaded`/`dark`; `Pebble.glyph` projection and tolerant `name`; `number-words`, `card-size`, `entry-type`, `people-line`, `persons-of-week`, `spine-week` with tests | 0.75 day |
| 2 | `feat/N-web-lit-stone` | `stone-carvings` (generated) and `stone-geometry` with drift tests; `stone-recipe`, `stone-tones`; `StoneSprite`, `LitStone`, `StoneSeal`; a nine-by-seven specimen on the design page | 1.5 days |
| 3 | `feat/N-spine-feed` | `SpineFeed`, `DaySeam`, `SpinePebble`, `SpineEntry`, `SpineCard`, `PeopleLine`, `useSnapAspect` (thumb signing in the provider); ripple and glint; fixture-week specimen | 1.5 days |
| 4 | `feat/N-spine-opening` | `WeekOpening`, `PersonsOfWeek`, `WeekContents`, `WeekMouth`, `SpineWeek` (phone), scroll-spy, contents → press | 1 day |
| 5 | `feat/N-spine-spread` | The ≥ 900 / ≥ 1240 spread, sticky verso; roll, dock and empty-state restyle; `PathScreen` layout without `max-w-md` (still rendering the wall) | 1 day |
| 6 | `feat/N-path-becomes-spine` | `WeekPager` renders `SpineWeek`; delete the wall, polaroid, `PathStone`, `WeekPath`, `PathPebbleRow`, `path-layout`, `polaroid-chaos` and the `/sandbox/path` route; specimens updated; decision-log entry; Arkaik; Lab Note | 0.5 day |

The total is about **6.25 days**. The DIRECTION's two-day web figure covers the stone, glint, ground, seams, ripple and strip, which is roughly Parts 2–3 minus cards and entries. Cards, entries and the opening are on top of that. Part 5 is "a move" (layout and restyle) and stays separate from the rewrite in Parts 3–4.

## Arkaik

The map is hosted, so every edit goes through `arkaik-mcp` (`update_node`, `create_node`, `add_edge`). Never edit `docs/arkaik/bundle.json`.

- **`V-timeline` ("Path")**: rewrite its description for web: the opening, persons and seven-dot strip, contents, the margin line with lit stones, seams, card versus entry, ripple, and the spread at ≥ 900 px. Drop the polaroid wording.
- **Acceptances it covers**, moved to `development` for web when Part 6 starts, never set `live` by hand: `AC-pebble-snap-framing`, `AC-pebble-unique-artwork`, `AC-path-row-emotion-tint`, `AC-pebble-emotion-palette-ink`, `AC-touch-feedback-and-haptics`, `AC-path-week-roll`.
- **Candidate new acceptances** to propose (web-only platform status): "The week opens with who was there and what happened" and "A photo keeps its own shape".
- **Lab Note**: Part 6 is user-facing and `feat`, so `CLAUDE.md` requires a `## Lab Note (EN/FR)` written with the repo's `lab-note` skill. The maintainer has also said that Arkaik coverage can replace the note (`no-lab-note`). Confirm which applies when opening Part 6.
- **Decision log** (Part 6): one entry, "The web Path becomes the Spine: lit SVG stones over iOS geometry, the polaroid wall and list retired". It supersedes the 2026-08-23 #720 entry.

## Decisions taken on the open questions (2026-10-11)

All settled by the maintainer on 2026-10-11 ("Sounds good" to the defaults), and binding for the parts below:

- **Photo width on a 390 px phone** stays as designed; the opened pebble gives the size back.
- **Stone construction** is SVG, the same render as the artifact (D5). A WebGL bake is a later option for a tilt-lit read page.
- **Tone slots** stay the artifact's for now (D4). The whole palette may be redefined later; iOS is then re-tuned against the same tables.
- **Soul roles** are dropped (§Non-goals).
- **A card starts square** and eases to its photo's ratio when known (D14).
- **Opening and roll:** the opening scrolls with the week under the persistent week roll; the roll does not move into the masthead.
- **Current week:** oldest first, no auto-scroll to today's seam on open.
- **Snap dimensions:** proposed for the moments split's Part 1 (stored `width`/`height` on `snaps`); until then the probe and the square start (D14) carry it.
- **Paper and colour worlds:** the Path paper stays neutral in every world (D10).
- **Persons cap:** eight rows, then "and N others".
- **The pip** returns only when a card leaves the page; share and Cairn export are specified later.
- **Moments split interplay:** uncompleted moments draw as blank stones, photo-only moments as "untitled" entries, as described in §Moments split.

## Risks

- **Probe and layout shift** until snap dimensions are stored, worst on Safari with no scroll anchoring. Mitigated by the square start and animated settle (D14), the session cache and the contents re-scroll.
- **Signed URLs expire after 1 h**, so a tab left open breaks photos. The orange "photo unavailable" state hides nothing. Re-signing on `onError` is a follow-up.
- **Filter cost on low-end Android** with ±1 weeks of 20 pebbles. Measure in Part 2 on the Pixel 7 AVD; if the budget fails, defer neighbours or replace the blurred shadow copies with a CSS `drop-shadow` on the wrapper.
- **The ground sits at the edge of visibility.** Check it on real screens in both themes before Part 6. Too light, and the page reads as a list with a line; too dark, and it reads as wood grain.
- **Divergence.** Web ships the Spine while iOS and Android keep rows. The rule set above is written to be the shared spec, and the two native issues should be filed with Part 6.
