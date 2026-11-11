import { readFileSync } from "node:fs"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import type { GlyphData, RenderedPath } from "../scripts/lib/forms"
import { formsOf, parseFormKey } from "../scripts/lib/forms"
import { DOTTED, MARKED, REGULAR, SOLID, drawForm, formKey, progress, resolveWith } from "./forms"

const ROOT = join(__dirname, "..")
const data = JSON.parse(readFileSync(join(ROOT, "glyphs/glyphs.json"), "utf8")) as { states: Record<string, string[]>; glyphs: GlyphData[] }
const contract = JSON.parse(readFileSync(join(ROOT, "glyphs/forms-contract.json"), "utf8")) as {
  glyphs: Record<string, { forms: string[]; rendered: Record<string, RenderedPath[]>; states: Record<string, string> }>
}
const byName = new Map(data.glyphs.map((g) => [g.name, g]))

describe("the runtime against the forms contract", () => {
  for (const [name, entry] of Object.entries(contract.glyphs)) {
    const glyph = byName.get(name)
    it(`${name}: forms, renders and states`, () => {
      expect(glyph).toBeDefined()
      if (!glyph) return
      expect(formsOf(glyph)).toEqual(entry.forms)
      for (const [key, expected] of Object.entries(entry.rendered)) {
        const form = parseFormKey(key)
        expect(form, key).not.toBeNull()
        if (form) expect(drawForm(glyph, form), key).toEqual(expected)
      }
      for (const [spec, expected] of Object.entries(entry.states)) {
        const [state, k] = spec.split("@")
        // Compare keys: `progress@0` resolves to `progress-0`, a key no glyph lists but the contract records.
        expect(formKey(resolveWith(data.states, entry.forms, state ?? "", Number(k ?? 0))), spec).toBe(expected)
      }
    })
  }

  it("draws regular for a form the glyph does not have, instead of throwing", () => {
    const stack = byName.get("stack")
    if (!stack) throw new Error("stack missing")
    expect(drawForm(stack, SOLID)).toEqual(drawForm(stack, REGULAR))
    expect(drawForm(stack, progress(0))).toEqual(drawForm(stack, REGULAR))
  })

  it("names the common forms", () => {
    expect([REGULAR, MARKED, SOLID, DOTTED, progress(2)]).toEqual([
      { fill: { kind: "regular" } },
      { fill: { kind: "regular" }, marked: true },
      { fill: { kind: "solid" } },
      { fill: { kind: "dotted" } },
      { fill: { kind: "progress", k: 2 } },
    ])
  })
})
