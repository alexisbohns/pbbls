import { describe, it, expect } from "vitest"
import { isStoreBackedRoute } from "./store-routes"

describe("isStoreBackedRoute", () => {
  it("matches store-backed routes and their children", () => {
    for (const pathname of [
      "/path",
      "/path/2026-W19",
      "/souls",
      "/souls/abc",
      "/wallet",
      "/achievements",
      "/drafts",
      "/settings",
      "/record",
      "/collections/xyz",
    ]) {
      expect(isStoreBackedRoute(pathname), pathname).toBe(true)
    }
  })

  it("leaves store-free routes alone so they never show a false failure", () => {
    for (const pathname of [
      "/",
      "/login",
      "/register",
      "/docs/privacy",
      "/u/alexis",
      "/p/8f14e45f",
      "/offline",
      "/onboarding/step-1",
      "/invite/tok3n",
    ]) {
      expect(isStoreBackedRoute(pathname), pathname).toBe(false)
    }
  })

  it("matches on whole segments, not a bare prefix", () => {
    // "/pathological" starts with "/path" — a bare startsWith would gate it.
    expect(isStoreBackedRoute("/pathological")).toBe(false)
    expect(isStoreBackedRoute("/profiles-public")).toBe(false)
    expect(isStoreBackedRoute("/walletx")).toBe(false)
  })
})
