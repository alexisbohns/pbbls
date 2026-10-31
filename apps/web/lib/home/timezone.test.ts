import { describe, expect, it } from "vitest"
import { currentMonthStart } from "./timezone"

describe("currentMonthStart", () => {
  it("reads the month in the reader's zone", () => {
    const instant = new Date("2026-10-31T23:30:00Z")
    expect(currentMonthStart("UTC", instant)).toBe("2026-10-01")
    expect(currentMonthStart("Europe/Paris", instant)).toBe("2026-11-01")
  })
})
