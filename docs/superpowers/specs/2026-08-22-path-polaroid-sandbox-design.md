# Path polaroid layout sandbox — design

- **Date:** 2026-08-22
- **Issue:** #720
- **Milestone:** M58 · Dynamic and picture-first Path
- **Surface:** `apps/web` only

## Purpose

Iterate on a more dynamic, picture-first Path display without touching the shipped
`/path` route. The experiment runs on a dedicated unauthenticated page seeded with
fixture content that covers every layout case, so a design decision can be made by
looking rather than by arguing.

The page exists primarily to answer one question that cannot be settled on paper:
**how the pebble glyph sits on a polaroid card, with a picture and without one.**
Three variants ship behind a live toggle.

## Success criteria

- `/sandbox/path` renders without auth, without network, and without Supabase.
- Every scenario in the fixture is reachable from the toolbar.
- The three glyph variants can be switched live and compared on the same content.
- The grouping rule is a pure, unit-tested function, promotable into the real Path.
- The shipped `/path` route renders identically before and after this change.

## Non-goals

- Changing the real `/path` route. Promoting a winning variant is a separate change.
- Mirroring anything onto iOS or Android. The cross-surface rule applies when a
  schema/RPC contract or a shipped behavior changes; a sandbox route changes neither.
- An Arkaik bundle update. A throwaway sandbox route is not a product view node.
- A Lab Note. Nothing user-facing ships.

## Architecture

### Route and shell

`apps/web/app/sandbox/path/page.tsx` — a client page with `robots: { index: false }`.

It composes its own minimal shell: no `PathBottomDock`, no `WeekRoll`, no `WeekPager`.
A toolbar pinned at the top carries four controls:

| Control | Values |
|---|---|
| Scenario | one entry per fixture week (see below) |
| Glyph variant | `stamp` / `adaptive` / `margin` |
| Theme | light / dark |
| Motion | animated / reduced |

The page imports nothing from `app/path` and renders new components under
`components/sandbox/`, so nothing tried here can regress the shipped Path.

### Files

**New**

| Path | Role |
|---|---|
| `app/sandbox/path/page.tsx` | Route, toolbar state, scenario + variant wiring |
| `components/sandbox/SandboxPathScreen.tsx` | Blocks → rendered layout |
| `components/sandbox/SandboxPolaroid.tsx` | One polaroid card |
| `components/sandbox/SandboxToolbar.tsx` | The four controls |
| `components/sandbox/PolaroidGlyph.tsx` | The three glyph variants |
| `lib/utils/path-layout.ts` | Pure grouping function |
| `lib/utils/path-layout.test.ts` | Vitest coverage of the grouping function |
| `lib/utils/polaroid-chaos.ts` | Deterministic per-pebble chaos |
| `lib/sandbox/sandbox-pebbles.ts` | Fixture pebbles, souls, palettes |
| `public/sandbox/*.jpg` | Local placeholder pictures |

**Modified**

| Path | Change |
|---|---|
| `lib/data/useEmotionPalettes.ts` | Add an exported `primeEmotionPalettes(map)` |
| `app/layout.tsx` | Import `@fontsource-variable/caveat` |
| `app/globals.css` | Add `--font-hand: "Caveat Variable", cursive` |
| `apps/web/package.json` | Add `@fontsource-variable/caveat` |

### Palette priming

`PebbleFramed` silently degrades to a bare, untinted `PebbleVisual` when
`useEmotionPalettes` has no entry for the pebble's emotion. On a sandbox page with
no network that is every card, which would make the whole experiment lie about
colour.

`useEmotionPalettes` already holds a module-level `cachedMap` that `loadOnce()`
short-circuits on. The change is additive and four lines:

```ts
/** Seed the module cache before any consumer mounts. Used by the sandbox page,
 *  which renders fixture pebbles with no network available. */
export function primeEmotionPalettes(map: PaletteMap): void {
  cachedMap = map
}
```

The sandbox page calls it at module scope, before first render. Production code
paths are untouched: nothing else calls it, and `loadOnce()` behaves exactly as
before when the cache starts null.

Fixture pebbles carry `render_svg: null`, so `PebbleVisual` falls through to the
client engine via `usePebbleVisual` — already the documented path for
unauthenticated previews such as the landing page.

## The layout algorithm

`lib/utils/path-layout.ts` exports one pure function:

```ts
export type PathBlock =
  | { kind: "small";  pebbles: Pebble[] }
  | { kind: "medium"; rows: Pebble[][] }
  | { kind: "large";  pebble: Pebble }

export function groupPebbles(pebbles: Pebble[]): PathBlock[]
```

Walk the input in the order given — chronological, as the caller supplies it — and
cut a new block whenever `intensity` changes from the previous pebble. Order is
never rearranged; the Path stays readable as a timeline.

- **Intensity 1 (small)** → one block holding the whole run. Rendered as N stacked
  compact rows.
- **Intensity 2 (medium)** → one block whose run is chunked into pairs. A run of N
  yields `ceil(N / 2)` rows; when N is odd the final row holds a single pebble.
- **Intensity 3 (large)** → one block per pebble, full width.

Rendering rules that follow from the shape:

- A medium row of two → two cards side by side, each 50% of the container minus gap.
- A medium row of one → one card at 50% width, centered in the container.
- So: 1 medium is a centered half-width card; 2 mediums are a pair; 3 mediums are a
  pair with one centered below; 4 are two pairs.

The mapping from `pebble.intensity` (`1 | 2 | 3`) to block kind reuses the existing
`SIZE_BY_INTENSITY` vocabulary in `lib/config/pebble-geometry` rather than inventing
a second size scale.

### Small rows

Small blocks render the shipped `PathPebbleRow` unchanged — pebble, name, picture.
It is imported, not copied: the small row is explicitly *not* part of what this
experiment is changing, and a forked copy would drift.

## The polaroid card

`SandboxPolaroid`, ported from the Let's Gong `polaroid-print.tsx` and adapted to
Pebbles' tokens.

### Stock

`bg-card` with a soft two-layer shadow. In dark mode a drop shadow cannot read
against a near-black page, so the dark variant swaps to a faint inset top highlight
that lifts the card instead. Both variants are defined as exported class constants,
so the card and its hover state stay in one place.

### Chaos

`polaroidChaos(pebbleId)` — deterministic, derived from a cheap string hash of the
pebble id, never `Math.random()` (which would reshuffle the layout on every render
and make it impossible to judge). Yields:

| Field | Range |
|---|---|
| `rotate` | −6…6 deg |
| `shiftX` | −6…6 px |
| `z` | 0…9 |

The wrapper element carries the static chaos; the card element carries the
interaction transform. Splitting them is load-bearing — a single element would have
the hover transform replace the rotation rather than compose with it.

### Interaction

- Hover: `-rotate-3 scale-105`, deeper shadow, spring easing.
- Active: `rotate-2 scale-95`.
- `focus-visible` mirrors the hover state, so keyboard navigation gets the same
  feedback as the pointer.
- All of it gated on `prefers-reduced-motion`, which the toolbar can also force.

### Contents

Picture (when present) → title in Caveat (`--font-hand`) → a footer row carrying
soul avatars and the time.

### Large

The same component at `size="lg"`, full width, and with **no rotation** — a
full-bleed card that tilts reads as broken rather than playful.

## Glyph variants

`PolaroidGlyph` takes the pebble, whether a picture is present, and the active
variant. All three ship; the page decides nothing.

- **`stamp`** — bottom-right, over the picture, small, on a soft light disc. With no
  picture it holds the same bottom-right position on the bare paper. Constant
  placement; reads like a wax seal.
- **`adaptive`** — with a picture: a small mark straddling the picture's bottom-left
  corner, half on the image and half on the white margin, like a corner sticker.
  With no picture: the glyph becomes the hero, large and centered, filling the
  picture well.
- **`margin`** — never touches the picture. Sits inline to the left of the title in
  the caption area, at avatar scale. With no picture the picture well collapses
  entirely and the card becomes a short title-only slip.

**Recommendation going in: `adaptive`.** A polaroid with no picture has an empty
picture well that has to be filled with something, and the glyph is the most
meaningful candidate — it is the pebble's own identity. `stamp` and `margin` both
leave that well awkward. This is a prior, not a decision; the page is what settles it.

## Fixture content

`lib/sandbox/sandbox-pebbles.ts` — hand-written scenarios, each a named week, plus a
hardcoded palette map keyed to real emotion ids and a small set of fixture souls.

| Scenario | Covers |
|---|---|
| `mixed` | small, small, medium×2, small, large, medium×3 — the full ladder |
| `oddMediums` | runs of 1, 3 and 5 mediums — the centering rule |
| `allMedium` | 8 mediums — the pure grid |
| `noPhotos` | every size, zero pictures — the glyph-hero case |
| `photosOnly` | every medium carries a picture |
| `manySouls` | 0, 1, 2 and 5 souls on one card — avatar stack overflow |
| `longTitles` | title wrapping in Caveat, at both card widths |

Pictures are local files under `public/sandbox/`. No Storage, no signed URLs, no
network.

## Testing

`lib/utils/path-layout.test.ts` (Vitest) covers `groupPebbles`:

- empty input → no blocks
- a single small → one small block of one
- medium runs of 1, 2, 3, 4 and 5 → correct row chunking, odd leftover in its own row
- a large between two medium runs → three blocks, runs not merged across it
- input order is preserved within every block

The visual layer has no automated test. Judging it by eye is the entire point of the
page.

## Verification

- `npm run test --workspace=apps/web`
- `npm run lint --workspace=apps/web`
- `/path` visually unchanged before and after.

---

# As built (2026-08-23)

The design above is the *starting* point. Iterating on the live page moved most of
it, and this section is what actually shipped. Where the two disagree, this wins.

## The glyph question was answered, then dissolved

The page was built to choose between three glyph placements (stamp / adaptive /
margin). None of them won: the answer was a fourth option not on the list — the
pebble sits **top-centre, overhanging the card's top edge**, like a real stone laid
on a print. Placement stopped being a variable, and the toolbar's third control was
repurposed to stone *scale*, which is what remained open.

## What the card became

Read top to bottom: stone over the edge → picture (if any) → name in Caveat →
souls and the day. Ordered that way after trying the reverse (meta row first, so
the stone fell into the `justify-between` gap); the original order read better once
the stone was overhanging.

- **No chaotic rotation.** The deck lies flat at rest; the tilt is interaction only
  (`-3°` hover, `+2°` press). `lib/utils/polaroid-chaos.ts` survives, unused, in
  case the scatter comes back.
- **Name** at `1.125rem` (`1rem` on a small card), leading `1.05`. The leading must
  be written *after* the `text-*` size — Tailwind's font-size utilities also set
  line-height, so `tailwind-merge` silently drops an earlier `leading-*`.
- **Day, not time**: "Monday, 17", plain case, composed from two single-field
  `formatDate` calls because Intl joins `{ weekday, day }` with no separator. The
  accessible name keeps the time, which is the only thing separating two pebbles on
  the same day.
- **Caption row** is `justify-between` with souls, `justify-center` without.
- **Square margin** when there is a picture (`pt` matches `px`); only a picture-less
  card opens extra head room for the stone.

## Layout: a wall, not rows of pairs

`groupPebbles` no longer chunks mediums into pairs. Small and medium pebbles are
all polaroids now, dealt **round-robin** into flex columns; a large pebble breaks
the wall and takes the full width.

Round-robin rather than height-balanced is a correctness choice, not a convenience
one: height-balancing lets a short card jump the queue to fill a gap, so two cards
side by side stop being neighbours in time. A test asserts that reading the columns
row by row gives back the input order.

Flex columns rather than CSS `columns-*`: multicol fragments boxes at column
boundaries, slicing each card's drop shadow — and here it would bisect the
overhanging stone too.

## Colour

Light mode fills the stone with the palette's `light` and draws in `primary`; dark
mode fills with `dark` and draws in `secondary`.

Both ends are emitted as CSS custom properties and picked by the `.dark` cascade —
never from a JS-read theme, which desyncs between server and client render (the
same reason `PathPebbleRow` documents). Glyph strokes reuse the existing
`.pbbls-visual` rule; one new `.path-stone-fill` pair in `globals.css` swaps the
silhouette, which fills with `currentColor` so CSS can reach it.

**`dark_color` is not reachable in production.** It is not on `EmotionPalette` and
not selected by `v_emotions_with_palette`, so the shipped stone falls back to
`secondary_color` for its dark fill. The sandbox has the real value hard-coded, so
the two differ in dark mode until the column is projected. Open follow-up.

## Two defects found by building this

1. **`strokeOverride` is silently dropped on client-engine renders.** A pebble with
   no server render takes the engine fallback, which bakes a flat hex in via
   `recolor()`; `PebbleVisual` ignores `strokeOverride` there. Any caller handing
   `PebbleFramed` a stroke colour for a legacy or anonymous pebble is not getting
   it. Worked around locally by substituting `currentColor` back into the composed
   SVG. Not fixed in the shipped component — it would change how anonymous pebbles
   render on the landing page.

2. **A sandbox that only exercises the fallback path cannot catch a regression on
   the other one.** The fixtures all carry `render_svg: null`. The first cut of
   `PathStone` read the fallback unconditionally and skipped the wobble pass, so
   real pebbles rendered as bare, unwobbled outlines while the sandbox looked
   perfect. Verifying the sandbox proved nothing about the branch that was broken.

## Scope actually shipped

The wall is the **default** Path display. `WeekPath` gained
`display?: "wall" | "list"`, defaulting to `"wall"`; the compact row stack is
untouched and still reachable, pending a user-facing setting.

`/sandbox/path` stays, now rendering the *production* `PathPolaroid` over fixture
data rather than a copy of it — two implementations of one card would drift, and
trying changes to the shipped card is the point of the page.

## Still open

- **iOS and Android still render the compact row Path.** No schema or RPC changed,
  so nothing is broken, but the three clients no longer look alike.
- `dark_color` in the palette projection (above).
- `next-intl` has no configured `timeZone`; a pebble recorded near midnight is
  where that would surface as a hydration mismatch. Pre-existing.
