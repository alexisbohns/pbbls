import { describe, expect, it } from "vitest"
import { filledArea } from "./lint"
import { carveSolid } from "./carve"

const SQUARE = "M4 4 L20 4 L20 20 L4 20 Z"

describe("carveSolid", () => {
  it("fills the body grown by half the stroke", () => {
    const { d, keep, area } = carveSolid([SQUARE], [])
    expect(keep).toEqual([])
    // 16×16 grown by 0.875 on every side with round corners: a little under 17.75².
    expect(area).toBeGreaterThan(310)
    expect(area).toBeLessThan(17.75 * 17.75)
    expect(filledArea(d, true)).toBeGreaterThan(300)
  })

  it("cuts an inner line out of the body and keeps an outside line", () => {
    const inner = "M8 12 L16 12"
    const outside = "M2 22 L6 22"
    const whole = carveSolid([SQUARE], []).area
    const { area, keep } = carveSolid([SQUARE], [inner, outside])
    // An 8-unit line outlined at 1.75 with round caps: 8 × 1.75 + π × 0.875².
    const cut = 8 * 1.75 + Math.PI * 0.875 * 0.875
    expect(whole - area).toBeGreaterThan(cut - 0.5)
    expect(whole - area).toBeLessThan(cut + 0.5)
    expect(keep).toEqual([1])
  })

  it("rounds its path data to two decimals and closes every ring", () => {
    const { d } = carveSolid([SQUARE], ["M8 12 L16 12"])
    expect(d).not.toMatch(/\d\.\d{3}/)
    expect(d.trim().endsWith("Z")).toBe(true)
  })
})
