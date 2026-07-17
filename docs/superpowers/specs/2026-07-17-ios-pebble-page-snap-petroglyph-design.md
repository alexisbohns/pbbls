# iOS — Snap display on the Pebble's page (issue #599)

**Date:** 2026-07-17
**Issue:** #599 · milestone M37 · Vision Pebble Page
**Surface:** iOS (`apps/ios`). Web/Android follow separately.

## Context

The Pebble read page currently shows a top zone (`PebbleReadBanner`) where the
snap fills the full content width in a **16:9 / 4:3 / 1:1** bucket and the pebble
sits **bottom-centered, half-overlapping** the snap, revealed *after* the stroke
animation via a timed insert/mask.

Issue #599 redesigns this top zone:

- **With a snap:** show the picture entirely, bucketed to the nearest of
  **1:1, 3:4, 4:3, 16:9**, with the **Petroglyph** (backfill + outline + glyph)
  overlapping on the **top-right**.
- **Without a snap:** show only the Petroglyph as the heading content of the
  page.

Contrary to the issue's "Context" wording, this is **new work** — no platform
has shipped it yet (web still hard-crops the snap to a square; iOS uses the old
bottom-centered banner). iOS is the first platform to build it to spec.

The **edit mode** is explicitly out of scope, but the same layout will be reused
there later (snap displayed, or a dashed tap-to-upload placeholder). The layout
must therefore be factored into a presentational, view-mode-agnostic component.

## Decisions (locked with the requester)

- **Snap fill:** cover — `.aspectRatio(.fill)` + clip. Fills the bucket
  edge-to-edge, crops a thin sliver. (The bucket is the *nearest* ratio, so the
  crop is minimal.)
- **Entry:** show snap and petroglyph *together* on load. Drop the old timed
  reveal gate. The petroglyph keeps its own spring/scale entry animation.
- **Tilt:** match web — snap **−4°**, petroglyph **+7°** (with-snap composition
  only; the no-snap petroglyph stays straight and centered).
- **`palette.dark`:** resolves to the new `dark_color` column the requester
  added to `public.emotion_categories` (alongside `shaded_color`).

## Architecture

Three concerns, cleanly separated:

### 1. `SnapPetroglyphHeader` (new — presentational layout)

Pure layout, no data loading, so edit mode can reuse it. Generic over the
petroglyph content:

```
SnapPetroglyphHeader<Petroglyph: View>
  snapImage: UIImage?       // decoded bytes, nil until loaded (or no snap)
  hasSnapSlot: Bool         // a snap is expected — drives layout before bytes arrive
  petroglyph: () -> Petroglyph
```

- **With snap slot** — `ZStack(alignment: .topTrailing)`:
  - Snap: full content width at its `BannerAspect` bucket ratio,
    `.aspectRatio(bucket, contentMode: .fill)` clipped to
    `RoundedRectangle(cornerRadius: 24)`, rotated **−4°**. Until `snapImage`
    decodes, the area shows a rounded placeholder (`Color.system` surface) at a
    default **square** ratio; on decode the image cross-fades in and the
    container settles to its bucket ratio.
  - Petroglyph: fixed **120pt** box, anchored top-trailing, offset
    **(x: +16, y: −16)** so it pokes out past the corner, rotated **+7°**.
- **No snap slot** — petroglyph centered as heading content, no tilt, no
  placeholder (today's Phase-1 behavior preserved).

**Layout is driven by `hasSnapSlot` (presence of a snap path), not by
bytes-loaded** — the petroglyph is top-right from first paint, so there is no
center→corner jump when the async image arrives.

### 2. `BannerAspect` — bucket set

Add **3:4 (0.75)** to the existing set. Final buckets:

| bucket | ratio (w/h) |
|---|---|
| 1:1 | 1.0 |
| 3:4 | 0.75 |
| 4:3 | 1.333… |
| 16:9 | 1.778… |

`nearest(to: width/height)` logic unchanged (min absolute distance). Portrait
snaps now bucket to **3:4** instead of collapsing to square. The source ratio is
computed from the **original** rendition's intrinsic size (already loaded first
via `SnapURLCache`), chosen before layout.

### 3. Color model — theme-aware petroglyph colors

The petroglyph is the composed pebble artwork:
`PebbleAnimatedRenderView` already `ZStack`s the backfill
(`PebbleOutlineBackdropView`) + the outline/glyph strokes. #599 changes only the
**colors it is fed**, and makes them **scheme-dependent**.

- **Migration (out of this iOS branch):** the `shaded_color`/`dark_color`
  columns + the `v_emotions_with_palette` change are already applied on the
  remote, and the git migration + regenerated `database.ts` land via the
  parallel Android branch (`claude/issue-599-android-pebble-52orio`, migration
  `20260717000000_emotion_categories_shaded_dark.sql`). This iOS branch adds
  **no migration and no type regen** — the Swift client decodes the JSON columns
  directly (it does not consume `database.ts`) and iOS unit tests use hardcoded
  palettes, so there is no DB dependency to reproduce here. A duplicate
  same-timestamp migration would collide, so we deliberately avoid one.
- `EmotionWithPalette` / `EmotionPalette` decode `darkHex` (+ `shadedHex`,
  carried but unused by #599 — kept so the Swift model mirrors the row).
- New `EmotionPalette.petroglyphColors(intensity:scheme:)` implementing the
  #599 table. "Stroke" = outline + glyph paths (one color); "backfill" = the
  silhouette fill:

  | intensity | stroke (light / dark) | backfill (light / dark) |
  |---|---|---|
  | small, medium (1, 2) | primary / secondary | light / dark |
  | large (3) | light / light | primary / primary |

  Returns 6-digit `#RRGGBB` hexes (SVGView requirement) plus `fillOpacity` from
  the backfill hex's alpha byte, matching the existing `PebbleFrameColors`
  contract.
- `PebbleReadBanner` reads `@Environment(\.colorScheme)` and feeds these into
  `PebbleAnimatedRenderView`.

**Scope: the Pebble page only.** The timeline row (`PathPebbleRow`) keeps its
current `pebbleFrameColors(forIntensity:)` coloring untouched — the existing
method stays for that call site; the new method is additive.

### 4. `PebbleReadBanner` — data + color wrapper

Retains the async snap load (signed URL → bytes → `UIImage`) and the
`BannerAspect` selection. **Removes** the `animationFinished` / `revealPhoto`
timed-reveal state (superseded by "show together"). Computes scheme-aware colors
via `petroglyphColors`, builds the petroglyph (`PebbleAnimatedRenderView`), and
passes `snapImage` + `hasSnapSlot` + the petroglyph to `SnapPetroglyphHeader`.

## Data flow

```
PebbleReadView
  └─ PebbleReadBanner  (loads snap bytes; picks BannerAspect; picks colors by colorScheme)
       └─ SnapPetroglyphHeader  (pure layout: cover snap + top-right petroglyph, or centered)
            └─ PebbleAnimatedRenderView  (= petroglyph: backfill + outline/glyph, own entry anim)
```

## Error handling

- Snap URL/bytes/decode failures: logged via `os.Logger` (unchanged from
  today). On a hard load failure the header falls back to the **no-snap
  centered** petroglyph layout, so the page never shows an empty placeholder box
  indefinitely. (A still-loading snap keeps the placeholder; only a settled
  failure collapses to centered.)
- Palette miss (cache cold / bad row): `petroglyphColors` callers fall back to
  `Color.accent.primary` / `Color.accent.primaryHex`, same as today.

## Testing

- `BannerAspectTests`: nearest-bucket selection including a portrait source
  bucketing to **3:4**, and a wide source to **16:9**.
- `EmotionPalettePetroglyphColorsTests`: the #599 table — each intensity
  (small/medium/large) × scheme (light/dark) yields the correct stroke/backfill
  hex and fill opacity.
- Layout, tilt, cross-fade, and entry animation verified in the simulator
  (no UI tests per iOS conventions).

## Geometry (confirmed with requester; reconcile against Figma if access lands)

Figma node `554-27469` is not accessible to the agent. Values below are
approved from the web reference + current iOS banner:

- Petroglyph box: **120pt**.
- Petroglyph offset from top-trailing: **(x: +16, y: −16)**.
- Snap corner radius: **24**.
- Tilts: snap **−4°**, petroglyph **+7°**.
- Snap width: full content width (page padding 16pt).

## Out of scope

- Edit mode (later; will reuse `SnapPetroglyphHeader` with a dashed
  tap-to-upload placeholder state).
- Web and Android — separate follow-ups. The `shaded_color` / `dark_color`
  columns and view change land here but benefit those surfaces later.
- Timeline row petroglyph coloring.
