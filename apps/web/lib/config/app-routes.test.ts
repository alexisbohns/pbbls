import { describe, expect, it } from "vitest"
import { routeShell, TAB_ROOTS } from "./app-routes"

describe("routeShell", () => {
  it("makes the five tab roots, and only them, carry the navigation", () => {
    expect(TAB_ROOTS).toEqual(["/home", "/path", "/souls", "/collections", "/profile"])
    for (const p of TAB_ROOTS) expect(routeShell(p).tab, p).toBe(true)
    for (const p of ["/souls/abc", "/collections/xyz", "/settings", "/record", "/pebble/1", "/guides/stones", "/"]) {
      expect(routeShell(p).tab, p).toBe(false)
    }
  })

  it("puts the tabs, Settings and the guides on the M3 ground, and keeps deeper pages on the colour world", () => {
    for (const p of [...TAB_ROOTS, "/settings", "/guides/stones"]) expect(routeShell(p).m3, p).toBe(true)
    for (const p of ["/record", "/carve"]) {
      expect(routeShell(p).m3, p).toBe(false)
    }
  })

  it("keeps the full-screen, immersive and gated sets the shell always had", () => {
    for (const p of ["/", "/login", "/register", "/onboarding", "/onboarding/step-1", "/path", "/blog", "/blog/a-post"]) {
      expect(routeShell(p).fullScreen, p).toBe(true)
    }
    expect(routeShell("/home").fullScreen).toBe(false)
    expect(routeShell("/record/complete/1").immersive).toBe(true)
    expect(routeShell("/carve").immersive).toBe(true)
    for (const p of ["/", "/login", "/register", "/docs/privacy", "/u/alexis", "/blog"]) expect(routeShell(p).gated, p).toBe(false)
    for (const p of ["/home", "/path", "/settings", "/onboarding"]) expect(routeShell(p).gated, p).toBe(true)
  })

  it("matches whole segments", () => {
    expect(routeShell("/pathological").path).toBe(false)
    expect(routeShell("/path").path).toBe(true)
    expect(routeShell("/homework").tab).toBe(false)
  })

  it("places each detail beside its tab, and only the details moved into the slot", () => {
    expect(routeShell("/guides/stones")).toMatchObject({ detail: true, parentTab: "/home", tab: false, m3: true })
    expect(routeShell("/wallet")).toMatchObject({ detail: true, parentTab: "/path", m3: true })
    expect(routeShell("/souls")).toMatchObject({ detail: false, parentTab: "/souls" })
    expect(routeShell("/souls/x")).toMatchObject({ detail: true, parentTab: "/souls", m3: true })
    expect(routeShell("/collections/x")).toMatchObject({ detail: true, parentTab: "/collections" })
    expect(routeShell("/connections")).toMatchObject({ detail: true, parentTab: "/souls" })
    expect(routeShell("/connections/x")).toMatchObject({ detail: true, parentTab: "/souls" })
    expect(routeShell("/settings")).toMatchObject({ detail: true, parentTab: "/profile", m3: true })
    expect(routeShell("/pebble/1")).toMatchObject({ detail: true, parentTab: "/path", overAnyTab: true, m3: true })
    for (const p of ["/pebble/1/edit", "/pebble/1/edit-pebble"]) expect(routeShell(p), p).toMatchObject({ detail: false, overAnyTab: false })
    for (const p of [
      "/glyphs",
      "/glyphs/x",
      "/achievements",
      "/achievements/explorer",
      "/achievements/emotions",
      "/achievements/domains",
      "/achievements/pebbles",
      "/achievements/year/2026",
      "/lab",
      "/lab/changelog",
      "/lab/backlog",
      "/lab/announcements/x",
    ]) {
      expect(routeShell(p), p).toMatchObject({ detail: true, parentTab: "/profile", m3: true })
    }
    for (const p of ["/pebble/1/edit", "/record/complete/1", "/p/1", "/u/x", "/blog", "/docs"]) {
      expect(routeShell(p).detail, p).toBe(false)
    }
    expect(routeShell("/record").parentTab).toBeNull()
  })
})
