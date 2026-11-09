import { describe, expect, it } from "vitest"
import { GLYPHS, availableForms, formPaths, needsReview, showsGlyph } from "./glyph-forms"

describe("glyph forms", () => {
  it("reads every glyph from glyphs.json", () => {
    expect(GLYPHS.length).toBeGreaterThan(900)
    expect(new Set(GLYPHS.map((g) => g.name)).size).toBe(GLYPHS.length)
  })

  it("lists forms and falls back to regular for a form a glyph lacks", () => {
    const plain = GLYPHS.find((g) => g.marks === 0 && g.solid === null && !g.paths.some((p) => p.role === "focus"))
    if (!plain) throw new Error("no plain glyph")
    expect(availableForms(plain)).toEqual(["regular"])
    expect(formPaths(plain, "solid").every((p) => !p.fill)).toBe(true)
  })

  it("draws a hidden mark only once progress reaches it", () => {
    const cromlech = GLYPHS.find((g) => g.name === "cromlech")
    if (!cromlech) throw new Error("no cromlech")
    const hidden = cromlech.paths.find((p) => p.hidden && p.role === "mark")
    if (!hidden) throw new Error("cromlech has no hidden mark")
    const drawn = (k: number) => formPaths(cromlech, "progress", k).find((p) => p.d === hidden.d)
    expect(drawn(0)).toBeUndefined()
    expect(formPaths(cromlech, "regular").some((p) => p.d === hidden.d)).toBe(false)
    expect(drawn(hidden.mark ?? 1)?.fill).toBe(true)
    expect(formPaths(cromlech, "progress", hidden.mark ?? 1)).toHaveLength(cromlech.paths.length)
    expect(formPaths(cromlech, "progress", 0)).toHaveLength(cromlech.paths.length - 1)
  })

  it("carves a filled mark through the lines inside it", () => {
    const pouch = GLYPHS.find((g) => g.name === "pouch")
    if (!pouch) throw new Error("no pouch")
    const carved = pouch.paths.flatMap((p) => (p.fill !== undefined && p.cuts ? [p] : []))
    expect(carved.length).toBeGreaterThan(0)
    const drawn = formPaths(pouch, "progress", pouch.marks)
    expect(drawn.some((p) => p.fill && p.evenOdd && !p.stroke)).toBe(true)
    for (const c of carved) for (const i of c.cuts ?? []) expect(drawn.some((p) => p.d === pouch.paths[i]?.d)).toBe(false)
    expect(formPaths(pouch, "regular")).toHaveLength(pouch.paths.length)
  })

  it("flags glyphs the review lists", () => {
    expect(typeof needsReview("home")).toBe("boolean")
  })
})

describe("showsGlyph", () => {
  const flagged = GLYPHS.find((g) => needsReview(g.name))?.name ?? ""
  const unflagged = GLYPHS.find((g) => !needsReview(g.name))?.name ?? ""
  it("lists every glyph, the flagged ones, or those marked fix", () => {
    expect(showsGlyph(unflagged, "all", {})).toBe(true)
    expect(showsGlyph(flagged, "review", {})).toBe(true)
    expect(showsGlyph(unflagged, "review", {})).toBe(false)
    expect(showsGlyph(unflagged, "fix", { [unflagged]: "fix" })).toBe(true)
    expect(showsGlyph(flagged, "fix", { [flagged]: "ok" })).toBe(false)
  })
})
