import { outlineFrom, resample, type Point, type StoneOptions } from "./stone-outline"
import { toPathData } from "./stone-path"
import { jitter, stoneRandom } from "./stone-random"

/**
 * One rock cut into `n` pieces by parallel leaning cuts (#1384, spec D7). For
 * `n = 3` with the default seed this is iOS `ShortcutStones.splitRow`, the
 * shipping quick actions row, line for line; any `n ≥ 2` works, so the counters
 * and the first-five stepper are "cut from one stone" too. Ported from the
 * 2026-10-31 exploration kit (`stones.js`), which produced the golden prefixes
 * in `stone-split.test.ts`.
 *
 * Each piece is the rock intersected with the half-spaces of its cuts, every
 * intersection rounded by the fillet, traced by 180 rays from the piece's centre
 * with 16 bisections each over that distance field.
 */
export const DEFAULT_SPLIT_SEED = "camera|fixed|rock|q11"

type Sdf = (x: number, y: number) => number

function polygonSdf(points: readonly Point[]): Sdf {
  return (qx, qy) => {
    let best = Infinity
    let inside = false
    for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
      const a = points[j]
      const b = points[i]
      const ex = b[0] - a[0]
      const ey = b[1] - a[1]
      const wx = qx - a[0]
      const wy = qy - a[1]
      const length2 = ex * ex + ey * ey || 1
      const t = Math.max(0, Math.min(1, (wx * ex + wy * ey) / length2))
      const dx = wx - ex * t
      const dy = wy - ey * t
      best = Math.min(best, dx * dx + dy * dy)
      if (a[1] > qy !== b[1] > qy && qx < ((b[0] - a[0]) * (qy - a[1])) / (b[1] - a[1]) + a[0]) inside = !inside
    }
    return (inside ? -1 : 1) * Math.sqrt(best)
  }
}

/** The rounded intersection of two distance fields. */
function roundIntersection(a: number, b: number, r: number): number {
  return Math.min(-r, Math.max(a, b)) + Math.hypot(Math.max(r + a, 0), Math.max(r + b, 0))
}

export function splitStone(
  w: number,
  h: number,
  n: number,
  seed: string = DEFAULT_SPLIT_SEED,
  opt: StoneOptions = {},
): string[] {
  const gutter = opt.gutter ?? 8
  const fillet = opt.fillet ?? 20
  // iOS's guard: nothing to cut, or no room left for the pieces.
  if (n < 2 || w <= (n - 1) * gutter) return Array.from({ length: Math.max(n, 0) }, () => "")

  const next = stoneRandom(seed)
  // The rock consumes the first nine draws of the shared generator, as in the app.
  const rock = outlineFrom(w, h, next, opt)
  const lean = opt.lean ?? 0.11 + jitter(next(), 0.03)
  const rockSdf = polygonSdf(resample(rock, 4))
  const width = (w - (n - 1) * gutter) / n
  const cutsAt = Array.from({ length: n - 1 }, (_, k) => (k + 1) * width + (k + 0.5) * gutter)
  const length = Math.hypot(lean * h, -h)
  const nx = h / length
  const ny = (lean * h) / length
  const cut = (x0: number, side: number): Sdf => (qx, qy) => side * ((qx - x0) * nx + (qy - h / 2) * ny) + gutter / 2
  const reach = Math.hypot(w, h)

  const pieces: string[] = []
  for (let k = 0; k < n; k++) {
    const cuts: Sdf[] = []
    if (k > 0) cuts.push(cut(cutsAt[k - 1], -1))
    if (k < n - 1) cuts.push(cut(cutsAt[k], 1))
    const cx = k * (width + gutter) + width / 2
    const cy = h / 2
    const sdf: Sdf = (qx, qy) => cuts.reduce((acc, c) => roundIntersection(acc, c(qx, qy), fillet), rockSdf(qx, qy))
    const traced: Point[] = []
    for (let i = 0; i < 180; i++) {
      const theta = Math.PI * 1.12 + (2 * Math.PI * i) / 180
      const dx = Math.cos(theta)
      const dy = Math.sin(theta)
      let lo = 0
      let hi = reach
      for (let s = 0; s < 16; s++) {
        const mid = (lo + hi) / 2
        if (sdf(cx + dx * mid, cy + dy * mid) < 0) lo = mid
        else hi = mid
      }
      traced.push([cx + dx * lo, cy + dy * lo])
    }
    pieces.push(toPathData(traced))
  }
  return pieces
}
