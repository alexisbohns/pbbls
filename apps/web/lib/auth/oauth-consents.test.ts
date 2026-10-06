import { describe, it, expect } from "vitest"
import { CONSENT_DOCUMENT_VERSION, TERMS_DOCUMENT_VERSION } from "@/lib/config/consent"
import { oauthConsentActs } from "./oauth-consents"

describe("oauthConsentActs", () => {
  it("records all four register acts, each at its own document version", () => {
    expect(oauthConsentActs(CONSENT_DOCUMENT_VERSION)).toEqual([
      { kind: "terms", version: TERMS_DOCUMENT_VERSION },
      { kind: "privacy", version: CONSENT_DOCUMENT_VERSION },
      { kind: "health_data", version: CONSENT_DOCUMENT_VERSION },
      { kind: "age_assurance", version: CONSENT_DOCUMENT_VERSION },
    ])
  })

  it("records nothing without the param (the /login OAuth buttons)", () => {
    expect(oauthConsentActs(null)).toEqual([])
    expect(oauthConsentActs("")).toEqual([])
  })

  it("records nothing for a stale or crafted version", () => {
    expect(oauthConsentActs("1.2.0")).toEqual([])
    expect(oauthConsentActs("9.9.9")).toEqual([])
  })
})
