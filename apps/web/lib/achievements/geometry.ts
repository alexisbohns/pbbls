import { hashUUID } from "@/lib/engine/seed"

// The achievements figures' shared geometry (#1308), ported from the approved
// boards' generators (`ach/final/_build/app.js`). Angles are degrees clockwise
// from twelve o'clock and y grows downward, as in SVG. Nothing here reads the
// payload: it turns served positions (a seat's index, a level, a progress)
// into coordinates. iOS: `ExplorerWheelGeometry.point`; Android: the same.

export type Segment = { x1: number; y1: number; x2: number; y2: number }

/** One decimal, as the boards print coordinates. */
export function r1(n: number): number {
  return Math.round(n * 10) / 10
}

/**
 * A trigonometric result rounded to a millionth. `Math.sin` and `Math.cos`
 * may differ in their last bit between the server's engine and the
 * browser's, which would make a server-rendered figure fail to hydrate; a
 * millionth is far below anything drawn, and the same on both.
 */
export function steady(n: number): number {
  return Math.round(n * 1e6) / 1e6
}

/** The boards' `polar`: a point at radius `r` and `deg` degrees clockwise from twelve. */
export function polar(r: number, deg: number): [number, number] {
  const a = (deg * Math.PI) / 180
  return [steady(r * Math.sin(a)), steady(-r * Math.cos(a))]
}

/** The boards' `arcD`: a circular arc from `a0` to `a1` degrees, coordinates at one decimal. */
export function arcPath(r: number, a0: number, a1: number): string {
  const [x0, y0] = polar(r, a0)
  const [x1, y1] = polar(r, a1)
  const sweep = a1 - a0
  return `M${r1(x0)} ${r1(y0)}A${r} ${r} 0 ${Math.abs(sweep) > 180 ? 1 : 0} ${sweep > 0 ? 1 : 0} ${r1(x1)} ${r1(y1)}`
}

/**
 * The boards' stable hash in [0, 1): FNV-1a (the stones' crust hash), modulo
 * 100 000. iOS `ShardBandGeometry.unit`, Android the same, so a seam leans the
 * same way on every device.
 */
export function unitHash(key: string): number {
  return (hashUUID(key) % 100000) / 100000
}
