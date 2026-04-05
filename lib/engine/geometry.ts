import type { Point } from "./types"
import type { PRNG } from "./prng"

/**
 * Convert polar coordinates to Cartesian.
 */
export function polarToCartesian(
  cx: number,
  cy: number,
  radius: number,
  angleRad: number,
): Point {
  return {
    x: cx + radius * Math.cos(angleRad),
    y: cy + radius * Math.sin(angleRad),
  }
}

/**
 * Displace a point by a random offset in both axes, bounded by `amount`.
 * Uses the provided PRNG for deterministic results.
 */
export function displacePoint(
  point: Point,
  amount: number,
  rng: PRNG,
): Point {
  return {
    x: point.x + rng.nextFloat(-amount, amount),
    y: point.y + rng.nextFloat(-amount, amount),
  }
}

/**
 * Convert a closed polygon into a smooth SVG path using cubic Bézier curves.
 *
 * Uses Catmull-Rom to cubic Bézier conversion: for each segment P[i] → P[i+1],
 * control points are derived from adjacent vertices to produce smooth contours.
 */
export function bezierSmooth(vertices: Point[]): string {
  const n = vertices.length
  if (n < 3) {
    throw new Error("bezierSmooth requires at least 3 vertices")
  }

  const at = (i: number) => vertices[((i % n) + n) % n]

  const first = at(0)
  let d = `M${first.x},${first.y}`

  for (let i = 0; i < n; i++) {
    const p0 = at(i - 1)
    const p1 = at(i)
    const p2 = at(i + 1)
    const p3 = at(i + 2)

    const cp1x = p1.x + (p2.x - p0.x) / 6
    const cp1y = p1.y + (p2.y - p0.y) / 6
    const cp2x = p2.x - (p3.x - p1.x) / 6
    const cp2y = p2.y - (p3.y - p1.y) / 6

    d += ` C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`
  }

  d += " Z"
  return d
}
