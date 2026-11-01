import { describe, expect, it } from "vitest"
import { isSettingsLinkSection, isAllowedLinkUrl, SETTINGS_LINK_SECTIONS } from "./options"

describe("settings link options", () => {
  it("knows the three sections in display order", () => {
    expect(SETTINGS_LINK_SECTIONS).toEqual(["legal", "about", "support"])
    expect(isSettingsLinkSection("about")).toBe(true)
    expect(isSettingsLinkSection("beta")).toBe(false)
  })
  it("accepts https and mailto only", () => {
    expect(isAllowedLinkUrl("https://www.pbbls.app/docs/terms")).toBe(true)
    expect(isAllowedLinkUrl("mailto:ciao@pbbls.app")).toBe(true)
    expect(isAllowedLinkUrl("http://www.pbbls.app")).toBe(false)
    expect(isAllowedLinkUrl("javascript:alert(1)")).toBe(false)
    expect(isAllowedLinkUrl("")).toBe(false)
  })
})
