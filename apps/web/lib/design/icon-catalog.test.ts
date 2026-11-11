import { describe, expect, it } from "vitest"
import { ICON_ROWS, glyphMatches, lucideBaseName, replacementsFor } from "./icon-catalog"

describe("ICON_ROWS", () => {
  // The icons page draws every row: a row the generator emits must resolve to its drawing in glyphs.json.
  it("finds the drawing of every candidate row", () => {
    for (const row of ICON_ROWS.filter((r) => r.status !== "exploring")) {
      expect(row.glyph, row.name).not.toBeNull()
      expect(row.glyph?.name).toBe(row.name)
    }
  })

  it("lists each row once, by name", () => {
    expect(new Set(ICON_ROWS.map((r) => r.name)).size).toBe(ICON_ROWS.length)
  })

  it("matches a name, a meaning or a replaced Lucide name", () => {
    const row = ICON_ROWS.find((r) => r.name === "check-circle")
    if (!row) throw new Error("check-circle missing")
    expect(glyphMatches(row, "")).toBe(true)
    expect(glyphMatches(row, " CHECK-circle ")).toBe(true)
    expect(glyphMatches(row, "pebble")).toBe(true)
    expect(glyphMatches(row, "circlecheckbig")).toBe(true)
    expect(glyphMatches(row, "umbrella")).toBe(false)
  })
})

describe("lucide names", () => {
  it("strips the alias spellings", () => {
    expect(lucideBaseName("Trash2Icon")).toBe("Trash2")
    expect(lucideBaseName("LucideTrash2")).toBe("Trash2")
    expect(lucideBaseName("Icon")).toBe("Icon")
  })

  it("finds the drawing that replaces a Lucide icon", () => {
    expect(replacementsFor("CircleCheckBig").map((r) => r.name)).toContain("check-circle")
  })
})
