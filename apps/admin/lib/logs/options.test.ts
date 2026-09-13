import { describe, expect, it } from "vitest"
import {
  PLATFORM_FILTER_OPTIONS,
  PLATFORM_OPTIONS,
  SPECIES_OPTIONS,
  STATUS_OPTIONS,
  isLogSpecies,
  isLogStatus,
  isPlatformFilter,
} from "./options"

// These guards sit between a URL search param (or a clipboard) and a column the
// database constrains with a check. They are the reason a hand-edited `?status=`
// is a no-op rather than a failed insert.

describe("log option guards", () => {
  it("accepts every value its own option list offers", () => {
    for (const { value } of SPECIES_OPTIONS) expect(isLogSpecies(value)).toBe(true)
    for (const { value } of STATUS_OPTIONS) expect(isLogStatus(value)).toBe(true)
    for (const { value } of PLATFORM_FILTER_OPTIONS) expect(isPlatformFilter(value)).toBe(true)
  })

  it("rejects undefined, the empty string and a near-miss", () => {
    for (const guard of [isLogSpecies, isLogStatus, isPlatformFilter]) {
      expect(guard(undefined)).toBe(false)
      expect(guard("")).toBe(false)
    }
    expect(isLogSpecies("Feature")).toBe(false)
    expect(isLogStatus("in-progress")).toBe(false)
  })

  it("does not confuse one enum for another", () => {
    expect(isLogSpecies("shipped")).toBe(false)
    expect(isLogStatus("feature")).toBe(false)
  })

  // The filter is a strict subset: `all`, `project` and `infra` are storable
  // platforms but not things you can filter the list by.
  it("keeps the platform filter a strict subset of the storable platforms", () => {
    const platforms = new Set(PLATFORM_OPTIONS.map((o) => o.value))
    for (const { value } of PLATFORM_FILTER_OPTIONS) expect(platforms.has(value)).toBe(true)
    expect(isPlatformFilter("all")).toBe(false)
    expect(isPlatformFilter("project")).toBe(false)
    expect(isPlatformFilter("infra")).toBe(false)
  })

  it("offers no duplicate or unlabelled options", () => {
    for (const list of [SPECIES_OPTIONS, STATUS_OPTIONS, PLATFORM_OPTIONS, PLATFORM_FILTER_OPTIONS]) {
      expect(new Set(list.map((o) => o.value)).size).toBe(list.length)
      for (const o of list) expect(o.label.trim()).not.toBe("")
    }
  })
})
