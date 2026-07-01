// apps/admin/lib/pebblestore/path.ts

export type PathPoint = { x: number; y: number }

export type PathCommand =
  | { cmd: "M"; points: [PathPoint] }
  | { cmd: "L"; points: [PathPoint] }
  | { cmd: "Q"; points: [PathPoint, PathPoint] }
  | { cmd: "C"; points: [PathPoint, PathPoint, PathPoint] }
  | { cmd: "Z"; points: [] }

export class UnsupportedPathError extends Error {
  constructor(public readonly command: string) {
    super(`Unsupported path command: ${command}`)
    this.name = "UnsupportedPathError"
  }
}

/** 2×3 affine matrix [a,b,c,d,e,f]: x' = a·x + c·y + e ; y' = b·x + d·y + f */
export type Matrix = [number, number, number, number, number, number]

const NUMBER_RE = /-?\d*\.?\d+(?:e[-+]?\d+)?/gi
// Split on path command letters only. `e`/`E` is never a command — excluding it
// from the delimiter class keeps exponent notation (e.g. `1e3`) inside its number
// rather than mis-tokenizing it as a new command.
const COMMAND_RE = /[a-df-z][^a-df-z]*/gi

/** Parse a path `d` into normalized absolute commands. Throws UnsupportedPathError. */
export function parsePath(d: string): PathCommand[] {
  const tokens = d.match(COMMAND_RE) ?? []
  const out: PathCommand[] = []
  let cx = 0
  let cy = 0
  let sx = 0
  let sy = 0

  for (const token of tokens) {
    const letter = token[0]
    const upper = letter.toUpperCase()
    const rel = letter !== upper
    const nums = (token.slice(1).match(NUMBER_RE) ?? []).map(Number)

    switch (upper) {
      case "M": {
        for (let i = 0; i + 1 < nums.length; i += 2) {
          let x = nums[i]
          let y = nums[i + 1]
          if (rel) {
            x += cx
            y += cy
          }
          if (i === 0) {
            out.push({ cmd: "M", points: [{ x, y }] })
            sx = x
            sy = y
          } else {
            out.push({ cmd: "L", points: [{ x, y }] })
          }
          cx = x
          cy = y
        }
        break
      }
      case "L": {
        for (let i = 0; i + 1 < nums.length; i += 2) {
          let x = nums[i]
          let y = nums[i + 1]
          if (rel) {
            x += cx
            y += cy
          }
          out.push({ cmd: "L", points: [{ x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "H": {
        for (const n of nums) {
          const x = rel ? cx + n : n
          out.push({ cmd: "L", points: [{ x, y: cy }] })
          cx = x
        }
        break
      }
      case "V": {
        for (const n of nums) {
          const y = rel ? cy + n : n
          out.push({ cmd: "L", points: [{ x: cx, y }] })
          cy = y
        }
        break
      }
      case "Q": {
        for (let i = 0; i + 3 < nums.length; i += 4) {
          let x1 = nums[i]
          let y1 = nums[i + 1]
          let x = nums[i + 2]
          let y = nums[i + 3]
          if (rel) {
            x1 += cx
            y1 += cy
            x += cx
            y += cy
          }
          out.push({ cmd: "Q", points: [{ x: x1, y: y1 }, { x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "C": {
        for (let i = 0; i + 5 < nums.length; i += 6) {
          let x1 = nums[i]
          let y1 = nums[i + 1]
          let x2 = nums[i + 2]
          let y2 = nums[i + 3]
          let x = nums[i + 4]
          let y = nums[i + 5]
          if (rel) {
            x1 += cx
            y1 += cy
            x2 += cx
            y2 += cy
            x += cx
            y += cy
          }
          out.push({ cmd: "C", points: [{ x: x1, y: y1 }, { x: x2, y: y2 }, { x, y }] })
          cx = x
          cy = y
        }
        break
      }
      case "Z": {
        out.push({ cmd: "Z", points: [] })
        cx = sx
        cy = sy
        break
      }
      default:
        throw new UnsupportedPathError(upper)
    }
  }

  return out
}

const fmt = (n: number): string => Number(n.toFixed(2)).toString()

export function serializePath(cmds: PathCommand[]): string {
  return cmds
    .map((c) => {
      switch (c.cmd) {
        case "M":
          return `M ${fmt(c.points[0].x)} ${fmt(c.points[0].y)}`
        case "L":
          return `L ${fmt(c.points[0].x)} ${fmt(c.points[0].y)}`
        case "Q":
          return `Q ${fmt(c.points[0].x)} ${fmt(c.points[0].y)} ${fmt(c.points[1].x)} ${fmt(c.points[1].y)}`
        case "C":
          return `C ${fmt(c.points[0].x)} ${fmt(c.points[0].y)} ${fmt(c.points[1].x)} ${fmt(c.points[1].y)} ${fmt(c.points[2].x)} ${fmt(c.points[2].y)}`
        case "Z":
          return "Z"
      }
    })
    .join(" ")
}

export type Bounds = { minX: number; minY: number; maxX: number; maxY: number }

/** Superset bounds (includes control points) — safe for framing, not exact. */
export function pathBounds(cmds: PathCommand[]): Bounds | null {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  let has = false
  for (const c of cmds) {
    for (const p of c.points) {
      has = true
      minX = Math.min(minX, p.x)
      minY = Math.min(minY, p.y)
      maxX = Math.max(maxX, p.x)
      maxY = Math.max(maxY, p.y)
    }
  }
  return has ? { minX, minY, maxX, maxY } : null
}

function applyMatrix(m: Matrix, p: PathPoint): PathPoint {
  return { x: m[0] * p.x + m[2] * p.y + m[4], y: m[1] * p.x + m[3] * p.y + m[5] }
}

export function transformPath(cmds: PathCommand[], m: Matrix): PathCommand[] {
  return cmds.map((c) => ({ ...c, points: c.points.map((p) => applyMatrix(m, p)) }) as PathCommand)
}
