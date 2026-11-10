import { describe, expect, it } from "vitest"
import { formKey, formKeysOf, formsOf, parseFormKey, renderForm, type Form, type GlyphData } from "./forms"

const SQ = "M4 4 L20 4 L20 20 L4 20 Z"
const regular: Form = { fill: { kind: "regular" } }

const glyph: GlyphData = {
  name: "path-river",
  meaning: "m",
  family: null,
  tags: {},
  paths: [
    { d: SQ, role: "body" },
    { d: "M8 12 L16 12" },
    { d: "M6 6 L7 7", role: "step", step: 1 },
    { d: "M9 9 L10 10", role: "step", step: 2 },
    { d: "M12 12 L13 13", role: "mark" },
  ],
  parts: [],
  steps: 2,
  carved: { solid: { d: "M1 1 L2 2 Z", keep: [], lit: [] }, "solid-marked": { d: "M1 1 L3 3 Z", keep: [], lit: [] } },
  dotted: null,
  overrides: {},
}

describe("form keys", () => {
  it("joins a fill and the mark", () => {
    expect(formKey(regular)).toBe("regular")
    expect(formKey({ fill: { kind: "regular" }, marked: true })).toBe("marked")
    expect(formKey({ fill: { kind: "solid" }, marked: true })).toBe("solid-marked")
    expect(formKey({ fill: { kind: "parts", parts: ["left", "middle"] } })).toBe("parts-left+middle")
    expect(formKey({ fill: { kind: "progress", k: 2 }, marked: true })).toBe("progress-2-marked")
  })

  it("parses every key it writes and refuses the rest", () => {
    for (const key of ["regular", "marked", "dotted", "solid", "solid-marked", "parts-moon", "parts-left+middle-marked", "progress-3", "progress-3-marked"]) {
      const form = parseFormKey(key)
      expect(form && formKey(form)).toBe(key)
    }
    for (const key of ["half", "regular-marked", "marked-marked", "dotted-marked", "parts-", "parts-Moon", "solid-half", "active", "lit-1", "progress-0", "progress-01"]) expect(parseFormKey(key)).toBeNull()
  })

  it("lists each fill then the same fill marked, dotted after regular", () => {
    expect(formKeysOf({ mark: true, body: true, parts: ["moon", "cloud"], steps: 1, dotted: true })).toEqual([
      "regular",
      "marked",
      "dotted",
      "solid",
      "solid-marked",
      "parts-moon",
      "parts-moon-marked",
      "parts-cloud",
      "parts-cloud-marked",
      "progress-1",
      "progress-1-marked",
    ])
    expect(formKeysOf({ mark: false, body: false, parts: [], steps: 0, dotted: false })).toEqual(["regular"])
  })
})

describe("renderForm", () => {
  it("lists the forms the roles allow", () => {
    expect(formsOf(glyph)).toEqual(["regular", "marked", "solid", "solid-marked", "progress-1", "progress-1-marked", "progress-2", "progress-2-marked"])
  })

  it("draws regular as strokes only", () => {
    expect(renderForm(glyph, regular).every((p) => p.stroke && !p.fill)).toBe(true)
  })

  it("fills the mark when marked and the first k steps for progress", () => {
    expect(renderForm(glyph, { fill: { kind: "regular" }, marked: true }).map((p) => p.fill)).toEqual([false, false, false, false, true])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 1 } }).map((p) => p.fill)).toEqual([false, false, true, false, false])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 2 }, marked: true }).map((p) => p.fill)).toEqual([false, false, true, true, true])
    expect(renderForm(glyph, { fill: { kind: "progress", k: 0 } })).toEqual(renderForm(glyph, regular))
  })

  it("draws solid and solid-marked from their carved fills", () => {
    expect(renderForm(glyph, { fill: { kind: "solid" } })).toEqual([{ d: "M1 1 L2 2 Z", fill: true, evenOdd: true, stroke: false }])
    expect(renderForm(glyph, { fill: { kind: "solid" }, marked: true })[0]?.d).toBe("M1 1 L3 3 Z")
  })

  it("draws the kept lines stroked and a lit mark filled beside a carved fill", () => {
    const g: GlyphData = { ...glyph, carved: { ...glyph.carved, "solid-marked": { d: "M1 1 L3 3 Z", keep: [1], lit: [4] } } }
    expect(renderForm(g, { fill: { kind: "solid" }, marked: true })).toEqual([
      { d: "M1 1 L3 3 Z", fill: true, evenOdd: true, stroke: false },
      { d: "M8 12 L16 12", fill: false, evenOdd: false, stroke: true },
      { d: "M12 12 L13 13", fill: true, evenOdd: false, stroke: true },
    ])
  })

  it("draws a parts form from its carved fill", () => {
    const parted: GlyphData = {
      ...glyph,
      paths: [
        { d: "M4 4 L10 4 L10 10 Z", role: "body", part: "left" },
        { d: "M14 4 L20 4 L20 10 Z", role: "body", part: "right" },
      ],
      parts: ["left", "right"],
      steps: 0,
      carved: { solid: { d: "M0 0 Z", keep: [], lit: [] }, "parts-left": { d: "M5 5 Z", keep: [1], lit: [] }, "parts-right": { d: "M15 5 Z", keep: [0], lit: [] } },
    }
    expect(formsOf(parted)).toEqual(["regular", "solid", "parts-left", "parts-right"])
    expect(renderForm(parted, { fill: { kind: "parts", parts: ["left"] } }).map((p) => [p.d, p.fill])).toEqual([
      ["M5 5 Z", true],
      ["M14 4 L20 4 L20 10 Z", false],
    ])
  })

  it("draws a drawn dotted form as it is", () => {
    const dotted: GlyphData = { ...glyph, dotted: [{ d: "M2 2 L3 3", fill: false, evenOdd: false, stroke: true }] }
    expect(formsOf(dotted).slice(0, 3)).toEqual(["regular", "marked", "dotted"])
    expect(renderForm(dotted, { fill: { kind: "dotted" } })).toEqual(dotted.dotted)
  })

  it("lets an override replace a form", () => {
    const over: GlyphData = { ...glyph, overrides: { "solid-marked": [{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }] } }
    expect(renderForm(over, { fill: { kind: "solid" }, marked: true })).toEqual([{ d: "M5 5 L6 6", fill: true, evenOdd: false, stroke: true }])
  })

  it("refuses a form the glyph does not have", () => {
    expect(() => renderForm({ ...glyph, paths: glyph.paths.slice(1), carved: {} }, { fill: { kind: "solid" } })).toThrow(/path-river has no solid form/)
    expect(() => renderForm(glyph, { fill: { kind: "progress", k: 3 } })).toThrow(/path-river has no progress-3 form/)
    expect(() => renderForm(glyph, { fill: { kind: "dotted" } })).toThrow(/path-river has no dotted form/)
  })

  it("draws a carved step fill instead of the plain path and omits its cuts", () => {
    const carved: GlyphData = {
      ...glyph,
      paths: [{ d: SQ, role: "step", step: 1, fill: "M0 0 L1 1 Z", cuts: [1] }, { d: "M8 12 L16 12" }, { d: "M2 2 L3 3" }],
      steps: 1,
      carved: {},
    }
    expect(renderForm(carved, { fill: { kind: "progress", k: 1 } })).toEqual([
      { d: "M0 0 L1 1 Z", fill: true, evenOdd: true, stroke: false },
      { d: "M2 2 L3 3", fill: false, evenOdd: false, stroke: true },
    ])
    expect(renderForm(carved, regular)).toHaveLength(3)
  })

  it("omits a hidden path at rest and draws it filled once its role fills", () => {
    const hidden: GlyphData = {
      ...glyph,
      paths: [
        { d: SQ, role: "body" },
        { d: "M8 12 L16 12" },
        { d: "M6 6 L7 7", role: "step", step: 1, hidden: true },
        { d: "M9 9 L10 10", role: "step", step: 2 },
        { d: "M12 12 L13 13", role: "mark", hidden: true },
      ],
    }
    expect(renderForm(hidden, regular).map((p) => p.d)).toEqual([SQ, "M8 12 L16 12", "M9 9 L10 10"])
    expect(renderForm(hidden, { fill: { kind: "progress", k: 1 } }).map((p) => [p.d, p.fill])).toEqual([
      [SQ, false],
      ["M8 12 L16 12", false],
      ["M6 6 L7 7", true],
      ["M9 9 L10 10", false],
    ])
    expect(renderForm(hidden, { fill: { kind: "regular" }, marked: true }).map((p) => [p.d, p.fill])).toEqual([
      [SQ, false],
      ["M8 12 L16 12", false],
      ["M9 9 L10 10", false],
      ["M12 12 L13 13", true],
    ])
  })
})
