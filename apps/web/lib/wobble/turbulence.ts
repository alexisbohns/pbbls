// Faithful TypeScript port of the SVG 1.1 §15.19 `feTurbulence type="fractalNoise"`
// reference implementation. Transliterated from the design playground's JS
// (`apps/ios/Scripts/generate-wobble-golden.mjs`) — the exact source the iOS
// `SVGTurbulence` port and the `WobbleGolden.json` fixture both derive from.
// The wobble look was tuned against this exact noise; substituting another
// noise (simplex, hash-based) changes the approved look.
//
// Two structure quirks are deliberate and load-bearing — do NOT "clean up":
//  - Gradients are generated for all four RGBA channels in the spec's loop
//    order even though the wobble only reads channels 0 (R) and 1 (G): the
//    seeded LCG stream position depends on it.
//  - Integer math follows the spec's Schrage decomposition exactly, so the raw
//    LCG sequence golden-tests against `PebblesTests/Wobble/WobbleGolden.json`.

import type { Point } from "./types"

const B_SIZE = 0x100
const BM = 0xff
const PERLIN_N = 0x1000

// Spec LCG constants (§15.19, "RandomNumberSetup").
const RAND_M = 2147483647 // 2**31 − 1
const RAND_A = 16807 // 7**5, primitive root of m
const RAND_Q = 127773 // m / a
const RAND_R = 2836 // m % a

/** One step of the spec's seeded LCG (Schrage decomposition). */
function nextRandom(seed: number): number {
  let result = RAND_A * (seed % RAND_Q) - RAND_R * Math.floor(seed / RAND_Q)
  if (result <= 0) result += RAND_M
  return result
}

function normalizedSeed(seed: number): number {
  let s = Math.floor(seed)
  if (s <= 0) s = -(s % (RAND_M - 1)) + 1
  if (s > RAND_M - 1) s = RAND_M - 1
  return s
}

/** First `count` raw LCG values for `seed` — consumed by the golden test. */
export function rawSequence(seed: number, count: number): number[] {
  const values: number[] = []
  let state = normalizedSeed(seed)
  for (let i = 0; i < count; i++) {
    state = nextRandom(state)
    values.push(state)
  }
  return values
}

const sCurve = (t: number): number => t * t * (3 - 2 * t)
const lerp = (t: number, a: number, b: number): number => a + t * (b - a)

export class SVGTurbulence {
  /** Lattice selector, length 2·B_SIZE + 2. */
  private readonly lattice: number[]
  /** Normalized 2-D gradients per channel: [4][2·B_SIZE + 2]. */
  private readonly gradient: Point[][]

  constructor(seed: number) {
    const lattice = new Array<number>(B_SIZE + B_SIZE + 2).fill(0)
    const gradient: Point[][] = [0, 1, 2, 3].map(() => {
      const row = new Array<Point>(B_SIZE + B_SIZE + 2)
      for (let i = 0; i < row.length; i++) row[i] = { x: 0, y: 0 }
      return row
    })
    let s = normalizedSeed(seed)

    for (let channel = 0; channel < 4; channel++) {
      for (let i = 0; i < B_SIZE; i++) {
        lattice[i] = i
        const v: [number, number] = [0, 0]
        for (let j = 0; j < 2; j++) {
          s = nextRandom(s)
          v[j] = ((s % (B_SIZE + B_SIZE)) - B_SIZE) / B_SIZE
        }
        // No zero-length guard, mirroring the reference: 0/0 → NaN on both
        // sides of the golden test, identically.
        const len = Math.hypot(v[0], v[1])
        gradient[channel][i] = { x: v[0] / len, y: v[1] / len }
      }
    }

    // Fisher–Yates-style lattice shuffle, consuming the same LCG stream.
    let i = B_SIZE - 1
    while (i > 0) {
      const swapped = lattice[i]
      s = nextRandom(s)
      const j = s % B_SIZE
      lattice[i] = lattice[j]
      lattice[j] = swapped
      i -= 1
    }

    // Wrap-around duplication so `lattice[i + by]` never overruns.
    for (let k = 0; k < B_SIZE + 2; k++) {
      lattice[B_SIZE + k] = lattice[k]
      for (let channel = 0; channel < 4; channel++) {
        gradient[channel][B_SIZE + k] = gradient[channel][k]
      }
    }

    this.lattice = lattice
    this.gradient = gradient
  }

  /** 2-D gradient noise for one channel at noise-space coordinates, in [−1, 1]. */
  noise2(channel: number, x: number, y: number): number {
    let t = x + PERLIN_N
    const bx0 = (t | 0) & BM // (t | 0) truncation, as in the reference
    const bx1 = (bx0 + 1) & BM
    const rx0 = t - (t | 0)
    const rx1 = rx0 - 1
    t = y + PERLIN_N
    const by0 = (t | 0) & BM
    const by1 = (by0 + 1) & BM
    const ry0 = t - (t | 0)
    const ry1 = ry0 - 1

    const latticeX0 = this.lattice[bx0]
    const latticeX1 = this.lattice[bx1]
    const b00 = this.lattice[latticeX0 + by0]
    const b10 = this.lattice[latticeX1 + by0]
    const b01 = this.lattice[latticeX0 + by1]
    const b11 = this.lattice[latticeX1 + by1]

    const sx = sCurve(rx0)
    const sy = sCurve(ry0)
    const grad = this.gradient[channel]

    let q = grad[b00]
    const u0 = rx0 * q.x + ry0 * q.y
    q = grad[b10]
    const v0 = rx1 * q.x + ry0 * q.y
    const a = lerp(sx, u0, v0)
    q = grad[b01]
    const u1 = rx0 * q.x + ry1 * q.y
    q = grad[b11]
    const v1 = rx1 * q.x + ry1 * q.y
    const b = lerp(sx, u1, v1)
    return lerp(sy, a, b)
  }

  /** Stitchless fractalNoise sum over `octaves` — signed, unclamped. */
  turbulence(channel: number, x: number, y: number, baseFrequency: number, octaves: number): number {
    let vx = x * baseFrequency
    let vy = y * baseFrequency
    let sum = 0
    let ratio = 1
    for (let o = 0; o < octaves; o++) {
      sum += this.noise2(channel, vx, vy) / ratio
      vx *= 2
      vy *= 2
      ratio *= 2
    }
    return sum
  }

  // ── Test hooks (consumed by the golden test) ──────────────────────

  latticePrefix(count: number): number[] {
    return this.lattice.slice(0, count)
  }

  gradientSample(channel: number, index: number): Point {
    return this.gradient[channel][index]
  }
}
