import { describe, expect, it } from "vitest"
import { BLUE_PEBBLE, FIRST_FIVE_ORDER, firstFiveState } from "./first-five"

const CATALOG = new Set([...FIRST_FIVE_ORDER, BLUE_PEBBLE, "pebbles-10"])

describe("firstFiveState", () => {
  it("is in progress with the count of what is done", () => {
    const state = firstFiveState(CATALOG, new Set(["first-moment", "first-pebble"]))
    expect(state.state).toBe("inProgress")
    expect(state.count).toBe(2)
    expect(state.done).toEqual({
      "first-moment": true,
      "first-pebble": true,
      "first-collection": false,
      "first-soul": false,
      "first-glyph": false,
    })
  })

  it("starts at 0 of 5", () => {
    expect(firstFiveState(CATALOG, new Set())).toMatchObject({ state: "inProgress", count: 0 })
  })

  it("is claimable with all five and no blue pebble", () => {
    expect(firstFiveState(CATALOG, new Set(FIRST_FIVE_ORDER)).state).toBe("claimable")
  })

  it("is done once the blue pebble is unlocked, even alone (a data repair)", () => {
    expect(firstFiveState(CATALOG, new Set([...FIRST_FIVE_ORDER, BLUE_PEBBLE])).state).toBe("done")
    expect(firstFiveState(CATALOG, new Set([BLUE_PEBBLE])).state).toBe("done")
  })

  it("is unavailable when the catalog misses a slug the user does not hold", () => {
    const partial = new Set(["first-moment", "first-pebble", "first-collection", "first-soul", BLUE_PEBBLE])
    expect(firstFiveState(partial, new Set()).state).toBe("unavailable")
    // A held unlock still counts for a retired row.
    expect(firstFiveState(partial, new Set(["first-glyph"])).state).toBe("inProgress")
  })
})
