import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { describe, it, expect } from "vitest"
import {
  CONSENT_DOCUMENT_VERSION,
  TERMS_DOCUMENT_VERSION,
  documentVersionFor,
} from "./consent"

function frontmatterVersion(relative: string): string | undefined {
  const path = fileURLToPath(new URL(relative, import.meta.url))
  const frontmatter = readFileSync(path, "utf8").split("---")[1]
  return frontmatter.match(/^version:\s*(.+)$/m)?.[1].trim()
}

/**
 * The consent record is only meaningful if it names the version of the document
 * the person actually saw. Nothing else in the system ties the constant to the
 * document, so this test is the whole binding: bump the policy without bumping
 * the constant and every consent recorded afterwards cites the wrong text.
 */
describe("CONSENT_DOCUMENT_VERSION", () => {
  it("matches the privacy policy frontmatter", () => {
    const version = frontmatterVersion("../../docs/privacy/en.md")

    expect(version).toBeDefined()
    expect(CONSENT_DOCUMENT_VERSION).toBe(version)
  })

  it("matches the French policy too, so the two cannot drift apart", () => {
    expect(CONSENT_DOCUMENT_VERSION).toBe(frontmatterVersion("../../docs/privacy/fr.md"))
  })
})

/** The same binding for the Terms: every `terms` row cites this constant. */
describe("TERMS_DOCUMENT_VERSION", () => {
  it("matches the Terms of Service frontmatter", () => {
    const version = frontmatterVersion("../../docs/terms/en.md")

    expect(version).toBeDefined()
    expect(TERMS_DOCUMENT_VERSION).toBe(version)
  })

  it("matches the French Terms too", () => {
    expect(TERMS_DOCUMENT_VERSION).toBe(frontmatterVersion("../../docs/terms/fr.md"))
  })
})

describe("documentVersionFor", () => {
  it("cites the Terms for terms, and the privacy policy for every other act", () => {
    expect(documentVersionFor("terms")).toBe(TERMS_DOCUMENT_VERSION)
    expect(documentVersionFor("privacy")).toBe(CONSENT_DOCUMENT_VERSION)
    expect(documentVersionFor("health_data")).toBe(CONSENT_DOCUMENT_VERSION)
    expect(documentVersionFor("age_assurance")).toBe(CONSENT_DOCUMENT_VERSION)
    expect(documentVersionFor("public_profile")).toBe(CONSENT_DOCUMENT_VERSION)
  })
})
