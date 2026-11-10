import { mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { emitted, loadManifest, parseManifest, SURFACES, type Manifest } from "./manifest"

const row = (name: string, extra: Partial<Manifest["icons"][number]> = {}) => ({
  name,
  meaning: `${name}.`,
  variants: [],
  wave: "2026-10-foundation",
  status: "candidate",
  migrated: [],
  replaces: { ios: [], android: [], web: [] },
  ...extra,
})

describe("manifest", () => {
  it("reads migrated and refuses an unknown or repeated surface", () => {
    const ok = parseManifest({ icons: [row("home", { migrated: ["ios"] })] })
    expect(ok.icons[0]?.migrated).toEqual(["ios"])
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: ["mac"] }] })).toThrow("icons[0] (home).migrated: mac")
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: ["ios", "ios"] }] })).toThrow("icons[0] (home).migrated: ios twice")
    expect(() => parseManifest({ icons: [{ ...row("home"), migrated: "ios" }] })).toThrow("icons[0] (home).migrated is not a string array")
  })

  it("refuses shipped while a surface is not migrated", () => {
    const all = { status: "shipped", migrated: ["ios", "android", "web"] } satisfies Partial<Manifest["icons"][number]>
    expect(parseManifest({ icons: [row("home", all)] }).icons).toHaveLength(1)
    expect(() => parseManifest({ icons: [row("home", { status: "shipped", migrated: ["ios"] })] }))
      .toThrow("icons[0] (home): shipped, but migrated lacks android, web")
  })

  it("loads a well-formed file", () => {
    const dir = mkdtempSync(join(tmpdir(), "icons-manifest-"))
    writeFileSync(join(dir, "icons.json"), JSON.stringify({ icons: [row("home", { variants: ["active"] })] }))
    const manifest = loadManifest(dir)
    expect(manifest.icons).toHaveLength(1)
    expect(manifest.icons[0]?.variants).toEqual(["active"])
  })

  it("names the row of a shape error", () => {
    const badReplaces: unknown = { icons: [{ ...row("home"), replaces: { ios: [], android: [], web: 3 } }] }
    expect(() => parseManifest(badReplaces)).toThrow("icons[0] (home).replaces.web")
    expect(() => parseManifest({ icons: [{ meaning: "x" }] })).toThrow("icons[0].name")
    expect(() => parseManifest({ nope: [] })).toThrow('expected { "icons": [...] }')
  })

  it("emits every non-exploring row and its twins in manifest order", () => {
    const manifest = parseManifest({
      icons: [
        row("home", { variants: ["active"] }),
        row("wip", { status: "exploring", variants: ["active"] }),
        row("path", { variants: ["active", "lit-1", "lit-2"], status: "shipped", migrated: ["ios", "android", "web"] }),
      ],
    })
    expect(emitted(manifest).map((e) => e.name)).toEqual(["home", "home.active", "path", "path.active", "path.lit-1", "path.lit-2"])
    expect(emitted(manifest)[1]).toMatchObject({ base: "home", variant: "active", meaning: "home." })
  })
})

describe("icons.json replaces", () => {
  const manifest = loadManifest(fileURLToPath(new URL("../..", import.meta.url)))

  it("lists bare symbol names, each on one row", () => {
    const owner = new Map<string, string>()
    for (const row of manifest.icons) {
      for (const surface of SURFACES) {
        for (const name of row.replaces[surface]) {
          expect(name, `${row.name}.replaces.${surface}`).toMatch(/^[A-Za-z0-9._]+$/)
          const key = `${surface}:${name}`
          expect(owner.get(key), `${name} listed by ${owner.get(key)} and ${row.name}`).toBeUndefined()
          owner.set(key, row.name)
        }
      }
    }
  })

  it("reads an optional family and tags", () => {
    const row = {
      name: "knot-sm", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [],
      replaces: { ios: [], android: [], web: [] },
      family: "knot",
      tags: { subject: ["knot"], size: ["sm"], keywords: ["done", "tied"] },
    }
    const parsed = parseManifest({ icons: [row] }).icons[0]
    expect(parsed?.family).toBe("knot")
    expect(parsed?.tags).toEqual({ subject: ["knot"], size: ["sm"], keywords: ["done", "tied"] })
  })

  it("defaults family to null and tags to empty", () => {
    const row = { name: "a", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
    const parsed = parseManifest({ icons: [row] }).icons[0]
    expect(parsed?.family).toBeNull()
    expect(parsed?.tags).toEqual({})
  })

  it("reads a category tag", () => {
    const row = { name: "a", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] }, tags: { category: ["sky", "water"] } }
    expect(parseManifest({ icons: [row] }).icons[0]?.tags).toEqual({ category: ["sky", "water"] })
  })

  it("refuses an unknown tag key or a non-array tag", () => {
    const base = { name: "a", meaning: "m", variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } }
    expect(() => parseManifest({ icons: [{ ...base, tags: { colour: ["red"] } }] })).toThrow(/tags\.colour is not a tag key/)
    expect(() => parseManifest({ icons: [{ ...base, tags: { size: "sm" } }] })).toThrow(/tags\.size is not a string array/)
    expect(() => parseManifest({ icons: [{ ...base, family: 3 }] })).toThrow(/family is not a string/)
  })
})
