// The 3×3 valence grid as a pair of ordered axes, plus the index arithmetic the
// two-axis roll walks it with. Port of the `// MARK: - The roll` half of
// `apps/ios/Pebbles/Features/Path/Models/Valence.swift`.
//
// Kept off the components for the same reason iOS kept it off the view: the
// roll's interesting behaviour is stepping, clamping and the ladder order, and
// all three are assertable without a gesture (`valence.test.ts`).
//
// The cell is `(size, polarity)` rather than a nine-case enum because web has
// carried the two axes separately since the first composer — `intensity` and
// `valence` are what the reducer, the draft payload and the wire all speak, and
// a ninth name in the middle would be one more thing to keep in sync.

import {
  POLARITY_BY_VALENCE,
  SIZE_BY_INTENSITY,
  type Intensity,
  type Polarity,
  type Size,
  type Valence,
} from "@/lib/config/pebble-geometry"

/** One of the nine cells: how big the event was, and how it landed. */
export type ValenceCell = { size: Size; polarity: Polarity }

/** Left → right order of the polarity axis. */
export const POLARITY_ORDER: readonly Polarity[] = ["lowlight", "neutral", "highlight"]

/**
 * Top → bottom order of the size axis. Deliberately *not* small → large: the
 * ladder runs big at the top, so dragging up rolls toward smaller events the
 * way scrolling down a list whose big end is at the top does.
 */
export const SIZE_LADDER: readonly Size[] = ["large", "medium", "small"]

export const INTENSITY_BY_SIZE: Record<Size, Intensity> = { small: 1, medium: 2, large: 3 }

export const VALENCE_BY_POLARITY: Record<Polarity, Valence> = {
  lowlight: -1,
  neutral: 0,
  highlight: 1,
}

/** Every cell, small → large and lowlight → highlight within each ring. */
export const ALL_CELLS: readonly ValenceCell[] = (["small", "medium", "large"] as const).flatMap(
  (size) => POLARITY_ORDER.map((polarity) => ({ size, polarity })),
)

export function cellFrom(intensity: Intensity, valence: Valence): ValenceCell {
  return { size: SIZE_BY_INTENSITY[intensity], polarity: POLARITY_BY_VALENCE[valence] }
}

export function cellsEqual(a: ValenceCell, b: ValenceCell): boolean {
  return a.size === b.size && a.polarity === b.polarity
}

/** Cell key, for React lists and for the artwork/outline lookups. */
export function cellKey(cell: ValenceCell): string {
  return `${cell.size}-${cell.polarity}`
}

export function polarityIndex(cell: ValenceCell): number {
  return POLARITY_ORDER.indexOf(cell.polarity)
}

export function sizeIndex(cell: ValenceCell): number {
  return SIZE_LADDER.indexOf(cell.size)
}

function clamp(index: number, length: number): number {
  return index < 0 ? 0 : index > length - 1 ? length - 1 : index
}

/**
 * The cell at the given indices, **clamped** to the grid — the roll stops at
 * the edges rather than wrapping, so a hard swipe cannot loop the user past the
 * end and back to where they started.
 */
export function cellAt(polarity: number, size: number): ValenceCell {
  return {
    polarity: POLARITY_ORDER[clamp(polarity, POLARITY_ORDER.length)],
    size: SIZE_LADDER[clamp(size, SIZE_LADDER.length)],
  }
}

/**
 * The polarity one step to each side, null at the ends. Drives the faded
 * neighbour words the roll bleeds off each screen edge.
 */
export function polarityBefore(cell: ValenceCell): Polarity | null {
  const index = polarityIndex(cell)
  return index > 0 ? POLARITY_ORDER[index - 1] : null
}

export function polarityAfter(cell: ValenceCell): Polarity | null {
  const index = polarityIndex(cell)
  return index < POLARITY_ORDER.length - 1 ? POLARITY_ORDER[index + 1] : null
}
