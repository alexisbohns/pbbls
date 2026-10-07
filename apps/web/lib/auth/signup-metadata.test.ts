import { describe, it, expect } from "vitest"
import { CONSENT_DOCUMENT_VERSION, TERMS_DOCUMENT_VERSION } from "@/lib/config/consent"
import { signupConsentMetadata, wholeSecondIso } from "./signup-metadata"

/**
 * Copied verbatim from `handle_new_user`'s `declare` block
 * (`packages/supabase/supabase/migrations/20260927090000_consent_terms_privacy.sql`)
 * plus `signup_surface`. Android pins the same set in `ConsentMetadataTest`.
 * Move it with the trigger.
 */
const TRIGGER_KEYS = [
  "terms_accepted_at",
  "terms_version",
  "privacy_accepted_at",
  "privacy_version",
  "health_data_consent_at",
  "health_data_consent_version",
  "age_attested_at",
  "age_attestation_version",
  "signup_surface",
]

/**
 * What Android's `SupabaseService.consentMetadata("2026-07-11T12:00:00Z")`
 * sends for the same act set, verbatim apart from the surface. Web must produce
 * the same keys, the same versions and the same timestamp form, because one
 * trigger reads both.
 */
const ANDROID_PAYLOAD = {
  terms_accepted_at: "2026-07-11T12:00:00Z",
  terms_version: "1.2.0",
  privacy_accepted_at: "2026-07-11T12:00:00Z",
  privacy_version: "1.4.0",
  health_data_consent_at: "2026-07-11T12:00:00Z",
  health_data_consent_version: "1.4.0",
  age_attested_at: "2026-07-11T12:00:00Z",
  age_attestation_version: "1.4.0",
  signup_surface: "android",
}

const allTicked = {
  terms_accepted: true,
  privacy_accepted: true,
  health_data_consent: true,
  age_attested: true,
}

describe("signupConsentMetadata", () => {
  it("sends exactly the keys handle_new_user reads", () => {
    const payload = signupConsentMetadata(allTicked, new Date("2026-07-11T12:00:00Z"))
    expect(Object.keys(payload).sort()).toEqual([...TRIGGER_KEYS].sort())
  })

  it("matches Android's payload for the same instant, surface aside", () => {
    const payload = signupConsentMetadata(allTicked, new Date("2026-07-11T12:00:00.734Z"))
    expect(payload).toEqual({ ...ANDROID_PAYLOAD, signup_surface: "web" })
  })

  it("cites the Terms version for terms and the privacy version for every other act", () => {
    const payload = signupConsentMetadata(allTicked, new Date())
    expect(payload.terms_version).toBe(TERMS_DOCUMENT_VERSION)
    expect(payload.privacy_version).toBe(CONSENT_DOCUMENT_VERSION)
    expect(payload.health_data_consent_version).toBe(CONSENT_DOCUMENT_VERSION)
    expect(payload.age_attestation_version).toBe(CONSENT_DOCUMENT_VERSION)
  })

  it("stamps every act with the one sign-up instant", () => {
    const payload = signupConsentMetadata(allTicked, new Date("2026-09-27T10:00:00.987Z"))
    const stamps = new Set([
      payload.terms_accepted_at,
      payload.privacy_accepted_at,
      payload.health_data_consent_at,
      payload.age_attested_at,
    ])
    expect([...stamps]).toEqual(["2026-09-27T10:00:00Z"])
  })

  it("declares the web surface, so the trigger never relies on its fallback", () => {
    expect(signupConsentMetadata(allTicked, new Date()).signup_surface).toBe("web")
  })

  /** The trigger guards each insert on both keys being non-null. */
  it("sends explicit nulls, timestamp and version alike, for an unticked act", () => {
    const payload = signupConsentMetadata(
      { ...allTicked, terms_accepted: false, age_attested: false },
      new Date("2026-09-27T10:00:00Z"),
    )
    expect(payload.terms_accepted_at).toBeNull()
    expect(payload.terms_version).toBeNull()
    expect(payload.age_attested_at).toBeNull()
    expect(payload.age_attestation_version).toBeNull()
    expect(payload.privacy_accepted_at).toBe("2026-09-27T10:00:00Z")
    expect("terms_version" in payload).toBe(true)
  })
})

describe("wholeSecondIso", () => {
  /** The same case as Android's `the sign-up instant is whole seconds`. */
  it("truncates to whole seconds with no fractional part", () => {
    const stamp = wholeSecondIso(new Date("2026-09-27T10:00:00.987Z"))
    expect(stamp).toBe("2026-09-27T10:00:00Z")
    expect(stamp).not.toContain(".")
  })

  it("leaves an already whole-second instant unchanged", () => {
    expect(wholeSecondIso(new Date("2026-07-11T12:00:00Z"))).toBe("2026-07-11T12:00:00Z")
  })
})
