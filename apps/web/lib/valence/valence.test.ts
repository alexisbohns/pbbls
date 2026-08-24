import { describe, expect, it } from "vitest"
import {
  ALL_CELLS,
  cellAt,
  cellFrom,
  cellKey,
  cellsEqual,
  INTENSITY_BY_SIZE,
  POLARITY_ORDER,
  polarityAfter,
  polarityBefore,
  polarityIndex,
  SIZE_LADDER,
  sizeIndex,
  VALENCE_BY_POLARITY,
} from "./valence"

/**
 * What the roll does is index arithmetic, so this is where its behaviour is
 * pinned down — stepping, clamping and the ladder's order — without a gesture.
 * What tests cannot cover, and what needs a real finger, is the feel: the
 * detent spacing, the spring back, and whether the vertical axis really wins
 * against the step's scroll container.
 */
describe("the valence grid", () => {
  it("covers all nine cells exactly once", () => {
    expect(new Set(ALL_CELLS.map(cellKey)).size).toBe(9)
  })

  it("round-trips through the (intensity, valence) pair the draft stores", () => {
    for (const cell of ALL_CELLS) {
      const pair = [INTENSITY_BY_SIZE[cell.size], VALENCE_BY_POLARITY[cell.polarity]] as const
      expect(cellsEqual(cellFrom(pair[0], pair[1]), cell)).toBe(true)
    }
  })

  it("runs the size ladder big-to-small, not small-to-big", () => {
    // Deliberately not `allCases` order: the big end is at the top, so dragging
    // up rolls toward smaller events.
    expect(SIZE_LADDER).toEqual(["large", "medium", "small"])
  })
})

describe("stepping", () => {
  it("moves one cell per step along each axis", () => {
    const start = { polarity: 0, size: 1 }
    expect(cellAt(start.polarity + 1, start.size)).toEqual({ polarity: "neutral", size: "medium" })
    expect(cellAt(start.polarity, start.size + 1)).toEqual({ polarity: "lowlight", size: "small" })
  })

  it("clamps at the ends rather than wrapping", () => {
    // A hard swipe must not loop the user past the end and back to where they
    // started.
    expect(cellAt(-4, 1)).toEqual({ polarity: "lowlight", size: "medium" })
    expect(cellAt(9, 1)).toEqual({ polarity: "highlight", size: "medium" })
    expect(cellAt(1, -7)).toEqual({ polarity: "neutral", size: "large" })
    expect(cellAt(1, 7)).toEqual({ polarity: "neutral", size: "small" })
  })

  it("indexes every cell back to where it came from", () => {
    for (const cell of ALL_CELLS) {
      expect(cellAt(polarityIndex(cell), sizeIndex(cell))).toEqual(cell)
    }
  })
})

describe("neighbour words", () => {
  it("offers one polarity on each side, and nothing past the ends", () => {
    const middle = { size: "medium", polarity: "neutral" } as const
    expect(polarityBefore(middle)).toBe("lowlight")
    expect(polarityAfter(middle)).toBe("highlight")

    expect(polarityBefore({ size: "medium", polarity: POLARITY_ORDER[0] })).toBeNull()
    expect(polarityAfter({ size: "medium", polarity: POLARITY_ORDER[2] })).toBeNull()
  })
})
