import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import { describe, expect, it } from "vitest"
import { loadIcons } from "./emit"

const row = (name: string, status = "candidate") => ({
  name,
  meaning: `${name}.`,
  wave: "2026-10-foundation",
  status,
  migrated: status === "shipped" ? ["ios", "android", "web"] : [],
  replaces: { ios: [], android: [], web: [] },
})

describe("loadIcons", () => {
  it("lists every emitted row in manifest order and skips exploring rows", () => {
    const root = mkdtempSync(join(tmpdir(), "icons-emit-"))
    mkdirSync(join(root, "src"))
    writeFileSync(join(root, "icons.json"), JSON.stringify({ icons: [row("home"), row("wip", "exploring"), row("trash")] }))
    expect(loadIcons(root)).toEqual([
      { name: "home", meaning: "home." },
      { name: "trash", meaning: "trash." },
    ])
  })
})
