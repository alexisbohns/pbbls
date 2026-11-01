import { describe, expect, it } from "vitest"
import { CAIRN_BOX, EMPTY_CAIRN, cairnStones, cairnWidth, cairnWidths } from "./cairn"

describe("cairn", () => {
  it("sizes stones as iOS CairnGeometry", () => {
    expect(cairnWidth([1, 1])).toBeCloseTo(13.2, 9)
    expect(cairnWidth([3, 3])).toBeCloseTo(26.4, 9)
    expect(cairnWidth([5, 2])).toBeCloseTo(24, 9)
    expect(cairnWidth([2, 0])).toBeCloseTo(17.4, 9)
  })

  it("keeps the days with a moment, widest first", () => {
    expect(cairnWidths([[0, 0], [1, 1], [3, 3], [0, 0], [5, 2]])).toEqual([cairnWidth([3, 3]), cairnWidth([5, 2]), cairnWidth([1, 1])])
  })

  it("stacks from the base, each stone sinking into the one below", () => {
    const [a, b] = cairnStones([20, 13.2])
    expect(a.y + a.h).toBeCloseTo(CAIRN_BOX.height, 9)
    expect(a.x).toBeCloseTo(5, 9)
    expect(b.y + b.h).toBeCloseTo(a.y + b.h * 0.2, 9)
  })

  it("flattens a stack taller than 42 without narrowing it", () => {
    const widths = Array.from({ length: 7 }, () => 26.4)
    const stones = cairnStones(widths)
    expect(stones[stones.length - 1].y).toBeCloseTo(0, 6)
    for (const s of stones) expect(s.w).toBe(26.4)
  })

  it("draws an empty week as one dotted stone", () => {
    expect(EMPTY_CAIRN).toHaveLength(1)
    expect(EMPTY_CAIRN[0].dotted).toBe(true)
    expect(EMPTY_CAIRN[0].w).toBeCloseTo(13.2, 9)
  })
})
