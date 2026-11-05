import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"
import { crisisResources, defaultCountry } from "./crisis-resources"

describe("crisis resources", () => {
  it("is byte-identical to the canonical file", () => {
    const canonical = readFileSync(
      resolve(process.cwd(), "../../packages/shared/crisis-resources.json"),
      "utf8",
    )
    const copy = readFileSync(resolve(process.cwd(), "lib/crisis/crisis-resources.json"), "utf8")
    expect(copy).toBe(canonical)
  })

  it("has digits-only tel and both locales on every label", () => {
    for (const country of crisisResources.countries) {
      expect(country.name.en).not.toBe("")
      expect(country.name.fr).not.toBe("")
      for (const line of country.lines) {
        expect(line.tel).toMatch(/^\d+$/)
        expect(line.label.en).not.toBe("")
        expect(line.label.fr).not.toBe("")
        if (line.note) {
          expect(line.note.en).not.toBe("")
          expect(line.note.fr).not.toBe("")
        }
      }
    }
  })

  it("falls back to FR when the region is unknown or missing", () => {
    expect(defaultCountry("gb").code).toBe("GB")
    expect(defaultCountry("XX").code).toBe("FR")
    expect(defaultCountry(undefined).code).toBe("FR")
  })
})
