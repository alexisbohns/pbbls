import { describe, expect, it } from "vitest"
import { nextVerdict, parseRecord } from "./useReviewVerdicts"

describe("nextVerdict", () => {
  it("cycles unset → ok → fix → drop-twin → unset", () => {
    expect(nextVerdict(undefined)).toBe("ok")
    expect(nextVerdict("ok")).toBe("fix")
    expect(nextVerdict("fix")).toBe("drop-twin")
    expect(nextVerdict("drop-twin")).toBeUndefined()
  })
})

describe("parseRecord", () => {
  const isWord = (v: unknown): v is string => typeof v === "string" && v !== ""
  it("keeps the entries the guard accepts and drops the rest", () => {
    expect(parseRecord('{"moon-cloud":"tag the moon as focus","home":"","x":3}', isWord)).toEqual({ "moon-cloud": "tag the moon as focus" })
  })
  it("reads nothing from empty, broken or non-object storage", () => {
    expect(parseRecord(null, isWord)).toEqual({})
    expect(parseRecord("{not json", isWord)).toEqual({})
    expect(parseRecord('"a string"', isWord)).toEqual({})
  })
})
