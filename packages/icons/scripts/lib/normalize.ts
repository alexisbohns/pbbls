import { tokenizePath } from "./lint"

/**
 * Path data every surface can parse with one small parser (spec D7): absolute
 * M L C Q Z only, numbers with at most two decimals. Arcs become cubic Béziers
 * (one per quarter turn at most), the standard endpoint-to-centre conversion.
 */

const fmt = (n: number): string => {
  const r = Math.round(n * 100) / 100
  return (Object.is(r, -0) ? 0 : r).toString()
}

type Pt = [number, number]

function arcToCubics(from: Pt, rx: number, ry: number, rotationDeg: number, largeArc: boolean, sweep: boolean, to: Pt): Pt[][] {
  if (rx === 0 || ry === 0) return []
  const phi = (rotationDeg * Math.PI) / 180
  const cos = Math.cos(phi)
  const sin = Math.sin(phi)
  const dx = (from[0] - to[0]) / 2
  const dy = (from[1] - to[1]) / 2
  const x1p = cos * dx + sin * dy
  const y1p = -sin * dx + cos * dy
  let a = Math.abs(rx)
  let b = Math.abs(ry)
  const lambda = (x1p * x1p) / (a * a) + (y1p * y1p) / (b * b)
  if (lambda > 1) {
    a *= Math.sqrt(lambda)
    b *= Math.sqrt(lambda)
  }
  const num = a * a * b * b - a * a * y1p * y1p - b * b * x1p * x1p
  const den = a * a * y1p * y1p + b * b * x1p * x1p
  const coef = (largeArc === sweep ? -1 : 1) * Math.sqrt(Math.max(0, num / den))
  const cxp = (coef * a * y1p) / b
  const cyp = (-coef * b * x1p) / a
  const cx = cos * cxp - sin * cyp + (from[0] + to[0]) / 2
  const cy = sin * cxp + cos * cyp + (from[1] + to[1]) / 2
  const angle = (ux: number, uy: number, vx: number, vy: number): number => {
    const sign = ux * vy - uy * vx < 0 ? -1 : 1
    const dot = (ux * vx + uy * vy) / (Math.hypot(ux, uy) * Math.hypot(vx, vy))
    return sign * Math.acos(Math.min(1, Math.max(-1, dot)))
  }
  const theta1 = angle(1, 0, (x1p - cxp) / a, (y1p - cyp) / b)
  let delta = angle((x1p - cxp) / a, (y1p - cyp) / b, (-x1p - cxp) / a, (-y1p - cyp) / b)
  if (!sweep && delta > 0) delta -= 2 * Math.PI
  if (sweep && delta < 0) delta += 2 * Math.PI
  const segments = Math.max(1, Math.ceil(Math.abs(delta) / (Math.PI / 2)))
  const step = delta / segments
  const k = (4 / 3) * Math.tan(step / 4)
  const point = (t: number): Pt => [cx + a * Math.cos(t) * cos - b * Math.sin(t) * sin, cy + a * Math.cos(t) * sin + b * Math.sin(t) * cos]
  const tangent = (t: number): Pt => [-a * Math.sin(t) * cos - b * Math.cos(t) * sin, -a * Math.sin(t) * sin + b * Math.cos(t) * cos]
  const out: Pt[][] = []
  for (let i = 0; i < segments; i += 1) {
    const t0 = theta1 + i * step
    const t1 = t0 + step
    const p0 = point(t0)
    const p3 = i === segments - 1 ? to : point(t1)
    const d0 = tangent(t0)
    const d1 = tangent(t1)
    out.push([
      [p0[0] + k * d0[0], p0[1] + k * d0[1]],
      [p3[0] - k * d1[0], p3[1] - k * d1[1]],
      p3,
    ])
  }
  return out
}

export function normalizePath(d: string): string {
  const parts: string[] = []
  let cursor: Pt = [0, 0]
  for (const { command, numbers } of tokenizePath(d)) {
    if (command === "Z") {
      parts.push("Z")
      continue
    }
    const arity = { M: 2, L: 2, C: 6, Q: 4, A: 7 }[command]
    if (arity === undefined) throw new Error(`normalizePath: ${command} is outside M L C Q A Z`)
    for (let i = 0; i + arity <= numbers.length; i += arity) {
      const g = numbers.slice(i, i + arity)
      const at = (j: number): number => g[j] ?? 0
      if (command === "A") {
        const to: Pt = [at(5), at(6)]
        const cubics = arcToCubics(cursor, at(0), at(1), at(2), at(3) !== 0, at(4) !== 0, to)
        if (cubics.length === 0) parts.push(`L${fmt(to[0])} ${fmt(to[1])}`)
        for (const [c1, c2, end] of cubics) {
          if (!c1 || !c2 || !end) continue
          parts.push(`C${fmt(c1[0])} ${fmt(c1[1])} ${fmt(c2[0])} ${fmt(c2[1])} ${fmt(end[0])} ${fmt(end[1])}`)
        }
        cursor = to
      } else {
        parts.push(command + g.map(fmt).join(" "))
        cursor = [at(arity - 2), at(arity - 1)]
      }
    }
  }
  return parts.join(" ")
}
