// Builds one valence stone's two halves — the backdrop silhouette and the
// artwork inked inside it — and keeps them.
//
// Port of `apps/ios/Pebbles/Features/Path/Valence/ValenceArt.swift`, using the
// web wobble module's two entry points, which are the exact analogues of the
// ones iOS calls:
//
// - stroked centerlines go through `wobbleGlyphInk`, the leaky-outline pass a
//   carved glyph gets, at the width the source PDF drew them with;
// - already-filled regions (the fossil's spiral) go through `wobbleBackdrop`,
//   which displaces a region's contours. Ink a filled spiral as a centerline
//   and it fills in solid.
//
// Wobbling all nine artworks costs ~70ms once per process, so it is memoized
// here on top of the renderer's own content-keyed caches: the fan mounts, pays
// it on the first render, and never pays it again.
//
// Every part paints with the *inherited* paint rather than naming a colour, so
// one copy of the geometry can be drawn twice at two different fills — see
// `ValenceStone`, which crossfades a resting layer and a selected one through a
// pair of `<use>` elements.

import { getOutline } from "@/lib/config/pebble-outlines"
import { getValenceArt } from "@/lib/config/valence-art"
import type { Polarity, Size } from "@/lib/config/pebble-geometry"
import { WOBBLE_ENABLED, wobbleBackdrop, wobbleGlyphInk } from "@/lib/wobble"
import { ALL_CELLS } from "./valence"

/**
 * A filled shape: the leaky ink of a wobbled centerline, or a displaced region.
 * `strokeWidth: 0` because the `<use>` above it sets `stroke` as well as `fill`
 * (a gradient has to reach both kinds of part), and a filled polygon does not
 * want an outline on top of it.
 */
export type StoneArtPart =
  | { kind: "fill"; d: string; fillRule: "evenodd" | "nonzero" }
  | { kind: "stroke"; d: string; width: number }

export type StoneArt = {
  /** The silhouette behind the artwork, in the outline asset's own viewBox. */
  backdrop: { d: string; fillRule: "evenodd" | "nonzero"; width: number; height: number }
  /** The artwork, in its own (smaller) viewBox. */
  artwork: { parts: StoneArtPart[]; width: number; height: number }
}

const cache = new Map<string, StoneArt>()

export function stoneArt(size: Size, polarity: Polarity): StoneArt {
  const key = `${size}-${polarity}`
  const cached = cache.get(key)
  if (cached) return cached
  const built = build(size, polarity)
  cache.set(key, built)
  return built
}

function build(size: Size, polarity: Polarity): StoneArt {
  const outline = getOutline(size, polarity)
  const art = getValenceArt(size, polarity)

  // Closed-contour displacement of the silhouette, exactly as
  // `PebbleOutlineBackdrop` does it. Falls back to the flat path on a parse
  // failure rather than losing the stone.
  const wobbledOutline = WOBBLE_ENABLED
    ? wobbleBackdrop(outline.path, outline.width, outline.height)
    : null

  const parts: StoneArtPart[] = []
  for (const stroke of art.strokes) {
    const ink = WOBBLE_ENABLED ? wobbleGlyphInk(stroke.d, stroke.width) : null
    parts.push(
      ink
        ? { kind: "fill", d: ink, fillRule: "nonzero" }
        : // Wobble off, or a `d` that would not parse: the centerline draws as
          // the plain stroke it already is. The line weight is the artwork's
          // own, never the uniform pebble weight — these live in a ~190-unit
          // box with fine detail, which the heavier weight merges.
          { kind: "stroke", d: stroke.d, width: stroke.width },
    )
  }
  for (const region of art.regions) {
    const displaced = WOBBLE_ENABLED ? wobbleBackdrop(region.d, art.width, art.height) : null
    parts.push({ kind: "fill", d: displaced ?? region.d, fillRule: region.fillRule })
  }

  return {
    backdrop: {
      d: wobbledOutline ?? outline.path,
      fillRule: outline.fillRule,
      width: outline.width,
      height: outline.height,
    },
    artwork: { parts, width: art.width, height: art.height },
  }
}

/**
 * Builds all nine ahead of the fan needing them, one per macrotask.
 *
 * Wobbling the whole set costs ~70ms on a laptop and several times that on a
 * phone — a visible hitch if it lands the moment the valence step appears. The
 * flow calls this on mount, which is three steps of runway. Spread across ticks
 * rather than done in one pass so the prewarm itself never blocks a frame, and
 * safe to call twice: the cache absorbs the second.
 *
 * Returns a cancel function for the caller's cleanup.
 */
export function prewarmValenceArt(): () => void {
  const cells = ALL_CELLS
  let index = 0
  let handle: ReturnType<typeof setTimeout>

  const tick = () => {
    const cell = cells[index++]
    if (!cell) return
    stoneArt(cell.size, cell.polarity)
    handle = setTimeout(tick, 0)
  }

  handle = setTimeout(tick, 0)
  return () => clearTimeout(handle)
}
