import { describe, expect, it } from "vitest"
import { ILLUSTRATION_OPTIONS, isGuideIllustration } from "./options"

describe("guide option guards", () => {
  it("accepts every listed illustration", () => {
    for (const { value } of ILLUSTRATION_OPTIONS) expect(isGuideIllustration(value)).toBe(true)
  })
  it("rejects anything else, including the empty string and undefined", () => {
    expect(isGuideIllustration("photo")).toBe(false)
    expect(isGuideIllustration("")).toBe(false)
    expect(isGuideIllustration(undefined)).toBe(false)
  })
  it("lists the nine illustrations in the table's order", () => {
    expect(ILLUSTRATION_OPTIONS.map((o) => o.value)).toEqual([
      "pebbles", "stones", "emotions", "valence", "domains", "collections", "souls", "glyphs", "karma",
    ])
  })
})
