import { tokenizePath } from "./lint"
import { normalizePath } from "./normalize"

export type Pt = [number, number]
export type Subpath = { points: Pt[]; closed: boolean }

/** Glyph units per sampled segment: 0.25 is about 1.5 px at the 48 px hero at 3×. */
const SEGMENT = 0.25
/** Ends closer than this close a subpath, as a hand closes a ring (half the stroke). */
const CLOSE_GAP = 0.875

function cubicPoints(p0: Pt, p1: Pt, p2: Pt, p3: Pt): Pt[] {
  const hull = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) + Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) + Math.hypot(p3[0] - p2[0], p3[1] - p2[1])
  const n = Math.max(8, Math.ceil(hull / SEGMENT))
  const out: Pt[] = []
  for (let k = 1; k <= n; k += 1) {
    const t = k / n
    const u = 1 - t
    out.push([
      u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
      u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
    ])
  }
  out[out.length - 1] = p3
  return out
}

/** Flattens path data (arcs included, through `normalizePath`) into polylines. */
export function flattenPath(d: string): Subpath[] {
  const subs: Subpath[] = []
  let current: Subpath | null = null
  let cursor: Pt = [0, 0]
  for (const { command, numbers } of tokenizePath(normalizePath(d))) {
    if (command === "Z") {
      if (current) current.closed = true
      continue
    }
    const arity = { M: 2, L: 2, C: 6, Q: 4 }[command] ?? 0
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const g = numbers.slice(i, i + arity)
      const at = (j: number): number => g[j] ?? 0
      if (command === "M") {
        current = { points: [[at(0), at(1)]], closed: false }
        subs.push(current)
        cursor = [at(0), at(1)]
      } else if (current && command === "L") {
        cursor = [at(0), at(1)]
        current.points.push(cursor)
      } else if (current && command === "C") {
        const end: Pt = [at(4), at(5)]
        current.points.push(...cubicPoints(cursor, [at(0), at(1)], [at(2), at(3)], end))
        cursor = end
      } else if (current && command === "Q") {
        const q: Pt = [at(0), at(1)]
        const end: Pt = [at(2), at(3)]
        const c1: Pt = [cursor[0] + (2 / 3) * (q[0] - cursor[0]), cursor[1] + (2 / 3) * (q[1] - cursor[1])]
        const c2: Pt = [end[0] + (2 / 3) * (q[0] - end[0]), end[1] + (2 / 3) * (q[1] - end[1])]
        current.points.push(...cubicPoints(cursor, c1, c2, end))
        cursor = end
      }
    }
  }
  return subs
}

/** A subpath is closed when it says Z or when its ends meet within half a stroke. */
export function isClosed(sub: Subpath): boolean {
  if (sub.closed) return true
  const first = sub.points[0]
  const last = sub.points[sub.points.length - 1]
  if (!first || !last || sub.points.length < 3) return false
  return Math.hypot(first[0] - last[0], first[1] - last[1]) <= CLOSE_GAP
}
