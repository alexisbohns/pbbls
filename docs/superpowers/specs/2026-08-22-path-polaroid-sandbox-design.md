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
