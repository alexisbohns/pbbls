import { describe, expect, it } from "vitest"
import { parseWeekParam } from "./week-param"

describe("parseWeekParam", () => {
  it("accepts a real ISO day", () => {
    expect(parseWeekParam("2026-10-05")).toEqual(new Date(Date.UTC(2026, 9, 5)))
  })

  it("rejects anything else", () => {
    for (const bad of ["2026-13-01", "2026-02-30", "x", "", " 2026-10-05", "2026-10-5", null]) {
      expect(parseWeekParam(bad)).toBeNull()
    }
  })
})
