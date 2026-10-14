import { describe, expect, it } from "vitest"
import { pebbleGlyphFromRow, pebbleNameFromRow } from "./pebble-row"

describe("pebbleGlyphFromRow", () => {
  it("treats a malformed embed as no glyph", () => {
    expect(pebbleGlyphFromRow(undefined)).toBeNull()
    expect(pebbleGlyphFromRow({ id: "g", view_box: "0 0 200 200" })).toBeNull()
    expect(pebbleGlyphFromRow({ id: "g", view_box: "0 0 200 200", strokes: [{ d: 1 }] })).toBeNull()
  })

  it("accepts a glyph with no name", () => {
    expect(pebbleGlyphFromRow({ id: "g", name: null, view_box: "0 0 200 200", strokes: [] })).toEqual({
      id: "g",
      name: undefined,
      strokes: [],
      viewBox: "0 0 200 200",
    })
  })
})

describe("pebbleNameFromRow", () => {
  it("maps anything but a string to an empty name", () => {
    expect(pebbleNameFromRow(null)).toBe("")
    expect(pebbleNameFromRow(undefined)).toBe("")
    expect(pebbleNameFromRow("Swim")).toBe("Swim")
  })
})
