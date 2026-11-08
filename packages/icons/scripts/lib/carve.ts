import { ClipperLib, SCALE } from "./clipper"
import { flattenPath, isClosed, type Pt } from "./geometry"

/** Half the 1.75 stroke: the body grows by it, inner lines are outlined by it. */
const HALF_STROKE = 0.875
/** Round joins and caps are approximated within this many glyph units. */
const ARC_TOLERANCE = 0.01

type IntPath = { X: number; Y: number }[]

const toInt = (points: Pt[]): IntPath => points.map(([x, y]) => ({ X: Math.round(x * SCALE), Y: Math.round(y * SCALE) }))
const fmt = (n: number): string => (Math.round((n / SCALE) * 100) / 100).toString()

function offset(paths: IntPath[], join: number, end: number, delta: number): IntPath[] {
  const co = new ClipperLib.ClipperOffset(2, ARC_TOLERANCE * SCALE)
  co.AddPaths(paths, join, end)
  const out: IntPath[] = []
  co.Execute(out, delta * SCALE)
  return out
}

function boolean(type: number, subject: IntPath[], clip: IntPath[]): IntPath[] {
  const c = new ClipperLib.Clipper()
  c.AddPaths(subject, ClipperLib.PolyType.ptSubject, true)
  c.AddPaths(clip, ClipperLib.PolyType.ptClip, true)
  const out: IntPath[] = []
  c.Execute(type, out, ClipperLib.PolyFillType.pftNonZero, ClipperLib.PolyFillType.pftNonZero)
  return out
}

/** True when every sampled point of `d` lies inside (or on) the region. */
function inside(d: string, region: IntPath[]): boolean {
  return flattenPath(d)
    .flatMap((s) => toInt(s.points))
    .every((pt) => region.some((ring) => ClipperLib.Clipper.PointInPolygon(pt, ring) !== 0))
}

export type Carved = {
  /** One compound path, filled even-odd with no stroke. */
  d: string
  /** Indices (into `others`) of the paths outside the body: drawn stroked on top. */
  keep: number[]
  /** Filled area in square glyph units. */
  area: number
}

/**
 * The solid form (spec D3, D8): the body's closed subpaths unioned and grown
 * by half the stroke, minus every other path inside the body outlined at the
 * stroke width with round caps.
 */
export function carveSolid(body: readonly string[], others: readonly string[]): Carved {
  const rings = body.flatMap((d) => flattenPath(d)).filter(isClosed).map((s) => toInt(s.points))
  const region = boolean(ClipperLib.ClipType.ctUnion, rings, [])
  const grown = offset(region, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, HALF_STROKE)
  const keep: number[] = []
  const cutters: IntPath[] = []
  others.forEach((d, i) => {
    if (!inside(d, region)) {
      keep.push(i)
      return
    }
    for (const sub of flattenPath(d)) {
      const end = isClosed(sub) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
      cutters.push(...offset([toInt(sub.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE))
    }
  })
  const result = cutters.length > 0 ? boolean(ClipperLib.ClipType.ctDifference, grown, cutters) : grown
  const area = result.reduce((sum, ring) => sum + ClipperLib.Clipper.Area(ring), 0) / (SCALE * SCALE)
  const d = result
    .filter((ring) => ring.length > 2)
    .map((ring) => ring.map((pt, i) => `${i === 0 ? "M" : "L"}${fmt(pt.X)} ${fmt(pt.Y)}`).join(" ") + " Z")
    .join(" ")
  return { d, keep, area: Math.abs(area) }
}
