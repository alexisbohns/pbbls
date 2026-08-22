import type { Pebble } from "@/lib/types"
import { SIZE_BY_INTENSITY, type Size } from "@/lib/config/pebble-geometry"

/**
 * One run of same-sized pebbles, in the order the caller supplied them.
 *
 * `medium` pre-chunks its run into rows of at most two rather than leaving that
 * to the renderer: an odd trailing pebble is laid out differently from a paired
 * one (half width, centred), and deciding that at render time means the rule
 * lives in JSX where it cannot be tested.
 */
export type PathBlock =
  | { kind: "small"; pebbles: Pebble[] }
  | { kind: "medium"; rows: Pebble[][] }
  | { kind: "large"; pebble: Pebble }

/**
 * Group a week's pebbles into the progressive Path layout.
 *
 * Walks the input in the order given — chronological, as the caller supplies it —
 * and cuts a new block whenever intensity changes. Order is never rearranged:
 * the Path has to stay readable as a timeline, so packing the grid more tightly
 * by sorting on size is deliberately not on the table.
 *
 * Larges get one block each rather than a run: two full-width cards in a row is
 * just two full-width cards, and a `Pebble[]` there would imply a grouping that
 * has no visual meaning.
 */
export function groupPebbles(pebbles: Pebble[]): PathBlock[] {
  const blocks: PathBlock[] = []

  for (const pebble of pebbles) {
    const size: Size = SIZE_BY_INTENSITY[pebble.intensity]

    if (size === "large") {
      blocks.push({ kind: "large", pebble })
      continue
    }

    const last = blocks.at(-1)

    if (size === "small") {
      if (last?.kind === "small") last.pebbles.push(pebble)
      else blocks.push({ kind: "small", pebbles: [pebble] })
      continue
    }

    // medium — extend the open row if it still has a free slot, else start one
    if (last?.kind === "medium") {
      const openRow = last.rows.at(-1)
      if (openRow && openRow.length < 2) openRow.push(pebble)
      else last.rows.push([pebble])
    } else {
      blocks.push({ kind: "medium", rows: [[pebble]] })
    }
  }

  return blocks
}
