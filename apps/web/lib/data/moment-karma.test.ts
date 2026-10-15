import { describe, expect, it } from "vitest"
import { karmaDelta } from "./moment-karma"

describe("karmaDelta", () => {
  it("is the first completion's karma when there was no pebble", () => {
    expect(karmaDelta(0, 7)).toBe(7)
  })

  it("is the enrichment delta on an existing pebble", () => {
    expect(karmaDelta(7, 9)).toBe(2)
  })

  it("is 0 when an edit earned nothing", () => {
    expect(karmaDelta(9, 9)).toBe(0)
  })

  it("shows nothing when either read failed", () => {
    expect(karmaDelta(null, 9)).toBe(0)
    expect(karmaDelta(7, null)).toBe(0)
  })
})
