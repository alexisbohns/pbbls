import { drawForm, formsOf } from "@pbbls/icons/runtime"
import { describe, expect, it } from "vitest"
import { CATEGORIES, DEFAULT_VIEW, GLYPHS, describeSigns, firstReview, formChoices, glyphInCategory, glyphMatchesQuery, needsReview, showsGlyph, signsOf, viewForm, viewMatches, type Glyph } from "./glyph-forms"

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
    expect(formsOf(plain)).toEqual(["regular"])
    expect(drawForm(plain, { fill: { kind: "solid" } }).every((p) => !p.fill)).toBe(true)
  })

  it("lists each fill then the same fill marked", () => {
    const marked = find((g) => g.carved.solid !== undefined && g.paths.some((p) => p.role === "mark"), "glyph with a body and a mark")
    expect(formsOf(marked).slice(0, 4)).toEqual(["regular", "marked", "solid", "solid-marked"])
    expect(formChoices(marked)[3]?.label).toBe("solid · marked")
  })

  it("draws a hidden step only once progress reaches it", () => {
    const stepped = find((g) => g.steps > 1 && g.paths.some((p) => p.hidden && p.role === "step"), "glyph with a hidden step")
    const hidden = stepped.paths.find((p) => p.hidden && p.role === "step")
    if (!hidden) throw new Error("no hidden step")
    const at = (k: number) => drawForm(stepped, { fill: { kind: "progress", k } })
    expect(at(0).some((p) => p.d === hidden.d)).toBe(false)
    expect(at(hidden.step ?? 1).find((p) => p.d === hidden.d)?.fill).toBe(true)
  })

  it("carves a lit mark through the lines inside it", () => {
    const glyph = find((g) => g.paths.some((p) => p.role === "mark" && p.fill !== undefined && (p.cuts?.length ?? 0) > 0), "glyph with a carved mark")
    const carved = glyph.paths.filter((p) => p.role === "mark" && p.fill !== undefined && p.cuts)
    const drawn = drawForm(glyph, { fill: { kind: "regular" }, marked: true })
    expect(drawn.some((p) => p.fill && p.evenOdd && !p.stroke)).toBe(true)
    for (const c of carved) for (const i of c.cuts ?? []) expect(drawn.some((p) => p.d === glyph.paths[i]?.d)).toBe(false)
  })

  it("draws a solid from its carved fill", () => {
    const solid = find((g) => g.carved.solid !== undefined, "glyph with a body")
    expect(drawForm(solid, { fill: { kind: "solid" } })[0]).toEqual({ d: solid.carved.solid?.d, fill: true, evenOdd: true, stroke: false })
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
  it("lists every glyph, the flagged ones, or those that need work", () => {
    expect(showsGlyph(unflagged, "all", {})).toBe(true)
    expect(showsGlyph(flagged, "review", {})).toBe(true)
    expect(showsGlyph(unflagged, "review", {})).toBe(false)
    expect(showsGlyph(unflagged, "fix", { [unflagged]: "fix" })).toBe(true)
    expect(showsGlyph(flagged, "fix", { [flagged]: "ok" })).toBe(false)
    expect(showsGlyph(unflagged, "fix", { [unflagged]: "solid" })).toBe(true)
    expect(showsGlyph(unflagged, "fix", {})).toBe(false)
  })
})

describe("round 2", () => {
  it("lists the glyphs touched since the first review and keeps their first verdict and note", () => {
    const touched = GLYPHS.find((g) => showsGlyph(g.name, "round2", {}))
    if (!touched) throw new Error("no glyph in round2.json")
    expect(showsGlyph(touched.name, "round2", {})).toBe(true)
    expect(firstReview("home")).toEqual({ verdict: "fix", note: expect.stringContaining("solid") })
    expect(firstReview("path")).toEqual({ verdict: "ok", note: undefined })
  })
})

describe("categories", () => {
  it("reads the 35 categories in order, and every glyph sits in one to three known ones", () => {
    expect(CATEGORIES).toHaveLength(35)
    expect(CATEGORIES[0]?.id).toBe("stones")
    const ids = new Set(CATEGORIES.map((c) => c.id))
    expect(ids.size).toBe(CATEGORIES.length)
    for (const g of GLYPHS) {
      const list = g.tags.category ?? []
      expect(list.length, g.name).toBeGreaterThanOrEqual(1)
      expect(list.length, g.name).toBeLessThanOrEqual(3)
      for (const id of list) expect(ids.has(id), `${g.name}: ${id}`).toBe(true)
    }
  })

  it("filters to the glyphs whose categories include the one picked", () => {
    const sky = GLYPHS.filter((g) => glyphInCategory(g, "sky"))
    expect(sky.length).toBeGreaterThan(40)
    expect(sky.every((g) => g.tags.category?.includes("sky"))).toBe(true)
    expect(GLYPHS.every((g) => glyphInCategory(g, "all"))).toBe(true)
    const pointer = GLYPHS.find((g) => g.name === "cursor-pointer")
    expect(pointer && glyphInCategory(pointer, "devices")).toBe(true)
    expect(pointer && glyphInCategory(pointer, "sky")).toBe(false)
  })

  it("searches category labels too: weather finds sky glyphs", () => {
    const sun = GLYPHS.find((g) => g.tags.category?.[0] === "sky")
    if (!sun) throw new Error("no sky glyph")
    expect(glyphMatchesQuery(sun, "weather")).toBe(true)
    expect(glyphMatchesQuery(sun, "WEATHER")).toBe(true)
    expect(GLYPHS.filter((g) => glyphMatchesQuery(g, "weather")).length).toBeGreaterThanOrEqual(GLYPHS.filter((g) => g.tags.category?.includes("sky")).length)
    const plant = GLYPHS.find((g) => g.tags.category?.[0] === "plants")
    expect(plant && glyphMatchesQuery(plant, "weather")).toBe(false)
  })
})
