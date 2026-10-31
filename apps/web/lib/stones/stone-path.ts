import { resample, stoneOutline, type Point, type StoneOptions } from "./stone-outline"

/**
 * SVG path data for a closed outline (#1384, spec D7): resampled every 4 units,
 * then a closed Catmull-Rom spline as cubic Béziers, one decimal per coordinate.
 */
export function toPathData(points: readonly Point[]): string {
  const p = resample(points, 4)
  const n = p.length
  const f = (v: number) => v.toFixed(1)
  let d = `M${f(p[0][0])} ${f(p[0][1])}`
  for (let i = 0; i < n; i++) {
    const p0 = p[(i - 1 + n) % n]
    const p1 = p[i]
    const p2 = p[(i + 1) % n]
    const p3 = p[(i + 2) % n]
    d +=
      `C${f(p1[0] + (p2[0] - p0[0]) / 6)} ${f(p1[1] + (p2[1] - p0[1]) / 6)} ` +
      `${f(p2[0] - (p3[0] - p1[0]) / 6)} ${f(p2[1] - (p3[1] - p1[1]) / 6)} ${f(p2[0])} ${f(p2[1])}`
  }
  return `${d}Z`
}

/** Path data for one stone exactly `w × h`. */
export function stonePath(w: number, h: number, seed: string, opt?: StoneOptions): string {
  return toPathData(stoneOutline(w, h, seed, opt))
}
