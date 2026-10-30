import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { CANONICAL_ROOT } from "./format"
import { filledArea, lint, type LintResult } from "./lint"

const WAVE = "2026-10-test"

const row = (name: string, variants: string[] = [], extra: Record<string, unknown> = {}) => ({
  name,
  meaning: `${name}.`,
  variants,
  wave: WAVE,
  status: "candidate",
  migrated: [],
  replaces: { ios: [], android: [], web: [] },
  ...extra,
})

const svg = (...paths: string[]) => `${CANONICAL_ROOT}\n${paths.map((p) => `  ${p}`).join("\n")}\n</svg>\n`

const STROKE = '<path d="M4.5 13 C6.1 14.3 7.7 15.9 9.5 18.1 C11.9 13.5 14.6 9.8 17.9 6.8"/>'
const STONE = '<path d="M11.9 18.1 C12.3 18 12.6 18.3 12.6 18.6 C12.6 19 12.2 19.2 11.9 19.2 C11.6 19.1 11.4 18.9 11.4 18.6 C11.4 18.2 11.7 18.1 12.2 18.1 Z" fill="currentColor" stroke="none"/>'
const CARVED = '<path d="M4 4 L20 4 L20 20 L4 20 Z M10 10 L14 10 L14 14 L10 14 Z" fill="currentColor" fill-rule="evenodd" stroke="none"/>'

const BASE = svg(STROKE)
const TWIN = svg(STROKE, STONE)

type Fixture = { manifest?: unknown; files?: Record<string, string>; wave?: boolean }

function run({ manifest, files, wave = true }: Fixture): LintResult {
  const root = mkdtempSync(join(tmpdir(), "icons-lint-"))
  mkdirSync(join(root, "src"))
  if (wave) mkdirSync(join(root, "waves", WAVE), { recursive: true })
  const json = manifest ?? { icons: [row("home", ["active"]), row("trash")] }
  writeFileSync(join(root, "icons.json"), JSON.stringify(json))
  const sources = files ?? { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE }
  for (const [name, text] of Object.entries(sources)) writeFileSync(join(root, "src", name), text)
  return lint(root)
}

const rules = (r: LintResult) => r.errors.map((e) => e.rule)

describe("lint", () => {
  it("passes a base and its twin", () => {
    const r = run({})
    expect(r.errors).toEqual([])
    expect(r.warnings).toEqual([])
  })

  it("passes a carved stone and warns above 64 square units", () => {
    const r = run({ files: { "home.svg": BASE, "home.active.svg": svg(STROKE, CARVED), "trash.svg": BASE } })
    expect(r.errors).toEqual([])
    expect(r.warnings).toHaveLength(1)
    expect(r.warnings[0]).toMatchObject({ file: "home.active.svg", rule: "fill" })
    expect(r.warnings[0]?.message).toContain("240.0")
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
      ["a fill-rule other than evenodd", { "home.active.svg": svg(STROKE, STONE.replace('fill="currentColor"', 'fill="currentColor" fill-rule="nonzero"')) }, "fill", "nonzero"],
      ["a fill other than currentColor", { "home.active.svg": svg(STROKE, STONE.replace("currentColor", "#000")) }, "fill", "#000"],
      ["four filled paths in a twin", { "home.active.svg": svg(STROKE, STONE, STONE, STONE, STONE) }, "fill", "4 filled paths"],
    ]
    it.each(cases)("%s", (_what, files, rule, fragment) => {
      const all = { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, ...files }
      const r = run({ files: all })
      const hit = r.errors.filter((e) => e.rule === rule)
      expect(hit.length, JSON.stringify(r.errors)).toBeGreaterThan(0)
      expect(hit.map((e) => e.file)).toContain(Object.keys(files)[0])
      expect(hit.map((e) => e.message).join("\n")).toContain(fragment)
    })
  })

  describe("manifest", () => {
    it("a row without its file", () => {
      const r = run({ manifest: { icons: [row("home", ["active"]), row("trash"), row("ghost")] } })
      expect(r.errors).toEqual([{ file: "icons.json", rule: "manifest", message: "ghost: no src/ghost.svg" }])
    })
    it("a file without a row", () => {
      const r = run({ files: { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, "stray.svg": BASE } })
      expect(r.errors).toEqual([{ file: "stray.svg", rule: "manifest", message: 'no row "stray" in icons.json' }])
    })
    it("a listed variant without its file", () => {
      const r = run({ manifest: { icons: [row("home", ["active", "lit-1"]), row("trash")] } })
      expect(r.errors.map((e) => e.message)).toEqual(["home: no src/home.lit-1.svg for variant \"lit-1\""])
    })
    it("a twin file not listed in variants", () => {
      const r = run({ files: { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, "trash.active.svg": TWIN } })
      expect(r.errors).toEqual([{ file: "trash.active.svg", rule: "manifest", message: 'not listed in the variants of "trash"' }])
    })
    it("a variant outside the four", () => {
      const r = run({ manifest: { icons: [row("home", ["active", "glow"]), row("trash")] }, files: { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, "home.glow.svg": TWIN } })
      expect(rules(r)).toEqual(["manifest", "manifest"])
      expect(r.errors.map((e) => e.message).join("\n")).toContain('variant "glow" is not one of active, lit-1, lit-2, lit-3')
    })
    it("a twin without its base", () => {
      const r = run({ files: { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, "path.active.svg": TWIN } })
      expect(r.errors.map((e) => e.message)).toEqual(['twin without its base: no row "path"'])
    })
    it("a duplicate and a non-kebab name", () => {
      const r = run({ manifest: { icons: [row("home", ["active"]), row("trash"), row("trash"), row("Bad_Name")] }, files: { "home.svg": BASE, "home.active.svg": TWIN, "trash.svg": BASE, "Bad_Name.svg": BASE } })
      expect(r.errors.map((e) => e.message)).toEqual(['"trash" is listed twice', '"Bad_Name" is not a kebab-case name'])
    })
    it("a missing wave folder", () => {
      const r = run({ wave: false })
      expect(r.errors.map((e) => e.message)).toEqual([`home: no folder waves/${WAVE}/`, `trash: no folder waves/${WAVE}/`])
    })
    it("an unknown status", () => {
      const r = run({ manifest: { icons: [row("home", ["active"]), row("trash", [], { status: "done" })] } })
      expect(r.errors.map((e) => e.message)).toEqual(['trash: unknown status "done"'])
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
