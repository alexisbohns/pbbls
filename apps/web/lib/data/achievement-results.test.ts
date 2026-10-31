import { describe, expect, it } from "vitest"
import { unlockResultsFrom } from "./achievement-results"
import { DUMPED_CHECK_ACHIEVEMENTS } from "./__fixtures__/achievements-captured"

describe("unlockResultsFrom", () => {
  it("reads the harness's check_achievements rows", () => {
    expect(unlockResultsFrom(JSON.parse(DUMPED_CHECK_ACHIEVEMENTS))).toEqual([{ slug: "first-moment", karmaGranted: 0 }])
  })

  it("reads no rows as nothing to celebrate", () => {
    expect(unlockResultsFrom([])).toEqual([])
    expect(unlockResultsFrom(null)).toEqual([])
  })

  it("drops a null row or one without a slug alone, and reads a missing karma as 0", () => {
    expect(unlockResultsFrom([null, { karma_granted: 5 }, { slug: "pebble-count-10" }, { slug: "first-pebble", karma_granted: 10 }])).toEqual([
      { slug: "pebble-count-10", karmaGranted: 0 },
      { slug: "first-pebble", karmaGranted: 10 },
    ])
  })
})
