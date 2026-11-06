import { describe, it, expect } from "vitest"
import { getAllLocaleContent, getDocsManifest } from "./load-docs"

describe("the store-facing pages", () => {
  it("are listed in the manifest", () => {
    const slugs = getDocsManifest().pages.map((page) => page.slug)
    expect(slugs).toContain("support")
    expect(slugs).toContain("delete-account")
    expect(slugs).toContain("delete-data")
  })

  it("give the support address as a mail link in both languages", () => {
    for (const slug of ["support", "delete-account", "delete-data"]) {
      for (const page of Object.values(getAllLocaleContent(slug))) {
        expect(page.html).toContain('href="mailto:ciao@pbbls.app"')
      }
    }
  })

  // Play's account-deletion policy asks for the steps, what is erased and what
  // is kept for how long. The periods must match the privacy policy, section 7.
  it("state the steps and the retention periods in both languages", () => {
    for (const page of Object.values(getAllLocaleContent("delete-account"))) {
      expect(page.html).toContain('href="/login"')
      expect(page.html).toContain('href="/docs/privacy"')
      expect(page.html).toContain("12 ")
      expect(page.html).toContain("90 ")
    }
  })

  // Play's Data safety form links here for deleting data without closing the account.
  it("link partial deletion and account deletion to each other in both languages", () => {
    for (const page of Object.values(getAllLocaleContent("delete-data"))) {
      expect(page.html).toContain('href="/docs/delete-account"')
      expect(page.html).toContain('href="/docs/privacy"')
      expect(page.html).toContain("90 ")
    }
    for (const page of Object.values(getAllLocaleContent("delete-account"))) {
      expect(page.html).toContain('href="/docs/delete-data"')
    }
  })
})
