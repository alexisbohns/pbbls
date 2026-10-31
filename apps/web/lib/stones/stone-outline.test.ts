import { describe, expect, it } from "vitest"
import { resample, stoneOutline } from "./stone-outline"
import { stonePath } from "./stone-path"

describe("stoneOutline", () => {
  it("fits its box exactly", () => {
    const points = stoneOutline(120, 80, "fit", { corner: 20 })
    const xs = points.map(([x]) => x)
    const ys = points.map(([, y]) => y)
    expect(Math.min(...xs)).toBeCloseTo(0, 9)
    expect(Math.max(...xs)).toBeCloseTo(120, 9)
    expect(Math.min(...ys)).toBeCloseTo(0, 9)
    expect(Math.max(...ys)).toBeCloseTo(80, 9)
  })

  it("draws the kit's chip (golden from the exploration kit)", () => {
    expect(stonePath(48, 46, "chip-0", { corner: 16 }).startsWith("M0.2 19.2C0.2 17.9")).toBe(true)
  })

  it("gives different seeds different stones", () => {
    expect(stonePath(48, 46, "chip-0", { corner: 16 })).not.toBe(stonePath(48, 46, "chip-1", { corner: 16 }))
  })
})

describe("resample", () => {
  it("returns at least 24 points", () => {
    expect(resample([[0, 0], [1, 0], [1, 1]], 4).length).toBeGreaterThanOrEqual(24)
  })
})
