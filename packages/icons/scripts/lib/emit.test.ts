import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { loadIcons } from "./emit"

const SVG = (paths: string) =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">\n${paths}\n</svg>\n`

const row = (name: string, variants: string[], status = "candidate") => ({
  name,
  meaning: `${name}.`,
  variants,
  wave: "2026-10-foundation",
  status,
  migrated: status === "shipped" ? ["ios", "android", "web"] : [],
  replaces: { ios: [], android: [], web: [] },
})

describe("loadIcons", () => {
  it("parses every emitted row's source and skips exploring rows", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-emit-"))
    mkdirSync(join(root, "src"))
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [row("home", ["active"]), row("wip", [], "exploring")] }))
    writeFileSync(join(root, "src", "home.svg"), SVG('  <path d="M2 2 L22 22"/>'))
    writeFileSync(join(root, "src", "home.active.svg"), SVG('  <path d="M2 2 L22 22"/>\n  <path d="M4 4 L8 8 Z" fill="currentColor" fill-rule="evenodd" stroke="none"/>'))
    writeFileSync(join(root, "src", "wip.svg"), SVG('  <path d="M2 2 L22 22"/>'))
    const icons = loadIcons(root)
    expect(icons.map((i) => i.name)).toEqual(["home", "home.active"])
    expect(icons[0]).toMatchObject({ base: "home", variant: null, meaning: "home." })
    expect(icons[1]?.paths).toHaveLength(2)
    expect(icons[1]?.paths[1]).toMatchObject({ d: "M4 4 L8 8 Z", filled: true, evenOdd: true, unstroked: true })
  })

  it("names the missing source", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-emit-"))
    mkdirSync(join(root, "src"))
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [row("home", [])] }))
    expect(() => loadIcons(root)).toThrow("home.svg")
  })
})
