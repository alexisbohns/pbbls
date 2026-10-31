import { describe, expect, it } from "vitest"
import en from "@/lib/i18n/messages/en.json"
import fr from "@/lib/i18n/messages/fr.json"

// Home and You's strings (#1384, spec §i18n) exist in both catalogues. Scoped
// to the new namespaces, so existing drift elsewhere does not fail it.
function keys(value: unknown, prefix = ""): string[] {
  if (typeof value !== "object" || value === null) return [prefix]
  return Object.entries(value).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k))
}

describe("Home and You strings", () => {
  it("has the same home keys in English and French", () => {
    expect(keys(fr.home).sort()).toEqual(keys(en.home).sort())
  })

  it("has no empty string under home", () => {
    for (const catalogue of [en.home, fr.home]) {
      for (const key of keys(catalogue)) {
        const value = key.split(".").reduce<unknown>((o, k) => (typeof o === "object" && o !== null ? Reflect.get(o, k) : undefined), catalogue)
        expect(typeof value === "string" && value.trim() !== "", key).toBe(true)
      }
    }
  })
})
