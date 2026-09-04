import type { Pebble } from "@/lib/types"
import { SIZE_BY_INTENSITY } from "@/lib/config/pebble-geometry"

/**
 * A run of ordinary pebbles laid out as a masonry wall, or a single large one
 * that takes the full width.
 *
 * `grid` carries pre-dealt columns rather than a flat list: which column a card
 * lands in decides the reading order, and that is a layout rule worth testing
 * rather than something to re-derive in JSX.
 */
export type PathBlock =
  | { kind: "grid"; columns: Pebble[][] }
  | { kind: "large"; pebble: Pebble }

export const DEFAULT_COLUMNS = 2

/**
 * Group a week's pebbles into the progressive Path layout.
 *
 * Walks the input in the order given — chronological, as the caller supplies it.
 * Large pebbles break the wall: each one takes its own full-width block, and the
 * runs on either side become their own masonry sections. Nothing is ever
 * reordered, so the Path stays readable as a timeline.
 *
 * Cards are dealt round-robin (0 → col 0, 1 → col 1, 2 → col 0 …) rather than
 * height-balanced. Height-balancing needs measurement, and worse, it breaks
 * chronology: a short card would jump the queue to fill a gap, so two cards
 * side by side would no longer be neighbours in time. Round-robin keeps
 * left-then-right reading order matching the order things happened, which is
 * the whole point of a Path.
 *
 * The cost is that columns end at different heights when card heights differ.
 * That is the trade the wall accepts — a ragged bottom edge reads as a wall,
 * an out-of-order timeline reads as a bug.
 */
export function groupPebbles(
  pebbles: Pebble[],
  columnCount: number = DEFAULT_COLUMNS,
): PathBlock[] {
  const blocks: PathBlock[] = []
  let run: Pebble[] = []

  const flush = () => {
    if (run.length === 0) return
    const columns: Pebble[][] = Array.from({ length: columnCount }, () => [])
    run.forEach((pebble, i) => columns[i % columnCount].push(pebble))
    blocks.push({ kind: "grid", columns })
    run = []
  }

  for (const pebble of pebbles) {
    if (SIZE_BY_INTENSITY[pebble.intensity] === "large") {
      flush()
      blocks.push({ kind: "large", pebble })
      continue
    }
    run.push(pebble)
  }
  flush()

  return blocks
}
