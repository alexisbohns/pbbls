import { describe, expect, it } from "vitest"
import { stoneRandom } from "./stone-random"

describe("stoneRandom", () => {
  it("draws the kit's (and iOS's) first three values for the quick actions seed", () => {
    const next = stoneRandom("camera|fixed|rock|q11")
    expect(next().toFixed(10)).toBe("0.9302541588")
    expect(next().toFixed(10)).toBe("0.5075708053")
    expect(next().toFixed(10)).toBe("0.6537410403")
  })

  it("is deterministic per seed", () => {
    const a = stoneRandom("chip-3")
    const b = stoneRandom("chip-3")
    for (let i = 0; i < 1000; i++) expect(a()).toBe(b())
  })

  it("hashes the seed by UTF-16 code unit", () => {
    expect(stoneRandom("é")()).not.toBe(stoneRandom("e")())
    // One code unit above "e" (U+0065) is "f"; "é" is U+00E9: three distinct streams.
    expect(stoneRandom("é")()).not.toBe(stoneRandom("f")())
  })

  it("stays in [0, 1)", () => {
    const next = stoneRandom("bounds")
    for (let i = 0; i < 1000; i++) {
      const v = next()
      expect(v).toBeGreaterThanOrEqual(0)
      expect(v).toBeLessThan(1)
    }
  })
})
