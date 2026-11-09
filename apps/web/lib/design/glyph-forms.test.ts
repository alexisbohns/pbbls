import { describe, expect, it } from "vitest"
import { DEFAULT_VIEW, GLYPHS, describeSigns, formChoices, formKey, formPaths, glyphForms, needsReview, showsGlyph, signsOf, viewForm, viewMatches, type Glyph } from "./glyph-forms"

const find = (test: (g: Glyph) => boolean, what: string): Glyph => {
  const g = GLYPHS.find(test)
  if (!g) throw new Error(`no ${what}`)
  return g
}

describe("glyph forms", () => {
  it("reads every glyph from glyphs.json", () => {
    expect(GLYPHS.length).toBeGreaterThan(900)
    expect(new Set(GLYPHS.map((g) => g.name)).size).toBe(GLYPHS.length)
  })

  it("lists a plain glyph's single form and draws a missing form as regular", () => {
    const plain = find((g) => g.steps === 0 && g.carved.solid === undefined && g.dotted === null && !g.paths.some((p) => p.role === "mark"), "plain glyph")
    expect(glyphForms(plain).map(formKey)).toEqual(["regular"])
    expect(formPaths(plain, { fill: { kind: "solid" } }).every((p) => !p.fill)).toBe(true)
  })

  it("lists each fill then the same fill marked", () => {
    const marked = find((g) => g.carved.solid !== undefined && g.paths.some((p) => p.role === "mark"), "glyph with a body and a mark")
    expect(glyphForms(marked).map(formKey).slice(0, 4)).toEqual(["regular", "marked", "solid", "solid-marked"])
    expect(formChoices(marked)[3]?.label).toBe("solid · marked")
  })

  it("draws a hidden step only once progress reaches it", () => {
    const cromlech = find((g) => g.name === "cromlech", "cromlech")
    const hidden = cromlech.paths.find((p) => p.hidden && p.role === "step")
    if (!hidden) throw new Error("cromlech has no hidden step")
    const at = (k: number) => formPaths(cromlech, { fill: { kind: "progress", k } })
    expect(at(0).some((p) => p.d === hidden.d)).toBe(false)
    expect(at(hidden.step ?? 1).find((p) => p.d === hidden.d)?.fill).toBe(true)
  })

  it("carves a filled step through the lines inside it", () => {
    const pouch = find((g) => g.name === "pouch", "pouch")
    const carved = pouch.paths.filter((p) => p.fill !== undefined && p.cuts)
    expect(carved.length).toBeGreaterThan(0)
    const drawn = formPaths(pouch, { fill: { kind: "progress", k: pouch.steps } })
    expect(drawn.some((p) => p.fill && p.evenOdd && !p.stroke)).toBe(true)
    for (const c of carved) for (const i of c.cuts ?? []) expect(drawn.some((p) => p.d === pouch.paths[i]?.d)).toBe(false)
  })

  it("draws a solid from its carved fill", () => {
    const solid = find((g) => g.carved.solid !== undefined, "glyph with a body")
    expect(formPaths(solid, { fill: { kind: "solid" } })[0]).toEqual({ d: solid.carved.solid?.d, fill: true, evenOdd: true, stroke: false })
  })

  it("flags glyphs the review lists", () => {
    expect(typeof needsReview("home")).toBe("boolean")
  })
})

describe("the page's view", () => {
  const marked = find((g) => g.carved.solid !== undefined && g.paths.some((p) => p.role === "mark"), "glyph with a body and a mark")
  const stepped = find((g) => g.steps > 1, "glyph with steps")
  const plain = find((g) => g.steps === 0 && g.carved.solid === undefined && g.dotted === null && !g.paths.some((p) => p.role === "mark"), "plain glyph")

  it("lists every glyph by default and draws it regular", () => {
    expect(viewMatches(plain, DEFAULT_VIEW)).toBe(true)
    expect(viewForm(marked, DEFAULT_VIEW)).toEqual({ fill: { kind: "regular" }, marked: false })
  })

  it("filters and draws by fill, mark and progress", () => {
    const solid = { ...DEFAULT_VIEW, fill: "solid" as const }
    expect(viewMatches(marked, solid)).toBe(true)
    expect(viewMatches(plain, solid)).toBe(false)
    expect(viewForm(marked, { ...solid, mark: "marked" })).toEqual({ fill: { kind: "solid" }, marked: true })
    expect(viewMatches(plain, { ...DEFAULT_VIEW, fill: "regular" })).toBe(true)
    expect(viewMatches(marked, { ...DEFAULT_VIEW, fill: "regular" })).toBe(false)
    expect(viewMatches(plain, { ...DEFAULT_VIEW, mark: "marked" })).toBe(false)
    expect(viewMatches(plain, { ...DEFAULT_VIEW, mark: "unmarked" })).toBe(true)
    const progress = { ...DEFAULT_VIEW, progress: true, k: 99 }
    expect(viewMatches(plain, progress)).toBe(false)
    expect(viewForm(stepped, progress).fill).toEqual({ kind: "progress", k: stepped.steps })
  })

  it("names the forms a glyph has beyond regular", () => {
    expect(describeSigns(signsOf(plain))).toBe("regular only")
    const signs = signsOf(stepped)
    expect(signs.steps).toBe(stepped.steps)
    expect(describeSigns(signs)).toContain(`${stepped.steps} steps`)
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
