import { describe, it, expect, vi } from "vitest"
import { CONSENT_DOCUMENT_VERSION, TERMS_DOCUMENT_VERSION } from "@/lib/config/consent"
import {
  GATE_KINDS,
  checkConsentGate,
  gateApplies,
  gateCacheValue,
  gateFingerprint,
  isAtLeast,
  isGateExemptPath,
  missingConsents,
  parseActiveConsents,
  submitConsentGate,
  type ActiveConsent,
} from "./consent-gate"

const current = (): ActiveConsent[] => [
  { kind: "terms", document_version: TERMS_DOCUMENT_VERSION },
  { kind: "privacy", document_version: CONSENT_DOCUMENT_VERSION },
  { kind: "health_data", document_version: CONSENT_DOCUMENT_VERSION },
  { kind: "age_assurance", document_version: CONSENT_DOCUMENT_VERSION },
]

/**
 * Verbatim PostgREST rows for `select kind, document_version` on accounts the
 * OTHER surfaces created, as the gate reads them. Android's email signup
 * (`consentMetadata`, #822) writes all four through handle_new_user; its gate
 * (#967) records the same kinds through record_consent. iOS signup today
 * records health_data and age_assurance only (#821 is open), so an iOS account
 * is asked for terms and privacy.
 */
const ANDROID_SIGNUP_ROWS: unknown = JSON.parse(
  '[{"kind":"health_data","document_version":"1.3.0"},' +
    '{"kind":"age_assurance","document_version":"1.3.0"},' +
    '{"kind":"terms","document_version":"1.1.0"},' +
    '{"kind":"privacy","document_version":"1.3.0"}]',
)
const IOS_SIGNUP_ROWS: unknown = JSON.parse(
  '[{"kind":"health_data","document_version":"1.3.0"},' +
    '{"kind":"age_assurance","document_version":"1.3.0"}]',
)
/**
 * A full user_consents row as PostgREST serialises it: microsecond
 * `granted_at`, explicit nulls. The gate must not care about any of it.
 */
const FULL_ROW_PAYLOAD: unknown = JSON.parse(
  '[{"id":"6f1c2a0e-5b7d-4f8e-9a51-0c2d3e4f5a6b","kind":"terms","document_version":"1.1.0",' +
    '"source":"android_settings","granted_at":"2026-09-27T16:40:03.952431+00:00",' +
    '"withdrawn_at":null,"superseded_at":null}]',
)

describe("isAtLeast", () => {
  it("compares numerically, not lexically", () => {
    expect(isAtLeast("1.10.0", "1.9.0")).toBe(true)
    expect(isAtLeast("1.3.0", "1.3.0")).toBe(true)
    expect(isAtLeast("2.0.0", "1.9.9")).toBe(true)
    expect(isAtLeast("1.2.9", "1.3.0")).toBe(false)
  })

  it("lets an unparsable version satisfy nothing", () => {
    expect(isAtLeast("not-a-version", "1.3.0")).toBe(false)
    expect(isAtLeast("1.3", "1.3.0")).toBe(false)
    expect(isAtLeast("1.3.0-beta", "1.3.0")).toBe(false)
    expect(isAtLeast("", "1.3.0")).toBe(false)
  })
})

describe("missingConsents", () => {
  it("asks an empty ledger for all four, in display order", () => {
    expect(missingConsents([])).toEqual(["terms", "privacy", "health_data", "age_assurance"])
  })

  it("asks a ledger at the current versions for nothing", () => {
    expect(missingConsents(current())).toEqual([])
  })

  it("asks only for the kinds that are absent", () => {
    const active = current().filter((r) => r.kind !== "age_assurance" && r.kind !== "terms")
    expect(missingConsents(active)).toEqual(["terms", "age_assurance"])
  })

  it("asks again for a kind held only at an older version", () => {
    const active = current().map((r) =>
      r.kind === "privacy" ? { ...r, document_version: "1.2.0" } : r,
    )
    expect(missingConsents(active)).toEqual(["privacy"])
  })

  /**
   * record_consent supersedes a grant at ANY different version, so asking
   * again here would downgrade a newer acceptance to this build's version.
   */
  it("never asks again for a kind held at a NEWER version", () => {
    const active = current().map((r) =>
      r.kind === "health_data" ? { ...r, document_version: "1.10.0" } : r,
    )
    expect(missingConsents(active)).toEqual([])
  })

  it("ignores kinds it does not ask for", () => {
    expect(missingConsents([...current(), { kind: "public_profile", document_version: "1.1.0" }])).toEqual([])
  })

  it("is satisfied by an Android-created account", () => {
    expect(missingConsents(parseActiveConsents(ANDROID_SIGNUP_ROWS))).toEqual([])
  })

  it("asks an iOS-created account for terms and privacy only", () => {
    expect(missingConsents(parseActiveConsents(IOS_SIGNUP_ROWS))).toEqual(["terms", "privacy"])
  })
})

describe("parseActiveConsents", () => {
  it("reads a full PostgREST row, ignoring timestamps and explicit nulls", () => {
    expect(parseActiveConsents(FULL_ROW_PAYLOAD)).toEqual([
      { kind: "terms", document_version: "1.1.0" },
    ])
  })

  it("drops a row whose version is null or missing, so its kind is asked for", () => {
    const rows = parseActiveConsents([
      { kind: "terms", document_version: null },
      { kind: "privacy" },
      null,
      "garbage",
    ])
    expect(rows).toEqual([])
  })

  it("treats a non-array payload as a failed read", () => {
    expect(() => parseActiveConsents(null)).toThrow()
    expect(() => parseActiveConsents({ kind: "terms" })).toThrow()
  })
})

describe("checkConsentGate", () => {
  it("is satisfied when nothing is missing", async () => {
    expect(await checkConsentGate(async () => current())).toEqual({ status: "satisfied" })
  })

  it("is required, listing only what is missing or outdated", async () => {
    const active = [{ kind: "terms", document_version: "1.0.0" }, ...current().slice(1)]
    expect(await checkConsentGate(async () => active)).toEqual({
      status: "required",
      missing: ["terms"],
    })
  })

  /** Fail closed (#784): a failed read is never an empty ledger, never a pass. */
  it("fails closed when the read throws", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    expect(
      await checkConsentGate(async () => {
        throw new Error("load consents timed out")
      }),
    ).toEqual({ status: "failed" })
  })

  it("fails closed when the payload is malformed", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    expect(await checkConsentGate(async () => parseActiveConsents(undefined))).toEqual({
      status: "failed",
    })
  })
})

describe("submitConsentGate", () => {
  it("records each missing act at its own version, then re-reads", async () => {
    const record = vi.fn(async () => {})
    const verdict = await submitConsentGate(["terms", "health_data"], record, async () => current())
    expect(record.mock.calls).toEqual([
      ["terms", TERMS_DOCUMENT_VERSION],
      ["health_data", CONSENT_DOCUMENT_VERSION],
    ])
    expect(verdict).toEqual({ status: "satisfied" })
  })

  it("reports a failed record without re-reading", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const load = vi.fn(async () => current())
    const verdict = await submitConsentGate(
      ["terms"],
      async () => {
        throw new Error("invalid_kind")
      },
      load,
    )
    expect(verdict).toEqual({ status: "record-failed" })
    expect(load).not.toHaveBeenCalled()
  })

  it("fails closed when the re-read after recording fails", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const verdict = await submitConsentGate(["terms"], async () => {}, async () => {
      throw new Error("network")
    })
    expect(verdict).toEqual({ status: "failed" })
  })
})

describe("gateApplies", () => {
  const base = { isAuthenticated: true, isProfileLoading: false }

  it("skips a signed-out visitor", () => {
    expect(gateApplies({ ...base, isAuthenticated: false, profile: null })).toBe("skip")
  })

  it("waits for the profile", () => {
    expect(gateApplies({ ...base, isProfileLoading: true, profile: null })).toBe("pending")
  })

  it("skips an account mid-onboarding, which onboarding's ConsentGate covers", () => {
    expect(gateApplies({ ...base, profile: { onboarding_completed: false } })).toBe("skip")
  })

  it("applies to an onboarded account", () => {
    expect(gateApplies({ ...base, profile: { onboarding_completed: true } })).toBe("applies")
  })

  /** #784: a null profile may be a failed read of an onboarded account. */
  it("applies when the profile could not be read", () => {
    expect(gateApplies({ ...base, profile: null })).toBe("applies")
  })
})

describe("gate cache and routes", () => {
  it("fingerprints every required kind at its version, in Android's format", () => {
    expect(gateFingerprint()).toBe(
      GATE_KINDS.map((k) => `${k}@${k === "terms" ? TERMS_DOCUMENT_VERSION : CONSENT_DOCUMENT_VERSION}`).join(","),
    )
    expect(gateFingerprint()).toMatch(
      /^terms@\d+\.\d+\.\d+,privacy@\d+\.\d+\.\d+,health_data@\d+\.\d+\.\d+,age_assurance@\d+\.\d+\.\d+$/,
    )
  })

  it("keys a cached pass on the user", () => {
    expect(gateCacheValue("u1")).not.toBe(gateCacheValue("u2"))
    expect(gateCacheValue("u1").startsWith("u1|")).toBe(true)
  })

  it("leaves only the legal documents uncovered", () => {
    expect(isGateExemptPath("/docs/terms")).toBe(true)
    expect(isGateExemptPath("/docs")).toBe(true)
    expect(isGateExemptPath("/docsx")).toBe(false)
    expect(isGateExemptPath("/path")).toBe(false)
    expect(isGateExemptPath("/onboarding")).toBe(false)
  })
})
