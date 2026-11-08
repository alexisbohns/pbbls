import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { buildGlyph, loadGlyphs } from "./emit-glyphs"
import { formsOf, renderForm } from "./forms"
import type { IconRow } from "./manifest"

const row: IconRow = { name: "ring", meaning: "A ring.", family: "ring", tags: { wrapper: ["circle"] }, variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
const source = (paths: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">\n${paths}\n</svg>\n`

describe("buildGlyph", () => {
  it("stores normalised paths with roles, the mark count and the carved solid", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12" data-role="mark" data-mark="1"/>\n  <path d="M2 22 A1 1 0 0 1 4 22"/>'), {})
    expect(g.paths[0]).toEqual({ d: "M4 4 L20 4 L20 20 L4 20 Z", role: "body" })
    expect(g.paths[1]).toEqual({ d: "M8 12 L16 12", role: "mark", mark: 1 })
    expect(g.paths[2]?.d).toMatch(/^M2 22 C/)
    expect(g.marks).toBe(1)
    expect(g.solid?.keep).toEqual([1])
    expect(formsOf(g)).toEqual(["regular", "solid", "progress-1"])
    expect(g.family).toBe("ring")
    expect(g.tags).toEqual({ wrapper: ["circle"] })
  })

  it("stores overrides by form, as rendered paths", () => {
    const g = buildGlyph(row, source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'), {
      solid: source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'),
    })
    expect(renderForm(g, { kind: "solid" })).toEqual([{ d: "M4 4 L20 4 L20 20 L4 20 Z", fill: true, evenOdd: false, stroke: false }])
  })
})

describe("hidden paths", () => {
  const hidden = '  <path d="M10 10 L11 11" data-role="mark" data-mark="1" data-rest="hidden"/>'
  const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>\n  <path d="M8 12 L16 12"/>'

  it("writes hidden: true and leaves the path out of the carve", () => {
    const g = buildGlyph(row, source(`${body}\n${hidden}`), {})
    expect(g.paths[2]).toEqual({ d: "M10 10 L11 11", role: "mark", mark: 1, hidden: true })
    expect(g.paths[0]).not.toHaveProperty("hidden")
    // keep indexes the non-body, non-hidden paths: the line is 0, the dot is not counted
    expect(g.solid?.keep).toEqual([])
    const withoutDot = buildGlyph(row, source(body), {})
    expect(g.solid?.d).toBe(withoutDot.solid?.d)
  })

  it("keeps keep consistent with renderForm when a hidden path comes before a kept line", () => {
    const g = buildGlyph(row, source(`${body.split("\n")[0]}\n${hidden}\n  <path d="M2 22 L4 22"/>`), {})
    expect(g.solid?.keep).toEqual([0])
    expect(renderForm(g, { kind: "solid" }).map((p) => p.d).slice(1)).toEqual(["M2 22 L4 22"])
    expect(renderForm(g, { kind: "progress", k: 1 }).filter((p) => p.fill).map((p) => p.d)).toEqual(["M10 10 L11 11"])
    expect(renderForm(g, { kind: "regular" }).map((p) => p.d)).not.toContain("M10 10 L11 11")
  })
})

describe("loadGlyphs", () => {
  it("attaches override files to their base and skips exploring rows", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-glyphs-"))
    mkdirSync(join(root, "src"))
    const body = '  <path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'
    const files: Record<string, string> = { "ring.svg": source(body), "ring.solid.svg": source('  <path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor" stroke="none"/>'), "wip.svg": source(body) }
    for (const [name, text] of Object.entries(files)) writeFileSync(join(root, "src", name), text)
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [{ ...row, variants: [] }, { ...row, name: "wip", status: "exploring" }] }))
    const glyphs = loadGlyphs(root)
    expect(glyphs.map((g) => g.name)).toEqual(["ring"])
    expect(Object.keys(glyphs[0]?.overrides ?? {})).toEqual(["solid"])
  })
})
