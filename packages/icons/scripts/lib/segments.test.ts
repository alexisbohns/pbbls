import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import type { GlyphData } from "./forms"
import { segmentsOf } from "./segments"

const GLYPHS = fileURLToPath(new URL("../../glyphs/glyphs.json", import.meta.url))
const FIXTURE = fileURLToPath(new URL("../../fixtures/path-contract.json", import.meta.url))

type Case = { d: string; segments: { op: string; pts: number[] }[] }

function cases(): Case[] {
  const json: unknown = JSON.parse(readFileSync(FIXTURE, "utf8"))
  if (typeof json !== "object" || json === null || !("cases" in json) || !Array.isArray(json.cases)) throw new Error("path-contract.json: expected { cases: [...] }")
  return json.cases.filter((c: unknown): c is Case => typeof c === "object" && c !== null && "d" in c && "segments" in c)
}

describe("segmentsOf (the path-parser contract)", () => {
  it("reads every fixture case as the fixture says", () => {
    const all = cases()
    expect(all.length).toBeGreaterThan(5)
    for (const c of all) expect(segmentsOf(c.d), c.d).toEqual(c.segments)
  })

  it("refuses a command the normaliser never writes", () => {
    expect(() => segmentsOf("M0 0 a1 1 0 0 1 2 2")).toThrow(/not normalised/)
  })

  it("parses every d of the emitted glyphs, each starting with an M", () => {
    const json: unknown = JSON.parse(readFileSync(GLYPHS, "utf8"))
    if (typeof json !== "object" || json === null || !("glyphs" in json) || !Array.isArray(json.glyphs)) throw new Error("glyphs.json: expected { glyphs: [...] }")
    const glyphs: GlyphData[] = json.glyphs
    expect(glyphs.length).toBeGreaterThan(100)
    const strings = (g: GlyphData): string[] => [
      ...g.paths.flatMap((p) => (p.fill === undefined ? [p.d] : [p.d, p.fill])),
      ...Object.values(g.carved).map((c) => c.d),
      ...(g.dotted ?? []).map((p) => p.d),
      ...Object.values(g.overrides).flatMap((ps) => ps.map((p) => p.d)),
    ]
    for (const g of glyphs) {
      for (const d of strings(g)) {
        expect(segmentsOf(d)[0]?.op, `${g.name}: ${d.slice(0, 40)}`).toBe("M")
      }
    }
  })
})
