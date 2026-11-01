import { describe, expect, it } from "vitest"
import payload from "./__fixtures__/settings-links.json"
import edge from "./__fixtures__/settings-links-edge.json"
import { DEFAULT_SETTINGS_LINKS, decodeSettingsLinks, groupSettingsLinks } from "./settings-links"

describe("decodeSettingsLinks", () => {
  it("reads the database payload in English", () => {
    const links = decodeSettingsLinks(payload, "en")
    expect(links.map((l) => l.key)).toEqual(["credits", "terms", "privacy", "legal-notice", "support"])
    expect(links.find((l) => l.key === "support")).toMatchObject({ url: "mailto:ciao@pbbls.app", external: false })
  })
  it("reads French and falls back to English on a null title_fr", () => {
    expect(decodeSettingsLinks(payload, "fr").find((l) => l.key === "privacy")?.title).toBe("Politique de confidentialité")
    const nullFr = decodeSettingsLinks(edge, "fr").find((l) => l.section === "about")
    expect(nullFr?.title).toBe("Credits")
  })
  it("drops unknown sections and schemes", () => {
    const links = decodeSettingsLinks(edge, "en")
    expect(links.map((l) => l.key)).toEqual(["credits"])
    expect(links.some((l) => l.url.startsWith("ftp:"))).toBe(false)
  })
  it("ships a built-in list equal to the seed", () => {
    expect(DEFAULT_SETTINGS_LINKS).toEqual(payload)
  })
  it("groups in the fixed section order", () => {
    expect(groupSettingsLinks(decodeSettingsLinks(payload, "en")).map((g) => g.section)).toEqual(["legal", "about", "support"])
  })
})
