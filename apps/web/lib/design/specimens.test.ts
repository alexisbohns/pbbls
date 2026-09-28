import { existsSync, readdirSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { SPECIMENS, tuningProgress, type SpecimenEntry } from "./specimens"

const webRoot = fileURLToPath(new URL("../..", import.meta.url))

describe("SPECIMENS", () => {
  it("has unique ids", () => {
    const ids = SPECIMENS.map((s) => s.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it("points every entry at a file that exists", () => {
    for (const s of SPECIMENS) expect(existsSync(path.join(webRoot, s.source)), s.source).toBe(true)
  })

  // A new shadcn/custom primitive must show up on the design page.
  it("covers every file in components/ui", () => {
    const sources = SPECIMENS.filter((s) => s.section === "primitives").map((s) => s.source)
    for (const file of readdirSync(path.join(webRoot, "components/ui")).filter((f) => f.endsWith(".tsx"))) {
      expect(sources, `components/ui/${file} has no specimen`).toContain(`components/ui/${file}`)
    }
  })
})

describe("tuningProgress", () => {
  it("counts tuned entries", () => {
    const entries: SpecimenEntry[] = [
      { id: "a", name: "A", source: "a.tsx", section: "primitives", m3: "tuned" },
      { id: "b", name: "B", source: "b.tsx", section: "primitives", m3: "bridged" },
    ]
    expect(tuningProgress(entries)).toEqual({ tuned: 1, total: 2 })
  })
})
