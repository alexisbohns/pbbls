// Hard gate for the wobble port: the noise + displacement must reproduce the
// cross-platform golden fixture the iOS spike pinned (issue #555, PR #556),
// `apps/ios/PebblesTests/Wobble/WobbleGolden.json` — LCG exact, everything
// else within 1e-9. The fixture is the shared parity anchor for the Kotlin/TS
// ports; regenerate it ONLY via `apps/ios/Scripts/generate-wobble-golden.mjs`.

import { readFileSync } from "node:fs"
import { dirname, resolve } from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

import { SVGTurbulence, rawSequence } from "./turbulence"
import { displace, type WobbleParams } from "./params"

type Golden = {
  seed: number
  lcg: number[]
  latticePrefix: number[]
  gradientSamples: { channel: number; index: number; x: number; y: number }[]
  turbulence: { channel: number; x: number; y: number; frequency: number; octaves: number; value: number }[]
  displaced: {
    x: number
    y: number
    amplitude: number
    frequency: number
    octaves: number
    xOut: number
    yOut: number
  }[]
}

const goldenPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../../../../apps/ios/PebblesTests/Wobble/WobbleGolden.json",
)
const golden = JSON.parse(readFileSync(goldenPath, "utf8")) as Golden

const TOLERANCE = 1e-9

describe("SVGTurbulence golden parity", () => {
  it("reproduces the raw LCG sequence exactly", () => {
    expect(rawSequence(golden.seed, golden.lcg.length)).toEqual(golden.lcg)
  })

  it("reproduces the lattice prefix exactly", () => {
    const noise = new SVGTurbulence(golden.seed)
    expect(noise.latticePrefix(golden.latticePrefix.length)).toEqual(golden.latticePrefix)
  })

  it("reproduces the gradient samples", () => {
    const noise = new SVGTurbulence(golden.seed)
    for (const sample of golden.gradientSamples) {
      const g = noise.gradientSample(sample.channel, sample.index)
      expect(g.x).toBeCloseTo(sample.x, 9)
      expect(g.y).toBeCloseTo(sample.y, 9)
      expect(Math.abs(g.x - sample.x)).toBeLessThanOrEqual(TOLERANCE)
      expect(Math.abs(g.y - sample.y)).toBeLessThanOrEqual(TOLERANCE)
    }
  })

  it("reproduces turbulence values within 1e-9", () => {
    const noise = new SVGTurbulence(golden.seed)
    for (const c of golden.turbulence) {
      const value = noise.turbulence(c.channel, c.x, c.y, c.frequency, c.octaves)
      expect(Math.abs(value - c.value)).toBeLessThanOrEqual(TOLERANCE)
    }
  })

  it("reproduces displaced points within 1e-9", () => {
    const noise = new SVGTurbulence(golden.seed)
    for (const c of golden.displaced) {
      const params: WobbleParams = {
        amplitude: c.amplitude,
        frequency: c.frequency,
        octaves: c.octaves,
        flattenStep: 0, // unused by displace()
      }
      const out = displace({ x: c.x, y: c.y }, noise, params)
      expect(Math.abs(out.x - c.xOut)).toBeLessThanOrEqual(TOLERANCE)
      expect(Math.abs(out.y - c.yOut)).toBeLessThanOrEqual(TOLERANCE)
    }
  })
})
