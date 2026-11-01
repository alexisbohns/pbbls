import { readdirSync, readFileSync, statSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"
import { DETAILS } from "./app-routes"

// The `@detail` slot (#1384, two-area spec D2) read as source. Ariko's lesson:
// deleting a null slot page passes every other check and brings back a stale
// pane, so the null pages are pinned here; and each detail page must render
// the detail's module, never a copy of it.
const appDir = path.join(fileURLToPath(new URL("../..", import.meta.url)), "app")
const slot = path.join(appDir, "@detail")

function pages(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name)
    if (statSync(full).isDirectory()) return pages(full)
    return name === "page.tsx" ? [full] : []
  })
}

describe("the detail slot", () => {
  it("closes on every URL that is not a detail: a null default, a null root and a null catch-all", () => {
    for (const file of ["default.tsx", "page.tsx", "[...rest]/page.tsx"]) {
      const source = readFileSync(path.join(slot, file), "utf8")
      expect(source, file).toMatch(/return null/)
    }
  })

  it("draws the pane frame once, in the (pane) group's layout, around every detail", () => {
    expect(readFileSync(path.join(slot, "(pane)/layout.tsx"), "utf8")).toContain("<DetailPane>")
    const outside = pages(slot).filter((p) => !p.includes(`${path.sep}(pane)${path.sep}`))
    expect(outside.map((p) => path.relative(slot, p)).sort()).toEqual(["[...rest]/page.tsx", "page.tsx"])
  })

  it("has a page for each detail in the route table, and the page under it in the tab's group renders nothing", () => {
    const groups: Record<string, string> = { "/home": "(home)", "/path": "(path)", "/souls": "(people)", "/collections": "(collections)", "/profile": "(you)" }
    for (const { prefix, tab } of DETAILS) {
      const route = prefix.slice(1)
      const detailPages = pages(path.join(slot, "(pane)", route))
      expect(detailPages.length, prefix).toBeGreaterThan(0)
      for (const p of detailPages) expect(readFileSync(p, "utf8"), p).toMatch(/import \{ \w+ \} from "@\/components\//)
      const under = pages(path.join(appDir, groups[tab], route))
      expect(under.length, prefix).toBeGreaterThan(0)
      for (const p of under) expect(readFileSync(p, "utf8"), p).toMatch(/return null/)
    }
  })

  it("intercepts the pebble with the very module its plain page renders", () => {
    const intercept = readFileSync(path.join(slot, "(pane)", "(.)pebble", "[id]", "page.tsx"), "utf8")
    expect(intercept).toContain('export { default } from "../../pebble/[id]/page"')
  })

  it("has a route-table row for every detail in the slot", () => {
    const routes = readdirSync(path.join(slot, "(pane)")).filter((name) => !name.includes(".") && !name.startsWith("("))
    const prefixes = DETAILS.map((d) => d.prefix.slice(1))
    for (const route of routes) expect(prefixes, route).toContain(route)
  })
})
