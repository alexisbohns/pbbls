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
    for (const p of ["/souls/abc", "/collections/xyz", "/pebble/1", "/record", "/carve", "/wallet", "/achievements"]) {
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
})
