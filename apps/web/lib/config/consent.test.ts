import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, it, expect } from "vitest"
import { CONSENT_DOCUMENT_VERSION } from "./consent"

/**
 * The consent record is only meaningful if it names the version of the document
 * the person actually saw. Nothing else in the system ties the constant to the
 * document, so this test is the whole binding: bump the policy without bumping
 * the constant and every consent recorded afterwards cites the wrong text.
 */
describe("CONSENT_DOCUMENT_VERSION", () => {
  it("matches the privacy policy frontmatter", () => {
    const path = fileURLToPath(new URL("../../docs/privacy/en.md", import.meta.url))
    const frontmatter = readFileSync(path, "utf8").split("---")[1]
    const version = frontmatter.match(/^version:\s*(.+)$/m)?.[1].trim()

    expect(version).toBeDefined()
    expect(CONSENT_DOCUMENT_VERSION).toBe(version)
  })

  it("matches the French policy too, so the two cannot drift apart", () => {
    const path = fileURLToPath(new URL("../../docs/privacy/fr.md", import.meta.url))
    const frontmatter = readFileSync(path, "utf8").split("---")[1]
    const version = frontmatter.match(/^version:\s*(.+)$/m)?.[1].trim()

    expect(CONSENT_DOCUMENT_VERSION).toBe(version)
  })
})
