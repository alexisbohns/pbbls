import { steady } from "@/lib/achievements/geometry"
import { familyRuns } from "@/lib/achievements/runs"
import type { Segment } from "@/lib/achievements/geometry"
import type { EmotionSeat } from "@/lib/types"

// The emotions rosace (#1308, the boards' `gem`): in a `side` square, one kite
// per served seat between the outer, middle and inner rings, and one star
// between two kites, seat 2.5 at twelve. Angles are radians from +x with y
// down. Seats are placed by their index in the served list. iOS
// `RosaceGeometry`, Android the same.

export type RosaceGeometry = {
  side: number
  centre: number
  outer: number
  mid: number
  inner: number
  step: number
  angle: (k: number) => number
  point: (r: number, a: number) => [number, number]
  /** Seat `k`'s kite: left shoulder, tip, right shoulder, heel. */
  kite: (k: number) => string
  /** The star between seats `k` and `k + 1`. */
  star: (k: number) => string
  /** The outline round every kite. */
  silhouette: string
  /** Every kite and star, for the "not yet" outline. */
  outline: string
  /** A family's boundary tick, half a step before its first seat, 3 to 9 outside. */
  tick: (start: number) => Segment
  /** A family's name: 22 outside, at its middle seat. */
  label: (start: number, length: number) => [number, number]
  /** A new emotion's dot, 6.5 outside its seat. */
  fresh: (k: number) => [number, number]
}

export function rosaceGeometry(side: number, count: number, labelled: boolean): RosaceGeometry {
  const centre = side / 2
  const outer = side * (labelled ? 0.345 : 0.44)
  const mid = outer * 0.85
  const inner = outer * 0.47
  const step = (2 * Math.PI) / Math.max(count, 1)
  const angle = (k: number) => -Math.PI / 2 + (k - 2.5) * step
  const point = (r: number, a: number): [number, number] => [steady(centre + Math.cos(a) * r), steady(centre + Math.sin(a) * r)]
  const at = (r: number, a: number) => {
    const [x, y] = point(r, a)
    return `${x.toFixed(2)},${y.toFixed(2)}`
  }
  const kite = (k: number) =>
    `M${at(mid, angle(k) - step / 2)}L${at(outer, angle(k))}L${at(mid, angle(k) + step / 2)}L${at(inner, angle(k))}Z`
  const star = (k: number) => `M${at(inner, angle(k))}L${at(mid, angle(k) + step / 2)}L${at(inner, angle(k + 1))}Z`
  let silhouette = ""
  let outline = ""
  for (let k = 0; k < count; k++) {
    silhouette += `${k ? "L" : "M"}${at(mid, angle(k) - step / 2)}L${at(outer, angle(k))}`
    outline += kite(k) + star(k)
  }
  return {
    side,
    centre,
    outer,
    mid,
    inner,
    step,
    angle,
    point,
    kite,
    star,
    silhouette: count > 0 ? `${silhouette}Z` : "",
    outline,
    tick: (start) => {
      const a = angle(start) - step / 2
      const [x1, y1] = point(outer + 3, a)
      const [x2, y2] = point(outer + 9, a)
      return { x1, y1, x2, y2 }
    },
    label: (start, length) => point(outer + 22, angle(start + (length - 1) / 2)),
    fresh: (k) => point(outer + 6.5, angle(k)),
  }
}

/** A seat's place in its family's ramp, 0 to 1 (the boards' `t`). */
export function rampPlace(index: number, length: number): number {
  return length > 1 ? index / (length - 1) : 0.5
}

export type Facet = {
  k: number
  seat: EmotionSeat
  /** Its family as served; null runs with its null neighbours. */
  family: string | null
  /** Its place in its family's ramp: the kite's tone. */
  place: number
  /** The star after it is lit: it and the next seat are met, in one family. */
  bridged: boolean
  /** The next seat's place: the lit star takes its tone, softened. */
  nextPlace: number
}

/** The served seats as the rosace lights them. */
export function facets(seats: readonly EmotionSeat[]): Facet[] {
  const placeOf: number[] = []
  for (const run of familyRuns(seats)) {
    for (let j = 0; j < run.length; j++) placeOf[run.start + j] = rampPlace(j, run.length)
  }
  return seats.map((seat, k) => {
    const n = (k + 1) % seats.length
    const next = seats[n]
    return {
      k,
      seat,
      family: seat.family,
      place: placeOf[k],
      bridged: seat.met && n !== k && next.met && next.family === seat.family,
      nextPlace: placeOf[n],
    }
  })
}

/**
 * A lit facet's tone over its family's palette variables (`--p`, `--s`, `--sh`),
 * the boards' `tone`: on Paper the secondary moved toward the shaded ink by 8 %
 * plus 72 % of its place, at Night toward the primary by 62 % of it; a soft one
 * (the star between two lit seats) 38 % toward the stock. CSS colours, so one
 * render serves both themes.
 */
export function facetTone(place: number, soft: boolean): { paper: string; night: string } {
  const paper = `color-mix(in oklab, var(--sh) ${Math.round(8 + place * 72)}%, var(--s))`
  const night = `color-mix(in oklab, var(--p) ${Math.round(place * 62)}%, var(--s))`
  const wash = (c: string) => `color-mix(in oklab, var(--spine-stock) 38%, ${c})`
  return soft ? { paper: wash(paper), night: wash(night) } : { paper, night }
}

/** A lit facet of a family this build cannot resolve: ink; its soft star, ink 38 % toward the stock (iOS). */
export const UNRESOLVED_KITE = "var(--spine-ink)"
export const UNRESOLVED_STAR = "color-mix(in oklab, var(--spine-stock) 38%, var(--spine-ink))"
