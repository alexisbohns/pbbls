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

/** Whether a point lies inside the region: its rings nest (an outer ring, its holes, an island in a hole), so an odd count; on an edge counts as inside. */
function contains(region: IntPath[], pt: { X: number; Y: number }): boolean {
  let containing = 0
  for (const ring of region) {
    const where = ClipperLib.Clipper.PointInPolygon(pt, ring)
    if (where === -1) return true
    if (where === 1) containing += 1
  }
  return containing % 2 === 1
}

/** Points along `d`, one at least every half unit, so a long straight segment weighs as much as a curve. */
function samples(d: string): { X: number; Y: number }[] {
  return flattenPath(d).flatMap((s) =>
    s.points.flatMap(([x, y], i) => {
      const prev = s.points[i - 1]
      if (!prev) return toInt([[x, y]])
      const steps = Math.max(1, Math.ceil(Math.hypot(x - prev[0], y - prev[1]) / 0.5))
      return toInt(Array.from({ length: steps }, (_, n): Pt => [prev[0] + ((x - prev[0]) * (n + 1)) / steps, prev[1] + ((y - prev[1]) * (n + 1)) / steps]))
    }),
  )
}

/** The share of `d`'s sampled points inside the region. */
function shareInside(d: string, region: IntPath[]): number {
  const pts = samples(d)
  return pts.length === 0 ? 0 : pts.filter((pt) => contains(region, pt)).length / pts.length
}

const inside = (d: string, region: IntPath[]): boolean => shareInside(d, region) === 1

export type Carved = {
  /** One compound path, filled even-odd with no stroke. */
  d: string
  /** Indices (into `others`) of the paths outside the body: drawn stroked on top. */
  keep: number[]
  /** Indices (into `holes`) of the holes outside the body: not cut, drawn filled on top. */
  outside: number[]
  /** Filled area in square glyph units. */
  area: number
}

/** A path as SVG paints it filled and stroked: its fill (each subpath implicitly closed) plus its stroke band. */
function paintedRegion(d: string): IntPath[] {
  const subs = flattenPath(d)
  const fill = boolean(ClipperLib.ClipType.ctUnion, subs.filter((s) => s.points.length >= 3).map((s) => toInt(s.points)), [])
  const band = subs.flatMap((s) => {
    const end = isClosed(s) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
    return offset([toInt(s.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE)
  })
  return boolean(ClipperLib.ClipType.ctUnion, fill, band)
}

/**
 * The solid form (spec D3, D8), as SVG paints a path that is filled and stroked:
 * the body's subpaths, each implicitly closed for the fill and unioned, plus
 * their stroke band (a line offset by half the stroke), minus every other path
 * inside the fill or standing on its edge (a door on the ground line, not a
 * rim traced along the outline), outlined at the stroke width with round caps, and minus every hole
 * there (the mark of a marked solid) as it is painted, fill and stroke.
 */
export function carveSolid(body: readonly string[], others: readonly string[], holes: readonly string[] = []): Carved {
  const bodySubs = body.map((d) => flattenPath(d).filter((s) => s.points.length >= 3))
  const subs = bodySubs.flat()
  // SVG fills each path on its own (non-zero over that path's subpaths), so two paths
  // wound in opposite directions must not cancel: fill per path, then union the regions.
  // Clipper returns outer rings and holes with opposite, normalised orientations, so the
  // final non-zero union keeps a hole open unless another path covers it.
  const regions = bodySubs.flatMap((paths) => boolean(ClipperLib.ClipType.ctUnion, paths.map((s) => toInt(s.points)), []))
  const fill = boolean(ClipperLib.ClipType.ctUnion, regions, [])
  const band = subs.flatMap((s) => {
    const end = isClosed(s) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
    return offset([toInt(s.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE)
  })
  const base = boolean(ClipperLib.ClipType.ctUnion, fill, band)
  // A line inside the fill is cut. So is one standing on its edge (a door on the ground line):
  // within half a stroke of the fill, with at least half of it deeper than half a stroke inside.
  // A line traced along the outline (a bowl's rim) is mostly on the edge, so it stays stroked.
  const reach = offset(fill, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, HALF_STROKE)
  const core = offset(fill, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, -HALF_STROKE)
  const carves = (d: string) => inside(d, fill) || (inside(d, reach) && shareInside(d, core) >= 0.5)
  const keep: number[] = []
  const cutters: IntPath[] = []
  others.forEach((d, i) => {
    if (!carves(d)) {
      keep.push(i)
      return
    }
    for (const sub of flattenPath(d)) {
      const end = isClosed(sub) ? ClipperLib.EndType.etClosedLine : ClipperLib.EndType.etOpenRound
      cutters.push(...offset([toInt(sub.points)], ClipperLib.JoinType.jtRound, end, HALF_STROKE))
    }
  })
  const outside: number[] = []
  holes.forEach((d, i) => {
    if (!inside(d, reach)) outside.push(i)
    else cutters.push(...paintedRegion(d))
  })
  const result = cutters.length > 0 ? boolean(ClipperLib.ClipType.ctDifference, base, boolean(ClipperLib.ClipType.ctUnion, cutters, [])) : base
  const area = result.reduce((sum, ring) => sum + ClipperLib.Clipper.Area(ring), 0) / (SCALE * SCALE)
  const d = result
    .filter((ring) => ring.length > 2)
    .map((ring) => ring.map((pt, i) => `${i === 0 ? "M" : "L"}${fmt(pt.X)} ${fmt(pt.Y)}`).join(" ") + " Z")
    .join(" ")
  return { d, keep, outside, area: Math.abs(area) }
}
