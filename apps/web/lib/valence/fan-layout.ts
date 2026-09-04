// Geometry of the valence fan: where each of the nine stones sits, and how big
// it is. Port of `apps/ios/Pebbles/Features/Path/Valence/ValenceFanLayout.swift`
// — every constant below is copied from it, so the two surfaces read as the
// same arrangement.
//
// Everything is expressed in a fixed **reference canvas** (`FAN_REFERENCE`).
// iOS renders that canvas at its authored size and gains side margin on wider
// phones, because deriving a scale there needs a `GeometryReader` whose ideal
// height is unspecified. CSS has no such problem: the fan holds the reference
// aspect ratio, positions are percentages of it, and the whole thing scales
// uniformly to whatever width it is given (capped at the reference width, so a
// desktop gets iOS's side margin rather than a blown-up fan).
//
// Two consequences worth stating: the fan reads identically at every width, and
// the layout is verifiable with plain arithmetic instead of a snapshot — see
// `fan-layout.test.ts` for the bounds and ordering invariants.
//
// The arrangement is a fan rather than a polar sweep. Polarity picks the
// horizontal half-spread (lowlight left, neutral centre, highlight right) and
// size picks the height off the bottom edge, with the centre stone of each ring
// lifted a little above its two siblings. Stones grow and their gaps widen as
// the fan rises, so the eye reads "bigger event" going up.

import { outlineAspectRatio, type Size } from "@/lib/config/pebble-geometry"
import type { ValenceCell } from "./valence"

/**
 * The canvas every constant below is expressed in. Authored on iOS as exactly
 * the content width of the narrowest supported device (375pt − 2 × `Spacing.lg`).
 */
export const FAN_REFERENCE = { width: 341, height: 324 } as const

/**
 * Stone height in reference units. Width follows from the silhouette's own
 * aspect ratio, so each stone keeps its real proportions — small is wider than
 * tall, large is taller than wide.
 */
export function stoneHeight(size: Size): number {
  switch (size) {
    case "small":
      return 40
    case "medium":
      return 71
    case "large":
      return 104
  }
}

export function stoneWidth(size: Size): number {
  return stoneHeight(size) * outlineAspectRatio(size)
}

/** Distance from the canvas's horizontal centre to a side stone's centre. */
function halfSpread(size: Size): number {
  switch (size) {
    case "small":
      return 62
    case "medium":
      return 90
    case "large":
      return 111
  }
}

/**
 * Sideways nudge applied to a whole ring, so the three rings do not stack into
 * a column grid. Small enough to read as a hand-strewn arrangement rather than
 * as a wonky one; symmetric within a ring, so it never disturbs the lowlight →
 * highlight ordering.
 */
function ringDrift(size: Size): number {
  switch (size) {
    case "small":
      return 9
    case "medium":
      return -7
    case "large":
      return 5
  }
}

/** Height of a side stone's centre above the canvas's bottom edge. */
function sideRise(size: Size): number {
  switch (size) {
    case "small":
      return 31
    case "medium":
      return 109
    case "large":
      return 225
  }
}

/**
 * Extra rise given to the ring's centre (neutral) stone, which is what bows
 * each row into an arc instead of a straight line.
 */
function centreLift(size: Size): number {
  switch (size) {
    case "small":
      return 12
    case "medium":
      return 24
    case "large":
      return 36
  }
}

/** Centre of a stone in reference space, origin top-left. */
export function stoneCentre(cell: ValenceCell): { x: number; y: number } {
  const { size, polarity } = cell
  const rise = sideRise(size) + (polarity === "neutral" ? centreLift(size) : 0)
  const spread =
    polarity === "lowlight" ? -halfSpread(size) : polarity === "highlight" ? halfSpread(size) : 0

  return {
    x: FAN_REFERENCE.width / 2 + spread + ringDrift(size),
    y: FAN_REFERENCE.height - rise,
  }
}

/** Bounding box of a stone in reference space. Used by the bounds invariant. */
export function stoneFrame(cell: ValenceCell): {
  x: number
  y: number
  width: number
  height: number
} {
  const centre = stoneCentre(cell)
  const width = stoneWidth(cell.size)
  const height = stoneHeight(cell.size)
  return { x: centre.x - width / 2, y: centre.y - height / 2, width, height }
}

/** A stone's placement as CSS percentages of the reference canvas. */
export function stonePlacement(cell: ValenceCell): {
  left: string
  top: string
  width: string
  height: string
} {
  const centre = stoneCentre(cell)
  const pct = (value: number, of: number) => `${(100 * value) / of}%`
  return {
    left: pct(centre.x, FAN_REFERENCE.width),
    top: pct(centre.y, FAN_REFERENCE.height),
    width: pct(stoneWidth(cell.size), FAN_REFERENCE.width),
    height: pct(stoneHeight(cell.size), FAN_REFERENCE.height),
  }
}
