import { existsSync, readdirSync, statSync } from "node:fs"
import path from "node:path"
import { fileURLToPath } from "node:url"
import { describe, expect, it } from "vitest"

// Each tab's pages live in its route group (#1384, two-area spec D2), so the
// group can hold the tab's screen in the left area while a detail of that tab
// opens beside it. Immersive flows stay outside every group: a group layout
// draws its tab under every route below it.
const appDir = path.join(fileURLToPath(new URL("../..", import.meta.url)), "app")

const GROUPS: Record<string, readonly string[]> = {
  "(home)": ["home", "guides/[slug]"],
  "(path)": ["path", "wallet", "pebble/[id]"],
  "(people)": ["souls", "souls/[id]", "connections", "connections/[id]"],
  "(collections)": ["collections", "collections/[id]"],
  "(you)": ["profile", "settings", "glyphs", "glyphs/[id]", "achievements", "lab", "lab/changelog", "lab/backlog", "lab/announcements/[id]"],
}

/** Every directory under `dir` holding a page.tsx, as a route (groups and slots kept in the path). */
function pageDirs(dir: string, rel = ""): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    if (!statSync(full).isDirectory()) continue
    const sub = rel ? `${rel}/${name}` : name
    if (existsSync(path.join(full, "page.tsx"))) out.push(sub)
    out.push(...pageDirs(full, sub))
  }
  return out
}

describe("the tabs' route groups", () => {
  it("hold each tab root and each of its pages", () => {
    for (const [group, routes] of Object.entries(GROUPS)) {
      for (const route of routes) {
        expect(existsSync(path.join(appDir, group, route, "page.tsx")), `${group}/${route}`).toBe(true)
      }
    }
  })

  it("leave no copy of a grouped route outside its group", () => {
    const outside = pageDirs(appDir).filter((p) => !p.startsWith("(") && !p.startsWith("@"))
    for (const routes of Object.values(GROUPS)) {
      for (const route of routes) expect(outside, route).not.toContain(route)
    }
  })

  it("keep the immersive flows outside every group", () => {
    const grouped = pageDirs(appDir).filter((p) => p.startsWith("("))
    for (const p of grouped) expect(p, p).not.toMatch(/\/(record|carve)(\/|$)|\/edit(-pebble)?$/)
    for (const flow of ["pebble/[id]/edit", "pebble/[id]/edit-pebble", "record", "carve"]) {
      expect(existsSync(path.join(appDir, flow, "page.tsx")), flow).toBe(true)
    }
  })
})
