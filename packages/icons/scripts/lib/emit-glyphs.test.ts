import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { buildGlyph, CONTRACT_GLYPHS, formsContract, loadGlyphs } from "./emit-glyphs"
import { formsOf, renderForm } from "./forms"
import { filledArea } from "./lint"
import type { IconRow } from "./manifest"

const row: IconRow = { name: "ring", meaning: "A ring.", family: "ring", tags: { wrapper: ["circle"] }, wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
const source = (paths: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">\n${paths}\n</svg>\n`

describe("buildGlyph", () => {
  it("stores normalised paths with roles, the step count and the carved solid", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="step" data-step="1"/>\n  <path d="M2 22 A1 1 0 0 1 4 22"/>'), {})
    expect(g.paths[0]).toEqual({ d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" })
    expect(g.paths[1]).toEqual({ d: "M8 12 L16 12", role: "step", step: 1 })
    expect(g.paths[2]?.d).toMatch(/^M2 22 C/)
    expect(g.steps).toBe(1)
    expect(g.parts).toEqual([])
    expect(g.dotted).toBeNull()
    // keep indexes paths: the arc below the body stays stroked.
    expect(g.carved.solid?.keep).toEqual([2])
    expect(formsOf(g)).toEqual(["regular", "solid", "progress-1"])
    expect(g.family).toBe("ring")
    expect(g.tags).toEqual({ wrapper: ["circle"] })
  })

  it("stores overrides by form key, as rendered paths", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'), {
      solid: source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'),
    })
    expect(renderForm(g, { fill: { kind: "solid" } })).toEqual([{ d: "M4 4 L20 4 L20 20 L4 20 Z", fill: true, evenOdd: false, stroke: false }])
  })

  it("carves a fill per part combination, the other parts stroked", () => {
    const g = buildGlyph(row, source('  <path d="M3 4 L11 4 L11 20 L3 20 Z" data-role="body" data-part="left"/>\n  <path d="M13 4 L21 4 L21 20 L13 20 Z" data-role="body" data-part="right"/>'), {})
    expect(g.parts).toEqual(["left", "right"])
    expect(g.paths[0]).toEqual({ d: "M3 4 L11 4 L11 20 L3 20 Z", role: "body", part: "left" })
    expect(Object.keys(g.carved)).toEqual(["solid", "parts-left", "parts-right"])
    expect(g.carved["parts-left"]?.keep).toEqual([1])
    expect(formsOf(g)).toEqual(["regular", "solid", "parts-left", "parts-right"])
  })

  it("cuts the mark out of a marked solid, and fills a mark that lies outside the body", () => {
    const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'
    const inside = buildGlyph(row, source(`${body}\n  <path d="M11 11 L13 11 L13 13 L11 13 Z" data-role="mark" data-rest="hidden"/>`), {})
    expect(Object.keys(inside.carved)).toEqual(["solid", "solid-marked"])
    expect(inside.carved["solid-marked"]?.lit).toEqual([])
    expect(filledArea(inside.carved["solid-marked"]?.d ?? "", true)).toBeLessThan(filledArea(inside.carved.solid?.d ?? "", true) - 4)
    const outside = buildGlyph(row, source(`${body}\n  <path d="M2 22 L3 22" data-role="mark"/>`), {})
    expect(outside.carved["solid-marked"]?.lit).toEqual([1])
    expect(outside.carved.solid?.keep).toEqual([1])
    expect(renderForm(outside, { fill: { kind: "solid" }, marked: true }).map((p) => [p.d, p.fill])).toEqual([
      [outside.carved["solid-marked"]?.d, true],
      ["M2 22 L3 22", true],
    ])
  })
})

describe("hidden paths", () => {
  const hidden = '  <path d="M10 10 L11 11" data-role="step" data-step="1" data-rest="hidden"/>'
  const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12"/>'

  it("writes hidden: true and leaves the path out of the carve", () => {
    const g = buildGlyph(row, source(`${body}\n${hidden}`), {})
    expect(g.paths[2]).toEqual({ d: "M10 10 L11 11", role: "step", step: 1, hidden: true })
    expect(g.paths[0]).not.toHaveProperty("hidden")
    expect(g.carved.solid?.keep).toEqual([])
    const withoutDot = buildGlyph(row, source(body), {})
    expect(g.carved.solid?.d).toBe(withoutDot.carved.solid?.d)
  })

  it("keeps keep consistent with renderForm when a hidden path comes before a kept line", () => {
    const g = buildGlyph(row, source(`${body.split("\n")[0]}\n${hidden}\n  <path d="M2 22 L4 22"/>`), {})
    expect(g.carved.solid?.keep).toEqual([2])
    expect(renderForm(g, { fill: { kind: "solid" } }).map((p) => p.d).slice(1)).toEqual(["M2 22 L4 22"])
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } }).filter((p) => p.fill).map((p) => p.d)).toEqual(["M10 10 L11 11"])
    expect(renderForm(g, { fill: { kind: "regular" } }).map((p) => p.d)).not.toContain("M10 10 L11 11")
  })
})

describe("carved role fills", () => {
  const square = "M4 4 L20 4 L20 20 L4 20 Z"
  it("gives a step with a line inside a carved fill and the line's index as a cut", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="step" data-step="1"/>\n  <path d="M8 12 L16 12"/>`), {})
    expect(g.paths[0]?.cuts).toEqual([1])
    expect(g.paths[0]?.fill).toMatch(/^M/)
    expect(g.paths[1]).not.toHaveProperty("fill")
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } })).toEqual([{ d: g.paths[0]?.fill, fill: true, evenOdd: true, stroke: false }])
    expect(renderForm(g, { fill: { kind: "regular" } }).map((p) => p.d)).toEqual([square, "M8 12 L16 12"])
  })

  it("stores nothing on a step with nothing inside", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="step" data-step="1"/>\n  <path d="M2 22 L3 22"/>`), {})
    expect(g.paths[0]).toEqual({ d: square, role: "step", step: 1 })
    expect(renderForm(g, { fill: { kind: "progress", k: 1 } }).map((p) => [p.d, p.fill, p.evenOdd])).toEqual([
      [square, true, false],
      ["M2 22 L3 22", false, false],
    ])
  })

  it("does not carve a mark through another path of the mark", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="mark" data-rest="hidden"/>\n  <path d="M12 12 L12.01 12" data-role="mark"/>`), {})
    expect(g.paths[0]).not.toHaveProperty("fill")
    expect(renderForm(g, { fill: { kind: "regular" }, marked: true }).map((p) => [p.d, p.fill])).toEqual([
      [square, true],
      ["M12 12 L12.01 12", true],
    ])
  })

  it("carves a hidden mark too", () => {
    const g = buildGlyph(row, source(`  <path d="${square}" data-role="mark" data-rest="hidden"/>\n  <path d="M8 12 L16 12"/>`), {})
    expect(g.paths[0]?.cuts).toEqual([1])
    expect(renderForm(g, { fill: { kind: "regular" }, marked: true }).map((p) => p.d)).toEqual([g.paths[0]?.fill])
  })
})

describe("loadGlyphs", () => {
  it("attaches override files to their base and skips exploring rows", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-glyphs-"))
    mkdirSync(join(root, "src"))
    const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'
    const files: Record<string, string> = { "ring.svg": source(body), "ring.solid.svg": source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'), "wip.svg": source(body) }
    for (const [name, text] of Object.entries(files)) writeFileSync(join(root, "src", name), text)
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [row, { ...row, name: "wip", status: "exploring" }] }))
    const glyphs = loadGlyphs(root)
    expect(glyphs.map((g) => g.name)).toEqual(["ring"])
    expect(Object.keys(glyphs[0]?.overrides ?? {})).toEqual(["solid"])
  })

  it("attaches a dotted drawing as the dotted form", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-glyphs-"))
    mkdirSync(join(root, "src"))
    writeFileSync(join(root, "src", "ring.svg"), source('  <path d="M4 4 L20 4 L20 20 L4 20 Z"/>'))
    writeFileSync(join(root, "src", "ring.dotted.svg"), source('  <path d="M4 4 L5 4 M7 4 L8 4"/>'))
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [row] }))
    const [ring] = loadGlyphs(root)
    expect(ring?.dotted).toEqual([{ d: "M4 4 L5 4 M7 4 L8 4", fill: false, evenOdd: false, stroke: true }])
    expect(ring ? formsOf(ring) : []).toEqual(["regular", "dotted"])
    expect(ring ? renderForm(ring, { fill: { kind: "dotted" } }) : []).toEqual(ring?.dotted)
    expect(Object.keys(ring?.overrides ?? {})).toEqual([])
  })
})

describe("formsContract", () => {
  const states = { rest: ["regular"], selected: ["solid", "progress-n", "marked", "regular"], progress: ["progress-k", "regular"] }
  const home = buildGlyph(
    { ...row, name: "home" },
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
  <path d="M4 20 L12 4 L20 20" data-role="body"/>
  <path d="M11 17 L13 17" data-role="mark"/>
</svg>`,
    {},
  )

  it("lists each glyph's forms, renders every one and resolves every state", () => {
    const contract = formsContract([home], states, ["home"])
    const entry = contract.glyphs.home
    expect(entry?.forms).toEqual(["regular", "marked", "solid", "solid-marked"])
    expect(Object.keys(entry?.rendered ?? {})).toEqual(entry?.forms)
    expect(entry?.rendered.solid?.[0]).toMatchObject({ fill: true, evenOdd: true, stroke: false })
    expect(entry?.states).toEqual({ rest: "regular", selected: "solid", "progress@0": "regular", "progress@1": "regular" })
  })

  it("names a missing glyph", () => {
    expect(() => formsContract([home], states, ["nope"])).toThrow(/nope/)
  })

  it("samples glyphs that exist in the set", () => {
    expect(CONTRACT_GLYPHS.length).toBeGreaterThanOrEqual(10)
  })
})
