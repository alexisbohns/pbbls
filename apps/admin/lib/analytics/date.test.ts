import { describe, expect, it } from "vitest"
import { dateRangeFor, periodLengthDays, shiftIsoDate } from "./date"
import { TIME_RANGES } from "./types"

// Every analytics chart is framed by these three functions. An off-by-one here
// reads as a real dip or spike in the data, which is the kind of wrong number
// someone acts on.

describe("periodLengthDays", () => {
  it("maps each range to its day count, with `all` unbounded", () => {
    expect(periodLengthDays("7d")).toBe(7)
    expect(periodLengthDays("30d")).toBe(30)
    expect(periodLengthDays("90d")).toBe(90)
    expect(periodLengthDays("1y")).toBe(365)
    expect(periodLengthDays("all")).toBeNull()
  })

  it("answers for every range the picker offers", () => {
    for (const range of TIME_RANGES) {
      expect(periodLengthDays(range)).not.toBeUndefined()
    }
  })
})

describe("dateRangeFor", () => {
  const today = new Date("2026-03-15T12:00:00Z")

  // Inclusive of both ends: a 7-day window ending the 15th starts the 9th, so
  // the window spans 7 dates, not 8.
  it("returns an inclusive window ending today", () => {
    expect(dateRangeFor("7d", today)).toEqual({ start: "2026-03-09", end: "2026-03-15" })
    expect(dateRangeFor("30d", today)).toEqual({ start: "2026-02-14", end: "2026-03-15" })
  })

  it("starts at the epoch for `all`", () => {
    expect(dateRangeFor("all", today)).toEqual({ start: "1970-01-01", end: "2026-03-15" })
  })

  it("crosses a year boundary", () => {
    expect(dateRangeFor("90d", new Date("2026-01-10T00:00:00Z")).start).toBe("2025-10-13")
  })

  it("counts a leap day as a day", () => {
    expect(dateRangeFor("7d", new Date("2028-03-03T00:00:00Z")).start).toBe("2028-02-26")
  })

  // The window is computed in UTC on both ends, so a late-evening local time
  // cannot produce a start and end on different days' worth of data.
  it("works entirely in UTC, whatever time of day it is", () => {
    const lateUtc = dateRangeFor("7d", new Date("2026-03-15T23:59:59Z"))
    const earlyUtc = dateRangeFor("7d", new Date("2026-03-15T00:00:00Z"))
    expect(lateUtc).toEqual(earlyUtc)
  })

  it("does not mutate the date it is given", () => {
    const d = new Date("2026-03-15T12:00:00Z")
    dateRangeFor("90d", d)
    expect(d.toISOString()).toBe("2026-03-15T12:00:00.000Z")
  })
})

describe("shiftIsoDate", () => {
  it("adds and subtracts days", () => {
    expect(shiftIsoDate("2026-03-15", 1)).toBe("2026-03-16")
    expect(shiftIsoDate("2026-03-15", -1)).toBe("2026-03-14")
    expect(shiftIsoDate("2026-03-15", 0)).toBe("2026-03-15")
  })

  it("crosses month, year and leap-day boundaries", () => {
    expect(shiftIsoDate("2026-03-01", -1)).toBe("2026-02-28")
    expect(shiftIsoDate("2028-03-01", -1)).toBe("2028-02-29")
    expect(shiftIsoDate("2025-12-31", 1)).toBe("2026-01-01")
  })

  // Parsed as `T00:00:00Z`, never as local midnight — otherwise a machine west
  // of UTC would shift the date by one before shifting it by `days`.
  it("is timezone-independent", () => {
    expect(shiftIsoDate("2026-03-15", -7)).toBe("2026-03-08")
  })
})
