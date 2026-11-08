import { describe, expect, it } from "vitest"
import { ICON_GLYPHS, ICON_ROWS, componentName, glyphMatches, lucideBaseName, replacementsFor } from "./icon-catalog"

describe("ICON_ROWS", () => {
  // The icons page draws every row: a row the generator emits must resolve.
  it("finds the generated component of every candidate row and twin", () => {
    for (const row of ICON_ROWS.filter((r) => r.status !== "exploring")) {
      expect(row.Icon, row.name).not.toBeNull()
      for (const twin of row.twins) expect(twin.Icon, `${row.name}.${twin.variant}`).not.toBeNull()
    }
  })
})

describe("ICON_GLYPHS", () => {
  it("lists every base and every twin once, named like its source file", () => {
    const twins = ICON_ROWS.reduce((n, row) => n + row.twins.length, 0)
    expect(ICON_GLYPHS).toHaveLength(ICON_ROWS.length + twins)
    expect(new Set(ICON_GLYPHS.map((g) => g.name)).size).toBe(ICON_GLYPHS.length)
    const home = ICON_ROWS.find((r) => r.name === "home")
    expect(home?.twins.map((t) => t.variant)).toContain("active")
    expect(ICON_GLYPHS.find((g) => g.name === "home.active")?.variant).toBe("active")
  })

  it("matches a name, a meaning or a replaced Lucide name", () => {
    const glyph = ICON_GLYPHS.find((g) => g.name === "check-circle")
    if (!glyph) throw new Error("check-circle missing")
    expect(glyphMatches(glyph, "")).toBe(true)
    expect(glyphMatches(glyph, " CHECK-circle ")).toBe(true)
    expect(glyphMatches(glyph, "pebble")).toBe(true)
    expect(glyphMatches(glyph, "circlecheckbig")).toBe(true)
    expect(glyphMatches(glyph, "umbrella")).toBe(false)
  })
})

describe("componentName", () => {
  it("follows the generator's naming", () => {
    expect(componentName("check-circle-dot")).toBe("CheckCircleDot")
    expect(componentName("path", "lit-1")).toBe("PathLit1")
    expect(componentName("home", "active")).toBe("HomeActive")
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
