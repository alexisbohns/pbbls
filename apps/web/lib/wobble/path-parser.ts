// Own SVG path-`d` parser, mirroring the iOS `SVGPathParser` (which builds a
// CGPath) but emitting normalized path elements the flattener consumes. Pure,
// no DOM: SSR-safe. Supports `M m L l H h V v C c S s Q q T t A a Z z` with
// implicit-command continuation and S/T smoothing; arcs are decomposed into
// cubic Béziers (the shape CGPath stores internally, so the flattener sees the
// same element stream on both platforms).

import type { Point } from "./types"

export type PathElement =
  | { type: "move"; to: Point }
  | { type: "line"; to: Point }
  | { type: "quad"; control: Point; to: Point }
  | { type: "cubic"; control1: Point; control2: Point; to: Point }
  | { type: "close" }

type Token = { kind: "command"; char: string } | { kind: "number"; value: number }

const COMMAND_CHARS = new Set("MmLlHhVvCcSsQqTtAaZz".split(""))

function isDigit(c: string): boolean {
  return c >= "0" && c <= "9"
}

function tokenize(d: string): Token[] {
  const tokens: Token[] = []
  const n = d.length
  let i = 0
  while (i < n) {
    const c = d[i]
    if (COMMAND_CHARS.has(c)) {
      tokens.push({ kind: "command", char: c })
      i += 1
    } else if (c === " " || c === "\t" || c === "\n" || c === "\r" || c === ",") {
      i += 1
    } else if (c === "+" || c === "-" || c === "." || isDigit(c)) {
      // Number — read until the next non-numeric character.
      const start = i
      if (c === "+" || c === "-") i += 1
      let seenDot = false
      let seenExp = false
      while (i < n) {
        const ch = d[i]
        if (isDigit(ch)) {
          i += 1
        } else if (ch === "." && !seenDot && !seenExp) {
          seenDot = true
          i += 1
        } else if ((ch === "e" || ch === "E") && !seenExp) {
          seenExp = true
          i += 1
          if (i < n && (d[i] === "+" || d[i] === "-")) i += 1
        } else {
          break
        }
      }
      const value = Number(d.slice(start, i))
      if (Number.isNaN(value)) return []
      tokens.push({ kind: "number", value })
    } else {
      // Unknown character — abort.
      return []
    }
  }
  return tokens
}

/** S/s reflects only after C/c/S/s; T/t only after Q/q/T/t. */
function reflectedControl(
  current: Point,
  lastControl: Point | null,
  lastCommand: string,
  cubicLike: boolean,
): Point {
  const qualifies = cubicLike ? "CcSs".includes(lastCommand) : "QqTt".includes(lastCommand)
  if (!qualifies || !lastControl) return { ...current }
  return { x: 2 * current.x - lastControl.x, y: 2 * current.y - lastControl.y }
}

/**
 * Parses an SVG path `d` string into normalized elements, or null when it
 * contains no recognizable command or cannot be tokenized.
 */
export function parsePath(d: string): PathElement[] | null {
  const tokens = tokenize(d)
  if (tokens.length === 0) return null

  const out: PathElement[] = []
  let current: Point = { x: 0, y: 0 }
  let subpathStart: Point = { x: 0, y: 0 }
  let lastControl: Point | null = null
  let lastCommand = "M"

  let i = 0
  while (i < tokens.length) {
    const head = tokens[i]
    if (head.kind !== "command") return null
    const cmdChar = head.char
    i += 1

    const args: number[] = []
    while (i < tokens.length) {
      const tok = tokens[i]
      if (tok.kind !== "number") break
      args.push(tok.value)
      i += 1
    }

    let argIndex = 0
    let firstIteration = true

    do {
      let activeCmd: string
      if (firstIteration) {
        activeCmd = cmdChar
        firstIteration = false
      } else {
        // Implicit M continues as L; m continues as l. Others repeat.
        activeCmd = cmdChar === "M" ? "L" : cmdChar === "m" ? "l" : cmdChar
      }

      switch (activeCmd) {
        case "M": {
          if (argIndex + 1 >= args.length) return null
          current = { x: args[argIndex], y: args[argIndex + 1] }
          subpathStart = current
          out.push({ type: "move", to: current })
          argIndex += 2
          break
        }
        case "m": {
          if (argIndex + 1 >= args.length) return null
          current = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          subpathStart = current
          out.push({ type: "move", to: current })
          argIndex += 2
          break
        }
        case "L": {
          if (argIndex + 1 >= args.length) return null
          current = { x: args[argIndex], y: args[argIndex + 1] }
          out.push({ type: "line", to: current })
          argIndex += 2
          break
        }
        case "l": {
          if (argIndex + 1 >= args.length) return null
          current = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          out.push({ type: "line", to: current })
          argIndex += 2
          break
        }
        case "H": {
          if (argIndex >= args.length) return null
          current = { x: args[argIndex], y: current.y }
          out.push({ type: "line", to: current })
          argIndex += 1
          break
        }
        case "h": {
          if (argIndex >= args.length) return null
          current = { x: current.x + args[argIndex], y: current.y }
          out.push({ type: "line", to: current })
          argIndex += 1
          break
        }
        case "V": {
          if (argIndex >= args.length) return null
          current = { x: current.x, y: args[argIndex] }
          out.push({ type: "line", to: current })
          argIndex += 1
          break
        }
        case "v": {
          if (argIndex >= args.length) return null
          current = { x: current.x, y: current.y + args[argIndex] }
          out.push({ type: "line", to: current })
          argIndex += 1
          break
        }
        case "C": {
          if (argIndex + 5 >= args.length) return null
          const c1 = { x: args[argIndex], y: args[argIndex + 1] }
          const c2 = { x: args[argIndex + 2], y: args[argIndex + 3] }
          current = { x: args[argIndex + 4], y: args[argIndex + 5] }
          out.push({ type: "cubic", control1: c1, control2: c2, to: current })
          lastControl = c2
          argIndex += 6
          break
        }
        case "c": {
          if (argIndex + 5 >= args.length) return null
          const c1 = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          const c2 = { x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3] }
          current = { x: current.x + args[argIndex + 4], y: current.y + args[argIndex + 5] }
          out.push({ type: "cubic", control1: c1, control2: c2, to: current })
          lastControl = c2
          argIndex += 6
          break
        }
        case "S": {
          if (argIndex + 3 >= args.length) return null
          const c1 = reflectedControl(current, lastControl, lastCommand, true)
          const c2 = { x: args[argIndex], y: args[argIndex + 1] }
          current = { x: args[argIndex + 2], y: args[argIndex + 3] }
          out.push({ type: "cubic", control1: c1, control2: c2, to: current })
          lastControl = c2
          argIndex += 4
          break
        }
        case "s": {
          if (argIndex + 3 >= args.length) return null
          const c1 = reflectedControl(current, lastControl, lastCommand, true)
          const c2 = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          current = { x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3] }
          out.push({ type: "cubic", control1: c1, control2: c2, to: current })
          lastControl = c2
          argIndex += 4
          break
        }
        case "Q": {
          if (argIndex + 3 >= args.length) return null
          const c = { x: args[argIndex], y: args[argIndex + 1] }
          current = { x: args[argIndex + 2], y: args[argIndex + 3] }
          out.push({ type: "quad", control: c, to: current })
          lastControl = c
          argIndex += 4
          break
        }
        case "q": {
          if (argIndex + 3 >= args.length) return null
          const c = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          current = { x: current.x + args[argIndex + 2], y: current.y + args[argIndex + 3] }
          out.push({ type: "quad", control: c, to: current })
          lastControl = c
          argIndex += 4
          break
        }
        case "T": {
          if (argIndex + 1 >= args.length) return null
          const c = reflectedControl(current, lastControl, lastCommand, false)
          current = { x: args[argIndex], y: args[argIndex + 1] }
          out.push({ type: "quad", control: c, to: current })
          lastControl = c
          argIndex += 2
          break
        }
        case "t": {
          if (argIndex + 1 >= args.length) return null
          const c = reflectedControl(current, lastControl, lastCommand, false)
          current = { x: current.x + args[argIndex], y: current.y + args[argIndex + 1] }
          out.push({ type: "quad", control: c, to: current })
          lastControl = c
          argIndex += 2
          break
        }
        case "A": {
          if (argIndex + 6 >= args.length) return null
          const end = { x: args[argIndex + 5], y: args[argIndex + 6] }
          appendArc(out, current, end, {
            rx: args[argIndex],
            ry: args[argIndex + 1],
            xAxisRotationDeg: args[argIndex + 2],
            largeArc: args[argIndex + 3] !== 0,
            sweep: args[argIndex + 4] !== 0,
          })
          current = end
          lastControl = null
          argIndex += 7
          break
        }
        case "a": {
          if (argIndex + 6 >= args.length) return null
          const end = { x: current.x + args[argIndex + 5], y: current.y + args[argIndex + 6] }
          appendArc(out, current, end, {
            rx: args[argIndex],
            ry: args[argIndex + 1],
            xAxisRotationDeg: args[argIndex + 2],
            largeArc: args[argIndex + 3] !== 0,
            sweep: args[argIndex + 4] !== 0,
          })
          current = end
          lastControl = null
          argIndex += 7
          break
        }
        case "Z":
        case "z": {
          out.push({ type: "close" })
          current = subpathStart
          lastControl = null
          break
        }
        default:
          return null
      }

      lastCommand = activeCmd
      if (activeCmd === "Z" || activeCmd === "z") break
    } while (argIndex < args.length)
  }

  return out
}

// ── Arc → cubic Bézier decomposition (SVG 1.1 F.6) ──────────────────

type ArcSpec = {
  rx: number
  ry: number
  xAxisRotationDeg: number
  largeArc: boolean
  sweep: boolean
}

function appendArc(out: PathElement[], from: Point, to: Point, spec: ArcSpec): void {
  if (Math.abs(from.x - to.x) < 1e-6 && Math.abs(from.y - to.y) < 1e-6) return

  // Zero radius → straight line, per spec.
  if (spec.rx === 0 || spec.ry === 0) {
    out.push({ type: "line", to })
    return
  }

  const phi = (spec.xAxisRotationDeg * Math.PI) / 180
  let rx = Math.abs(spec.rx)
  let ry = Math.abs(spec.ry)

  // F.6.5 step 1
  const dx2 = (from.x - to.x) / 2
  const dy2 = (from.y - to.y) / 2
  const cosPhi = Math.cos(phi)
  const sinPhi = Math.sin(phi)
  const x1p = cosPhi * dx2 + sinPhi * dy2
  const y1p = -sinPhi * dx2 + cosPhi * dy2

  // F.6.6 — ensure radii large enough.
  const lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
  if (lambda > 1) {
    const s = Math.sqrt(lambda)
    rx *= s
    ry *= s
  }

  // F.6.5 step 2 — center in primed coords.
  const signFactor = spec.largeArc === spec.sweep ? -1 : 1
  const num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
  const denom = rx * rx * y1p * y1p + ry * ry * x1p * x1p
  const coeff = signFactor * Math.sqrt(Math.max(0, num / denom))
  const cxp = coeff * ((rx * y1p) / ry)
  const cyp = -coeff * ((ry * x1p) / rx)

  // Center back in user coords.
  const cx = cosPhi * cxp - sinPhi * cyp + (from.x + to.x) / 2
  const cy = sinPhi * cxp + cosPhi * cyp + (from.y + to.y) / 2

  const angle = (ux: number, uy: number, vx: number, vy: number): number => {
    const dot = ux * vx + uy * vy
    const len = Math.sqrt(ux * ux + uy * uy) * Math.sqrt(vx * vx + vy * vy)
    let v = dot / len
    v = Math.max(-1, Math.min(1, v))
    const sign = ux * vy - uy * vx < 0 ? -1 : 1
    return sign * Math.acos(v)
  }

  const theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
  let deltaTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
  if (!spec.sweep && deltaTheta > 0) deltaTheta -= 2 * Math.PI
  if (spec.sweep && deltaTheta < 0) deltaTheta += 2 * Math.PI

  // Split into ≤ 90° arcs; each becomes one cubic (the standard κ tangent
  // approximation), transformed by the ellipse rotation/scale about the center.
  const segments = Math.max(1, Math.ceil(Math.abs(deltaTheta) / (Math.PI / 2)))
  const delta = deltaTheta / segments
  const t = (4 / 3) * Math.tan(delta / 4)

  const onEllipse = (ang: number): Point => {
    const cosA = Math.cos(ang)
    const sinA = Math.sin(ang)
    return {
      x: cx + cosPhi * rx * cosA - sinPhi * ry * sinA,
      y: cy + sinPhi * rx * cosA + cosPhi * ry * sinA,
    }
  }
  const derivative = (ang: number): Point => {
    const cosA = Math.cos(ang)
    const sinA = Math.sin(ang)
    return {
      x: -cosPhi * rx * sinA - sinPhi * ry * cosA,
      y: -sinPhi * rx * sinA + cosPhi * ry * cosA,
    }
  }

  let angleStart = theta1
  let p0 = onEllipse(angleStart)
  for (let seg = 0; seg < segments; seg++) {
    const angleEnd = angleStart + delta
    const p3 = onEllipse(angleEnd)
    const d0 = derivative(angleStart)
    const d3 = derivative(angleEnd)
    const c1 = { x: p0.x + t * d0.x, y: p0.y + t * d0.y }
    const c2 = { x: p3.x - t * d3.x, y: p3.y - t * d3.y }
    out.push({ type: "cubic", control1: c1, control2: c2, to: p3 })
    angleStart = angleEnd
    p0 = p3
  }
}
