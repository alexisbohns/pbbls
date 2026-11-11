import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { CANONICAL_ROOT } from "./format"
import { filledArea, lint, type LintResult } from "./lint"

const WAVE = "2026-10-test"

const row = (name: string, extra: Record<string, unknown> = {}) => ({
  name,
  meaning: `${name}.`,
  tags: { category: ["sky"] },
  wave: WAVE,
  status: "candidate",
  migrated: [],
  replaces: { ios: [], android: [], web: [] },
  ...extra,
})

const svg = (...paths: string[]) => `${CANONICAL_ROOT}\n${paths.map((p) => `  ${p}`).join("\n")}\n</svg>\n`

const STROKE = '<path d="M4.5 13 C6.1 14.3 7.7 15.9 9.5 18.1 C11.9 13.5 14.6 9.8 17.9 6.8"/>'
const STONE = '<path d="M11.9 18.1 C12.3 18 12.6 18.3 12.6 18.6 C12.6 19 12.2 19.2 11.9 19.2 C11.6 19.1 11.4 18.9 11.4 18.6 C11.4 18.2 11.7 18.1 12.2 18.1 Z" fill="currentColor" stroke="none"/>'

const BASE = svg(STROKE)

const CATEGORIES = { categories: [{ id: "sky", label: "Sky", description: "The sky." }, { id: "water", label: "Water", description: "Water." }] }

type Fixture = { manifest?: unknown; files?: Record<string, string>; wave?: boolean; categories?: unknown }

function run({ manifest, files, wave = true, categories = CATEGORIES }: Fixture): LintResult {
  const root = mkdtempSync(join(tmpdir(), "icons-lint-"))
  mkdirSync(join(root, "src"))
  if (wave) mkdirSync(join(root, "waves", WAVE), { recursive: true })
  const json = manifest ?? { icons: [row("home"), row("trash")] }
  writeFileSync(join(root, "icons.json"), JSON.stringify(json))
  if (categories !== null) writeFileSync(join(root, "categories.json"), typeof categories === "string" ? categories : JSON.stringify(categories))
  const sources = files ?? { "home.svg": BASE, "trash.svg": BASE }
  for (const [name, text] of Object.entries(sources)) writeFileSync(join(root, "src", name), text)
  return lint(root)
}

const rules = (r: LintResult) => r.errors.map((e) => e.rule)

describe("lint", () => {
  it("passes two bases", () => {
    const r = run({})
    expect(r.errors).toEqual([])
    expect(r.warnings).toEqual([])
  })

  describe("rejects", () => {
    const cases: [string, Record<string, string>, string, string][] = [
      ["a wrong viewBox", { "trash.svg": BASE.replace('viewBox="0 0 24 24"', 'viewBox="0 0 48 48"') }, "root", 'viewBox="0 0 48 48"'],
      ["an extra root attribute", { "trash.svg": BASE.replace("<svg ", '<svg width="24" ') }, "root", "width"],
      ["a circle", { "trash.svg": svg(STROKE, '<circle cx="12" cy="12" r="3"/>') }, "elements", "<circle>"],
      ["a transform", { "trash.svg": svg('<path d="M4 4 L5 5" transform="rotate(3)"/>') }, "elements", "transform"],
      ["a relative command", { "trash.svg": svg('<path d="M4 4 c1 1 2 2 3 3"/>') }, "commands", "uses c"],
      ["a coordinate below the range", { "trash.svg": svg('<path d="M0.9 4 L5 5"/>') }, "range", "0.9"],
      ["a coordinate above the range", { "trash.svg": svg('<path d="M4 4 L23.1 5"/>') }, "range", "23.1"],
      ["an arc ending out of range (its radius is not checked)", { "trash.svg": svg('<path d="M4 4 A30 30 0 0 1 23 5"/>') }, "range", "23 (in A)"],
      ["a fill in a base file", { "trash.svg": svg(STROKE, STONE) }, "fill", "base file"],
      ["a fill-rule on an unfilled path", { "trash.svg": svg('<path d="M4 4 L5 5" fill-rule="evenodd"/>') }, "fill", "unfilled"],
      ["a stroke=none on an unfilled path", { "trash.svg": svg('<path d="M4 4 L5 5" stroke="none"/>') }, "fill", "unfilled"],
      ["a stroke-dasharray", { "trash.svg": svg('<path d="M4 4 L5 5" stroke-dasharray="2 3"/>') }, "dash", "stroke-dasharray"],
      ["a non-canonical file", { "trash.svg": BASE.replace("</svg>\n", "</svg>") }, "canonical", "canonical"],
    ]
    it.each(cases)("%s", (_what, files, rule, fragment) => {
      const all = { "home.svg": BASE, "trash.svg": BASE, ...files }
      const r = run({ files: all })
      const hit = r.errors.filter((e) => e.rule === rule)
      expect(hit.length, JSON.stringify(r.errors)).toBeGreaterThan(0)
      expect(hit.map((e) => e.file)).toContain(Object.keys(files)[0])
      expect(hit.map((e) => e.message).join("\n")).toContain(fragment)
    })
  })

  describe("manifest", () => {
    it("a row without its file", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash"), row("ghost")] } })
      expect(r.errors).toEqual([{ file: "icons.json", rule: "manifest", message: "ghost: no src/ghost.svg" }])
    })
    it("a file without a row", () => {
      const r = run({ files: { "home.svg": BASE, "trash.svg": BASE, "stray.svg": BASE } })
      expect(r.errors).toEqual([{ file: "stray.svg", rule: "manifest", message: 'no row "stray" in icons.json' }])
    })
    it("refuses a source that is neither the base, its dotted drawing nor an override", () => {
      const r = run({ manifest: { icons: [row("home")] }, files: { "home.svg": BASE, "home.active.svg": BASE } })
      expect(r.errors).toContainEqual(
        expect.objectContaining({ file: "home.active.svg", rule: "manifest", message: '"active" is not a form: a source is <name>.svg, <name>.dotted.svg or <name>.<form>.svg' }),
      )
    })
    it("a duplicate and a non-kebab name", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash"), row("trash"), row("Bad_Name")] }, files: { "home.svg": BASE, "trash.svg": BASE, "Bad_Name.svg": BASE } })
      expect(r.errors.map((e) => e.message)).toEqual(['"trash" is listed twice', '"Bad_Name" is not a kebab-case name'])
    })
    it("a missing wave folder", () => {
      const r = run({ wave: false })
      expect(r.errors.map((e) => e.message)).toEqual([`home: no folder waves/${WAVE}/`, `trash: no folder waves/${WAVE}/`])
    })
    it("an unknown status", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash", { status: "done" })] } })
      expect(r.errors.map((e) => e.message)).toEqual(['trash: unknown status "done"'])
    })
    it("a row without a category", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash", { tags: {} })] } })
      expect(r.errors).toEqual([{ file: "icons.json", rule: "category", message: "trash: no tags.category (1 to 3 ids from categories.json)" }])
    })
    it("an unknown category", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash", { tags: { category: ["sky", "weather"] } })] } })
      expect(r.errors.map((e) => e.message)).toEqual(['trash: unknown category "weather"'])
    })
    it("more than three, or repeated, categories", () => {
      const four = { tags: { category: ["sky", "water", "sky", "water"] } }
      const r = run({ manifest: { icons: [row("home"), row("trash", four)] } })
      expect(r.errors.map((e) => e.message)).toEqual(["trash: 4 categories, at most 3", 'trash: category "sky" twice', 'trash: category "water" twice'])
    })
    it("an exploring row needs no category", () => {
      const r = run({ manifest: { icons: [row("home"), row("trash", { status: "exploring", tags: {} })] } })
      expect(r.errors).toEqual([])
    })
    it("a missing or malformed categories.json", () => {
      expect(run({ categories: null }).errors.map((e) => e.message)).toEqual([expect.stringContaining("categories.json")])
      expect(run({ categories: "{" }).errors[0]?.rule).toBe("category")
      expect(run({ categories: { categories: [{ id: "sky", label: "Sky" }] } }).errors[0]?.message).toContain("description")
      const dup = { categories: [{ id: "sky", label: "A", description: "a" }, { id: "sky", label: "B", description: "b" }] }
      expect(run({ categories: dup }).errors[0]?.message).toContain('"sky" is listed twice')
    })
    it("a malformed manifest, once", () => {
      const r = run({ manifest: { icons: [{ name: 3 }] } })
      expect(r.errors.map((e) => e.rule)).toEqual(["manifest"])
      expect(r.errors[0]?.message).toContain("icons[0].name")
    })
  })

  it("measures a filled area, even-odd subtracting the knockout", () => {
    expect(filledArea("M4 4 L20 4 L20 20 L4 20 Z", false)).toBeCloseTo(256)
    expect(filledArea("M4 4 L20 4 L20 20 L4 20 Z M10 10 L14 10 L14 14 L10 14 Z", true)).toBeCloseTo(240)
    expect(filledArea("M4 4 L20 4 L20 20 L4 20 Z M10 10 L14 10 L14 14 L10 14 Z", false)).toBeCloseTo(272)
  })
})

describe("roles and overrides", () => {
  const BODY = '<path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body"/>'

  it("accepts role attributes", () => {
    const files = { "ring.svg": svg(BODY, '<path d="M8 12 L16 12" data-role="step" data-step="1"/>', '<path d="M8 15 L16 15" data-role="step" data-step="2"/>') }
    expect(run({ manifest: { icons: [row("ring")] }, files }).errors).toEqual([])
  })

  it("accepts a hidden mark and reports a misplaced data-rest", () => {
    const hidden = '<path d="M10 10 L11 11" data-role="mark" data-rest="hidden"/>'
    expect(run({ manifest: { icons: [row("ring")] }, files: { "ring.svg": svg(BODY, hidden) } }).errors).toEqual([])
    const bad = run({ manifest: { icons: [row("ring")] }, files: { "ring.svg": svg('<path d="M4 4 L20 4 L20 20 L4 20 Z" data-role="body" data-rest="hidden"/>') } }).errors.map((e) => e.message)
    expect(bad).toContain("path 1: data-rest is only allowed on a mark or step")
  })

  it("reports role mistakes and an open body", () => {
    const files = { "ring.svg": svg('<path d="M4 4 L20 4" data-role="body"/>', '<path d="M8 12 L16 12" data-role="step" data-step="2"/>') }
    const messages = run({ manifest: { icons: [row("ring")] }, files }).errors.map((e) => e.message)
    expect(messages).toContain("steps must run 1…n without gaps; found 2")
    expect(messages).toContain("the body encloses no area")
  })

  it("accepts an override for a form the glyph has and refuses one it lacks", () => {
    const over = svg('<path d="M4 4 L20 4 L20 20 L4 20 Z" fill="currentColor"/>')
    const manifest = { icons: [row("ring")] }
    expect(run({ manifest, files: { "ring.svg": svg(BODY), "ring.solid.svg": over } }).errors).toEqual([])
    const messages = run({ manifest, files: { "ring.svg": svg(BODY), "ring.marked.svg": over } }).errors.map((e) => e.message)
    expect(messages).toContain('override "marked" for a form ring does not have')
  })

  it("accepts an override for a part combination and a marked solid", () => {
    const parts = svg('<path d="M3 4 L11 4 L11 20 L3 20 Z" data-role="body" data-part="left"/>', '<path d="M13 4 L21 4 L21 20 L13 20 Z" data-role="body" data-part="right"/>', '<path d="M12 2 L12 3" data-role="mark"/>')
    const over = svg('<path d="M3 4 L11 4 L11 20 L3 20 Z" fill="currentColor"/>')
    const files = { "ring.svg": parts, "ring.parts-left.svg": over, "ring.solid-marked.svg": over }
    expect(run({ manifest: { icons: [row("ring")] }, files }).errors).toEqual([])
    const messages = run({ manifest: { icons: [row("ring")] }, files: { "ring.svg": parts, "ring.parts-middle.svg": over } }).errors.map((e) => e.message)
    expect(messages).toContain('override "parts-middle" for a form ring does not have')
  })

  it("reports a body with a relative command or H/V instead of crashing", () => {
    for (const d of ["M4 4 h16 v16 h-16 Z", "M4 4 H20 V20 L4 20 Z"]) {
      const files = { "ring.svg": svg(`<path d="${d}" data-role="body"/>`) }
      const result = run({ manifest: { icons: [row("ring")] }, files })
      expect(result.errors.some((e) => e.rule === "commands")).toBe(true)
    }
  })

  it("does not limit what an override fills", () => {
    const marks = [0, 1, 2, 3, 4].map((i) => `<path d="M${4 + i * 3} 12 L${5 + i * 3} 12" data-role="step" data-step="${i + 1}"/>`)
    const base = svg(BODY, ...marks)
    const filled = [0, 1, 2, 3, 4].map((i) => `<path d="M${4 + i * 3} 12 L${5 + i * 3} 12 L${5 + i * 3} 13 Z" fill="currentColor" stroke="none"/>`)
    const big = svg('<path d="M2 2 L22 2 L22 22 L2 22 Z" fill="currentColor" stroke="none"/>')
    const result = run({ manifest: { icons: [row("ring")] }, files: { "ring.svg": base, "ring.progress-5.svg": svg(...filled), "ring.solid.svg": big } })
    expect(result.errors).toEqual([])
    expect(result.warnings).toEqual([])
  })

  it("refuses a fill-rule other than evenodd and a fill other than currentColor, in an override too", () => {
    const manifest = { icons: [row("ring")] }
    const solid = (attrs: string) => svg(`<path d="M4 4 L20 4 L20 20 L4 20 Z" ${attrs}/>`)
    const rule = run({ manifest, files: { "ring.svg": svg(BODY), "ring.solid.svg": solid('fill="currentColor" fill-rule="nonzero"') } }).errors.map((e) => e.message)
    expect(rule).toContain('path 1 has fill-rule="nonzero", only evenodd is allowed')
    const colour = run({ manifest, files: { "ring.svg": svg(BODY), "ring.solid.svg": solid('fill="#000"') } }).errors.map((e) => e.message)
    expect(colour).toContain('path 1 has fill="#000", only currentColor is allowed')
  })

  it("refuses a name ending in a form word", () => {
    const messages = run({ manifest: { icons: [row("scale-marked")] }, files: { "scale-marked.svg": svg('<path d="M4 4 L20 20"/>') } }).errors.map((e) => e.message)
    expect(messages).toContain('"scale-marked" ends in a form word (regular, marked, solid, parts, of)')
  })
  it("accepts a dotted drawing beside its base and refuses one with a role, a fill, or no base", () => {
    const dotted = svg('<path d="M4 12 L5 12 M7 12 L8 12"/>')
    const manifest = { icons: [row("ring")] }
    expect(run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": dotted } }).errors).toEqual([])
    const roled = run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": svg('<path d="M4 12 L5 12" data-role="mark"/>') } }).errors.map((e) => e.message)
    expect(roled).toContain("path 1: a dotted drawing carries no role")
    const filled = run({ manifest, files: { "ring.svg": svg(BODY), "ring.dotted.svg": svg('<path d="M4 4 L8 4 L8 8 Z" fill="currentColor"/>') } }).errors.map((e) => e.message)
    expect(filled).toContain("path 1: a dotted drawing is never filled")
    const orphan = run({ manifest, files: { "ring.svg": svg(BODY), "knot.dotted.svg": dotted } }).errors.map((e) => e.message)
    expect(orphan).toContain('dotted drawing without its base: no row "knot"')
  })
})
