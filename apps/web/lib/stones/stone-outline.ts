import { jitter, stoneRandom } from "./stone-random"

/**
 * The Pebbles stone outline (#1384, spec D7), ported from iOS
 * `ShortcutStones.rockOutline` through the exploration kit: four unequal
 * superelliptic corners (fullest top-left) joined by gently bowed sides,
 * fitted exactly to `w × h`.
 */
export type Point = readonly [number, number]

export type StoneOptions = {
  /** Base corner radius (34). Each corner is a share of it, ±10 %. */
  corner?: number
  /** Scale of the sides' bulge (1). */
  bow?: number
  /** Lean of a split's cuts (0.11 ± 0.03 when absent). */
  lean?: number
  /** Gap between the pieces of a split (8). */
  gutter?: number
  /** Radius rounding each cut into the rock (20). */
  fillet?: number
}

const CORNERS = [1.15, 0.95, 0.8, 1.05] as const
const BOWS = [1.0, 1.4, 0.4, 1.4] as const

function superelliptic(v: number, n: number): number {
  return v === 0 ? 0 : Math.sign(v) * Math.pow(Math.abs(v), 2 / n)
}

/**
 * The outline drawn from a generator the caller owns: nine draws (four corners,
 * four bows, the exponent). A split shares its generator with the rock, as iOS does.
 */
export function outlineFrom(w: number, h: number, next: () => number, opt: StoneOptions = {}): Point[] {
  const base = opt.corner ?? 34
  const bowScale = opt.bow ?? 1
  const most = Math.min(w, h) / 2
  const radii = CORNERS.map((c) => Math.min(most, base * c * (1 + jitter(next(), 0.1))))
  const bows = BOWS.map((b) => (b + jitter(next(), 0.3)) * bowScale)
  const exponent = 2.5 + jitter(next(), 0.15)
  const points: [number, number][] = []

  const side = (p0: Point, p1: Point, nx: number, ny: number, bow: number) => {
    const length = Math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    const count = Math.max(2, Math.ceil(length / 1.5))
    const amplitude = bow * Math.min(1.6, length / 70)
    for (let i = 0; i < count; i++) {
      const t = i / count
      const offset = amplitude * Math.sin(Math.PI * t)
      points.push([p0[0] + (p1[0] - p0[0]) * t + nx * offset, p0[1] + (p1[1] - p0[1]) * t + ny * offset])
    }
  }
  const corner = (cx: number, cy: number, radius: number, from: number) => {
    const count = Math.max(8, Math.ceil(radius))
    for (let i = 0; i < count; i++) {
      const theta = from + ((Math.PI / 2) * i) / count
      points.push([
        cx + radius * superelliptic(Math.cos(theta), exponent),
        cy + radius * superelliptic(Math.sin(theta), exponent),
      ])
    }
  }

  const [a, b, c, d] = radii
  corner(a, a, a, Math.PI)
  side([a, 0], [w - b, 0], 0, -1, bows[0])
  corner(w - b, b, b, -Math.PI / 2)
  side([w, b], [w, h - c], 1, 0, bows[1])
  corner(w - c, h - c, c, 0)
  side([w - c, h], [d, h], 0, 1, bows[2])
  corner(d, h - d, d, Math.PI / 2)
  side([0, h - d], [0, a], -1, 0, bows[3])

  // Fit to exactly w × h.
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity
  for (const [x, y] of points) {
    x0 = Math.min(x0, x); x1 = Math.max(x1, x)
    y0 = Math.min(y0, y); y1 = Math.max(y1, y)
  }
  return points.map(([x, y]) => [((x - x0) / (x1 - x0)) * w, ((y - y0) / (y1 - y0)) * h])
}

/** One stone exactly `w × h`, from its own seed. */
export function stoneOutline(w: number, h: number, seed: string, opt: StoneOptions = {}): Point[] {
  return outlineFrom(w, h, stoneRandom(seed), opt)
}

/** Points evenly spaced every `step` along the closed polyline (24 at least). */
export function resample(points: readonly Point[], step: number): Point[] {
  const n = points.length
  const cumulative = [0]
  for (let i = 1; i <= n; i++) {
    const p = points[i - 1]
    const q = points[i % n]
    cumulative.push(cumulative[i - 1] + Math.hypot(q[0] - p[0], q[1] - p[1]))
  }
  const total = cumulative[n]
  const count = Math.max(24, Math.round(total / step))
  const out: Point[] = []
  let j = 0
  for (let i = 0; i < count; i++) {
    const s = (total * i) / count
    while (j < n - 1 && cumulative[j + 1] < s) j++
    const p = points[j]
    const q = points[(j + 1) % n]
    const segment = cumulative[j + 1] - cumulative[j]
    const u = segment === 0 ? 0 : (s - cumulative[j]) / segment
    out.push([p[0] + (q[0] - p[0]) * u, p[1] + (q[1] - p[1]) * u])
  }
  return out
}
