import { describe, expect, it } from "vitest"
import { PBBLS_ICON_SIZE } from "./pbbls-icon"

// A Tailwind `size-<n>` class is n × 4 px at the default 16 px root. The
// expected values are the iOS `PbblsIconSize` raw values
// (apps/ios/Pebbles/Theme/Icon+Pebbles.swift), so the two scales cannot drift.
const px = (cls: string): number => Number(/^size-(\d+(?:\.\d+)?)$/.exec(cls)?.[1]) * 4

describe("PBBLS_ICON_SIZE", () => {
  it("names the five sizes, smallest first", () => {
    expect(Object.keys(PBBLS_ICON_SIZE)).toEqual(["inline", "row", "toolbar", "tile", "hero"])
  })

  it("draws each at the iOS value, from the 16 px floor", () => {
    const sizes = Object.fromEntries(Object.entries(PBBLS_ICON_SIZE).map(([name, cls]) => [name, px(cls)]))
    expect(sizes).toEqual({ inline: 16, row: 20, toolbar: 22, tile: 28, hero: 48 })
    expect(Math.min(...Object.values(sizes))).toBe(16)
  })
})
