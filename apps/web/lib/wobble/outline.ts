// Converts flattened centerline polylines into wobbled filled outlines, and
// displaces already-filled silhouettes. Faithful port of the iOS
// `WobbleOutlineBuilder`, but emitting SVG `d` strings instead of CGPaths.
//
// The leaky ink: offset both stroke edges from the centerline first, then
// displace every contour point independently. The independent displacement is
// what makes the width breathe ("leaky") — a stroked wobbled centerline would
// stay constant-width.

import type { Point } from "./types"
import type { Polyline } from "./flatten"
import { displace, type WobbleParams } from "./params"
import type { SVGTurbulence } from "./turbulence"

// End caps are semicircles of `CAP_SEGMENTS` arcs (CAP_SEGMENTS − 1 interior
// points), matching the playground.
const CAP_SEGMENTS = 6
// A zero-length polyline (single-tap carve dot) becomes a circle with this many
// segments — the current renderer's round-cap dot, kept visible.
const DOT_SEGMENTS = 12

/** Per-point normals from neighbor tangents; endpoints use their single
 * adjacent segment, closed rings wrap cyclically. */
function normals(points: Point[], closed: boolean): Point[] {
  const count = points.length
  const result: Point[] = []
  for (let i = 0; i < count; i++) {
    const prev = closed ? points[(i - 1 + count) % count] : points[Math.max(0, i - 1)]
    const next = closed ? points[(i + 1) % count] : points[Math.min(count - 1, i + 1)]
    const tx = next.x - prev.x
    const ty = next.y - prev.y
    const rawLength = Math.hypot(tx, ty)
    const length = rawLength === 0 ? 1 : rawLength // playground: `|| 1`
    result.push({ x: -ty / length, y: tx / length })
  }
  return result
}

/** Appends the interior points of a semicircular end cap around `center`,
 * starting from `from` and bulging along the tangent direction. */
function appendCapArc(
  contour: Point[],
  center: Point,
  from: Point,
  halfWidth: number,
  tangentX: number,
  tangentY: number,
): void {
  const cx = center.x
  const cy = center.y
  const startAngle = Math.atan2(from.y - cy, from.x - cx)
  const midAngle = startAngle + Math.PI / 2
  const direction = Math.cos(midAngle) * tangentX + Math.sin(midAngle) * tangentY >= 0 ? 1 : -1
  for (let step = 1; step < CAP_SEGMENTS; step++) {
    const angle = startAngle + direction * ((Math.PI * step) / CAP_SEGMENTS)
    contour.push({ x: cx + halfWidth * Math.cos(angle), y: cy + halfWidth * Math.sin(angle) })
  }
}

/** Un-displaced outline contours for one polyline. */
export function contours(polyline: Polyline, halfWidth: number): Point[][] {
  const points = polyline.points
  const count = points.length

  if (count === 1) {
    const { x: cx, y: cy } = points[0]
    const circle: Point[] = []
    for (let i = 0; i < DOT_SEGMENTS; i++) {
      const angle = (2 * Math.PI * i) / DOT_SEGMENTS
      circle.push({ x: cx + halfWidth * Math.cos(angle), y: cy + halfWidth * Math.sin(angle) })
    }
    return [circle]
  }

  const norms = normals(points, polyline.isClosed)
  const left = points.map((p, i) => ({ x: p.x + norms[i].x * halfWidth, y: p.y + norms[i].y * halfWidth }))
  const right = points.map((p, i) => ({ x: p.x - norms[i].x * halfWidth, y: p.y - norms[i].y * halfWidth }))

  if (polyline.isClosed) {
    // Outer ring + reversed inner ring: opposite windings make the pair render
    // as an annulus under nonzero fill.
    return [left, [...right].reverse()]
  }

  const contour = [...left]
  appendCapArc(
    contour,
    points[count - 1],
    left[count - 1],
    halfWidth,
    points[count - 1].x - points[count - 2].x,
    points[count - 1].y - points[count - 2].y,
  )
  contour.push(...[...right].reverse())
  appendCapArc(
    contour,
    points[0],
    right[0],
    halfWidth,
    points[0].x - points[1].x,
    points[0].y - points[1].y,
  )
  return [contour]
}

// ── Serialization ───────────────────────────────────────────────────

function fmt(n: number): string {
  // 3 decimals is well under a sub-pixel at display scale and keeps the emitted
  // `d` compact. Avoids `-0` and trailing zeros.
  const r = Math.round(n * 1000) / 1000
  return Object.is(r, -0) ? "0" : String(r)
}

function closedPolygonToD(points: Point[]): string {
  if (points.length === 0) return ""
  let d = `M${fmt(points[0].x)} ${fmt(points[0].y)}`
  for (let i = 1; i < points.length; i++) d += `L${fmt(points[i].x)} ${fmt(points[i].y)}`
  return d + "Z"
}

/**
 * The leaky filled ink for a set of stroke centerlines: build outline contours,
 * displace every point independently, emit as closed subpaths. Returns "" when
 * nothing renders.
 */
export function buildInk(
  polylines: Polyline[],
  halfWidth: number,
  noise: SVGTurbulence,
  params: WobbleParams,
): string {
  let d = ""
  for (const polyline of polylines) {
    for (const contour of contours(polyline, halfWidth)) {
      if (contour.length <= 2) continue
      d += closedPolygonToD(contour.map((p) => displace(p, noise, params)))
    }
  }
  return d
}

/**
 * Displaces already-filled silhouette contours (backdrops, fossils) — no
 * outline building: the region is already a fill, so wobbling its edge is the
 * whole effect. Each subpath is displaced and closed. Returns "" when nothing
 * renders.
 */
export function displaceFilledContours(
  polylines: Polyline[],
  noise: SVGTurbulence,
  params: WobbleParams,
): string {
  let d = ""
  for (const polyline of polylines) {
    const displaced = polyline.points.map((p) => displace(p, noise, params))
    if (displaced.length <= 2) continue
    d += closedPolygonToD(displaced)
  }
  return d
}
