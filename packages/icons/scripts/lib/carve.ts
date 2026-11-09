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

/**
 * True when every sampled point of `d` lies inside (or on the edge of) the region.
 * The region's rings nest (an outer ring, its holes, an island in a hole), so a point
 * is inside when an odd number of rings contain it; on a ring's edge counts as inside.
 */
function inside(d: string, region: IntPath[]): boolean {
  return flattenPath(d)
    .flatMap((s) => toInt(s.points))
    .every((pt) => {
      let containing = 0
      for (const ring of region) {
        const where = ClipperLib.Clipper.PointInPolygon(pt, ring)
        if (where === -1) return true
        if (where === 1) containing += 1
      }
      return containing % 2 === 1
    })
}

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
 * lying within half a stroke of the fill (a door standing on the ground line
 * counts), outlined at the stroke width with round caps, and minus every hole
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
  // A line counts as inside when it lies within half a stroke of the fill.
  const reach = offset(fill, ClipperLib.JoinType.jtRound, ClipperLib.EndType.etClosedPolygon, HALF_STROKE)
  const keep: number[] = []
  const cutters: IntPath[] = []
  others.forEach((d, i) => {
    if (!inside(d, reach)) {
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
