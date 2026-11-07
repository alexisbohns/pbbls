import { describe, it, expect } from "vitest"
import { getAllLocaleContent, getDocsManifest } from "./load-docs"
import { slugify, tocLabel } from "./outline"

describe("the docs outline", () => {
  it("drops the numbering and the accents", () => {
    expect(tocLabel("7. Data Retention Periods")).toBe("Data Retention Periods")
    expect(tocLabel("Preamble")).toBe("Preamble")
    expect(slugify("Données collectées")).toBe("donnees-collectees")
  })

  it("lifts the title out of every page and anchors every section", () => {
    for (const { slug } of getDocsManifest().pages) {
      for (const page of Object.values(getAllLocaleContent(slug))) {
        expect(page.title).not.toBe("")
        expect(page.html).not.toContain("<h1")
        for (const item of page.toc) expect(page.html).toContain(`<h2 id="${item.id}">`)
        expect(new Set(page.toc.map((item) => item.id)).size).toBe(page.toc.length)
      }
    }
  })
})
