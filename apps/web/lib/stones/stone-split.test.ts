import { describe, expect, it } from "vitest"
import { splitStone } from "./stone-split"

// Goldens from the 2026-10-31 exploration kit, whose row equals iOS splitRow.
describe("splitStone", () => {
  it("cuts the quick actions rock into three pieces", () => {
    const pieces = splitStone(358, 100, 3)
    expect(pieces).toHaveLength(3)
    expect(pieces[0].startsWith("M1.5 28.0C1.8 26.7 2.1 25.4 2.5 24.1")).toBe(true)
    for (const piece of pieces) expect(piece.endsWith("Z")).toBe(true)
  })

  it("draws the first-five stepper", () => {
    const pieces = splitStone(324, 6, 5, "first-five", { corner: 3, gutter: 5, fillet: 2, lean: 0.9 })
    expect(pieces).toHaveLength(5)
    expect(pieces[0].startsWith("M25.4 1.0C26.8 1.0 28.1 1.0 29.5 1.0")).toBe(true)
  })

  it("returns n empty pieces when there is nothing to cut or no room (iOS's guard)", () => {
    expect(splitStone(358, 100, 1)).toEqual([""])
    expect(splitStone(16, 100, 3)).toEqual(["", "", ""])
  })

  it("is deterministic", () => {
    expect(splitStone(300, 88, 3, "days|pebbles|karma")).toEqual(splitStone(300, 88, 3, "days|pebbles|karma"))
  })
})
