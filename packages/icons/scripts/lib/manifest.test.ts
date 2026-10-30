import { mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { emitted, loadManifest, parseManifest, type Manifest } from "./manifest"

const row = (name: string, extra: Partial<Manifest["icons"][number]> = {}) => ({
  name,
  meaning: `${name}.`,
  variants: [],
  wave: "2026-10-foundation",
  status: "candidate",
  replaces: { ios: [], android: [], web: [] },
  ...extra,
})

describe("manifest", () => {
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
        row("path", { variants: ["active", "lit-1", "lit-2"], status: "shipped" }),
      ],
    })
    expect(emitted(manifest).map((e) => e.name)).toEqual(["home", "home.active", "path", "path.active", "path.lit-1", "path.lit-2"])
    expect(emitted(manifest)[1]).toMatchObject({ base: "home", variant: "active", meaning: "home." })
  })
})
