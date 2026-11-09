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

  it("fills an open C-shaped body through its implicit closure", () => {
    const { area } = carveSolid(["M8 4 C2 4 2 20 8 20 L16 20 C22 20 22 4 16 4"], [])
    expect(area).toBeGreaterThan(150)
  })

  it("fills a ring that overshoots its start", () => {
    const { area } = carveSolid(["M12 4 C18 4 20 8 20 12 C20 16 16 20 12 20 C8 20 4 16 4 12 C4 8 8 4 14 4.2"], [])
    expect(area).toBeGreaterThan(200)
  })

  it("does not let two body paths wound in opposite directions cancel", () => {
    const cw = "M2 2 L12 2 L12 12 L2 12 Z"
    const ccwOverlap = "M7 7 L7 17 L17 17 L17 7 Z"
    const cwOverlap = "M7 7 L17 7 L17 17 L7 17 Z"
    const same = carveSolid([cw, cwOverlap], []).area
    const opposite = carveSolid([cw, ccwOverlap], []).area
    expect(opposite).toBeCloseTo(same, 1)
  })

  it("keeps a line that sits in the hole of a ring-shaped body", () => {
    const ring = "M2 2 L22 2 L22 22 L2 22 Z M7 7 L7 17 L17 17 L17 7 Z"
    const inHole = "M10 12 L14 12"
    const inBand = "M3 12 L5 12"
    const { keep } = carveSolid([ring], [inHole, inBand])
    expect(keep).toEqual([0])
  })

  it("cuts a line standing on the body's edge", () => {
    // A door whose feet stand on the bottom edge (y = 20).
    const { keep } = carveSolid([SQUARE], ["M10 20 L10 15 L14 15 L14 20"])
    expect(keep).toEqual([])
  })

  it("keeps a line traced along the body's outline", () => {
    // A hand-drawn rim wavering over the top edge, as a bowl's lip: partly outside the fill, never deep inside.
    const { keep } = carveSolid([SQUARE], ["M4 3.6 L12 4.4 L20 3.6"])
    expect(keep).toEqual([0])
  })

  it("keeps a line further than half a stroke from the body", () => {
    // x = 2 is 2 units left of the edge at x = 4.
    const { keep } = carveSolid([SQUARE], ["M2 4 L2 20"])
    expect(keep).toEqual([0])
  })

  it("cuts a hole as a filled shape and reports a hole outside the body", () => {
    const dot = "M11 11 L13 11 L13 13 L11 13 Z"
    const whole = carveSolid([SQUARE], []).area
    const { area, outside, keep } = carveSolid([SQUARE], [], [dot, "M1.6 1.6 L2 2 L1.6 2 Z"])
    // A 2×2 square grown by half a stroke, round corners: 2² + 4·2·0.875 + π·0.875².
    const cut = 4 + 8 * 0.875 + Math.PI * 0.875 * 0.875
    expect(whole - area).toBeCloseTo(cut, 0)
    expect(outside).toEqual([1])
    expect(keep).toEqual([])
  })
})
