import { describe, expect, it } from "vitest"
import { formKey, formsOf, renderForm, type GlyphData } from "./forms"

const glyph: GlyphData = {
  name: "path-river",
  meaning: "m",
  family: null,
  tags: {},
  paths: [
    { d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" },
    { d: "M8 12 L16 12" },
    { d: "M6 6 L7 7", role: "mark", mark: 1 },
    { d: "M9 9 L10 10", role: "mark", mark: 2 },
    { d: "M12 12 L13 13", role: "focus" },
  ],
  marks: 2,
  solid: { d: "M1 1 L2 2 Z", keep: [] },
  overrides: {},
}

describe("forms", () => {
  it("lists the forms the roles allow", () => {
    expect(formsOf(glyph)).toEqual(["regular", "half", "solid", "progress-1", "progress-2"])
    expect(formsOf({ ...glyph, paths: [{ d: "M3 3 L4 4" }], marks: 0, solid: null })).toEqual(["regular"])
  })

  it("draws regular as strokes only", () => {
    expect(renderForm(glyph, { kind: "regular" }).every((p) => p.stroke && !p.fill)).toBe(true)
  })

  it("fills the focus for half and the first k marks for progress", () => {
    expect(renderForm(glyph, { kind: "half" }).map((p) => p.fill)).toEqual([false, false, false, false, true])
    expect(renderForm(glyph, { kind: "progress", k: 1 }).map((p) => p.fill)).toEqual([false, false, true, false, false])
    expect(renderForm(glyph, { kind: "progress", k: 0 })).toEqual(renderForm(glyph, { kind: "regular" }))
  })

  it("draws solid as the carved path plus the kept lines", () => {
    const out = renderForm(glyph, { kind: "solid" })
    expect(out[0]).toEqual({ d: "M1 1 L2 2 Z", fill: true, evenOdd: true, stroke: false })
    expect(out).toHaveLength(1)
  })

  it("lets an override replace a form", () => {
    const over = { ...glyph, overrides: { half: [{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }] } }
    expect(renderForm(over, { kind: "half" })).toEqual([{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }])
    expect(formKey({ kind: "progress", k: 2 })).toBe("progress-2")
  })

  it("refuses a form the glyph does not have", () => {
    expect(() => renderForm({ ...glyph, solid: null }, { kind: "solid" })).toThrow(/path-river has no solid form/)
    expect(() => renderForm(glyph, { kind: "progress", k: 3 })).toThrow(/path-river has no progress-3 form/)
  })

  it("draws a carved fill instead of the plain path and omits its cuts", () => {
    const carved: GlyphData = {
      ...glyph,
      paths: [{ d: "M4 4 L20 4 L20 20 L4 20 Z", role: "mark", mark: 1, fill: "M0 0 L1 1 Z", cuts: [1] }, { d: "M8 12 L16 12" }, { d: "M2 2 L3 3" }],
      marks: 1,
      solid: null,
    }
    expect(renderForm(carved, { kind: "progress", k: 1 })).toEqual([{ d: "M0 0 L1 1 Z", fill: true, evenOdd: true, stroke: false }, { d: "M2 2 L3 3", fill: false, evenOdd: false, stroke: true }])
    expect(renderForm(carved, { kind: "regular" }).map((p) => p.d)).toHaveLength(3)
  })

  describe("a hidden path", () => {
    const dotted: GlyphData = {
      ...glyph,
      paths: [
        { d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" },
        { d: "M8 12 L16 12" },
        { d: "M6 6 L7 7", role: "mark", mark: 1, hidden: true },
        { d: "M9 9 L10 10", role: "mark", mark: 2 },
        { d: "M12 12 L13 13", role: "focus", hidden: true },
      ],
      solid: { d: "M1 1 L2 2 Z", keep: [0] },
    }

    it("is omitted in regular and drawn filled once its role fills", () => {
      expect(renderForm(dotted, { kind: "regular" }).map((p) => p.d)).toEqual(["M4 4 L20 4 L20 20 L4 20 Z", "M8 12 L16 12", "M9 9 L10 10"])
      expect(renderForm(dotted, { kind: "progress", k: 1 }).map((p) => [p.d, p.fill])).toEqual([
        ["M4 4 L20 4 L20 20 L4 20 Z", false],
        ["M8 12 L16 12", false],
        ["M6 6 L7 7", true],
        ["M9 9 L10 10", false],
      ])
      expect(renderForm(dotted, { kind: "progress", k: 2 }).map((p) => p.fill)).toEqual([false, false, true, true])
    })

    it("draws a hidden focus filled in half and omits a hidden mark there", () => {
      expect(renderForm(dotted, { kind: "half" }).map((p) => [p.d, p.fill])).toEqual([
        ["M4 4 L20 4 L20 20 L4 20 Z", false],
        ["M8 12 L16 12", false],
        ["M9 9 L10 10", false],
        ["M12 12 L13 13", true],
      ])
    })

    it("is left out of solid, whose keep indexes the visible non-body paths", () => {
      const out = renderForm(dotted, { kind: "solid" })
      expect(out.map((p) => p.d)).toEqual(["M1 1 L2 2 Z", "M8 12 L16 12"])
    })
  })
})
