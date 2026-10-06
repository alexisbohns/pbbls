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
  isGatePublicPath,
  missingConsents,
  parseActiveConsents,
  resolveGateState,
  submitConsentGate,
  type ActiveConsent,
  type GateAuthInput,
  type GateStateInput,
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

  it("re-reads after a failed record, so a retry asks only for what is still missing", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    // terms records, then privacy fails: the ledger now holds terms.
    const ledger: ActiveConsent[] = [
      { kind: "health_data", document_version: CONSENT_DOCUMENT_VERSION },
      { kind: "age_assurance", document_version: CONSENT_DOCUMENT_VERSION },
    ]
    const record = vi.fn(async (kind: string, version: string) => {
      if (kind === "privacy") throw new Error("network")
      ledger.push({ kind, document_version: version })
    })
    const verdict = await submitConsentGate(["terms", "privacy"], record, async () => ledger)
    expect(verdict).toEqual({ status: "record-failed", missing: ["privacy"] })
  })

  it("keeps the original list when the re-read after a failed record fails too", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const verdict = await submitConsentGate(
      ["terms", "privacy"],
      async () => {
        throw new Error("invalid_kind")
      },
      async () => {
        throw new Error("network")
      },
    )
    expect(verdict).toEqual({ status: "record-failed", missing: ["terms", "privacy"] })
  })

  it("passes when a record reports failure but the re-read shows everything on record", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const verdict = await submitConsentGate(
      ["terms"],
      async () => {
        throw new Error("timeout")
      },
      async () => current(),
    )
    expect(verdict).toEqual({ status: "satisfied" })
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
  const base = { isLoading: false, isAuthenticated: true, isProfileLoading: false }

  it("waits while the session check runs, when there is no user yet", () => {
    expect(
      gateApplies({ isLoading: true, isAuthenticated: false, isProfileLoading: true, profile: null }),
    ).toBe("pending")
  })

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

describe("resolveGateState", () => {
  /** What ReconsentGate computes for one render: gateApplies, then the state. */
  const decide = (
    auth: GateAuthInput & { userId: string | null },
    rest: Partial<Omit<GateStateInput, "userId" | "applies" | "publicRoute">> = {},
    pathname = "/path",
  ) =>
    resolveGateState({
      userId: auth.userId,
      applies: isGateExemptPath(pathname) ? "skip" : gateApplies(auth),
      publicRoute: isGatePublicPath(pathname),
      cached: false,
      result: null,
      submitting: false,
      recordFailed: false,
      ...rest,
    })
  const loading = {
    isLoading: true,
    isAuthenticated: false,
    isProfileLoading: true,
    profile: null,
    userId: null,
  }
  const signedOut = { ...loading, isLoading: false, isProfileLoading: false }
  const signedIn = {
    isLoading: false,
    isAuthenticated: true,
    isProfileLoading: false,
    profile: { onboarding_completed: true },
    userId: "u1",
  }
  const required = {
    result: { userId: "u1", verdict: { status: "required" as const, missing: ["terms" as const] } },
  }

  it("holds a route that needs sign-in while sign-in loads", () => {
    for (const path of ["/path", "/pebble/abc", "/settings", "/onboarding", "/wallet"]) {
      expect(decide(loading, {}, path)).toEqual({ status: "checking" })
    }
    // A cached pass cannot be read without a user, so it does not open early.
    expect(decide(loading, { cached: true })).toEqual({ status: "checking" })
  })

  it("renders a public route while sign-in loads, so its server HTML is the page", () => {
    for (const path of ["/", "/u/alexis", "/p/abc", "/invite/tok", "/lab", "/login", "/register"]) {
      expect(decide(loading, {}, path)).toEqual({ status: "open" })
    }
  })

  it("still holds a public route for a signed-in user who owes consent, once loaded", () => {
    expect(decide(signedIn, {}, "/u/alexis")).toEqual({ status: "checking" })
    expect(decide(signedIn, required, "/")).toEqual({
      status: "required",
      missing: ["terms"],
      submitting: false,
      recordFailed: false,
    })
  })

  it("renders the page for a signed-out visitor once auth has finished", () => {
    expect(decide(signedOut, {}, "/u/alexis")).toEqual({ status: "open" })
    expect(decide(signedOut, {}, "/path")).toEqual({ status: "open" })
  })

  it("never covers the legal documents", () => {
    expect(decide(loading, {}, "/docs/terms")).toEqual({ status: "open" })
    expect(decide(signedIn, required, "/docs/privacy")).toEqual({ status: "open" })
  })

  it("holds a signed-in user whose check is pending", () => {
    expect(decide({ ...signedIn, isProfileLoading: true, profile: null })).toEqual({ status: "checking" })
    expect(decide(signedIn)).toEqual({ status: "checking" })
  })

  it("asks a signed-in user for what is missing", () => {
    expect(
      decide(signedIn, {
        result: { userId: "u1", verdict: { status: "required", missing: ["terms"] } },
        recordFailed: true,
      }),
    ).toEqual({ status: "required", missing: ["terms"], submitting: false, recordFailed: true })
  })

  it("opens for a signed-in user who passed, through a later profile re-fetch", () => {
    const passed = { result: { userId: "u1", verdict: { status: "satisfied" as const } } }
    expect(decide(signedIn, passed)).toEqual({ status: "open" })
    expect(decide({ ...signedIn, isProfileLoading: true }, passed)).toEqual({ status: "open" })
    expect(decide(signedIn, { cached: true })).toEqual({ status: "open" })
  })

  it("never lets a verdict reached for another user through", () => {
    expect(
      decide({ ...signedIn, userId: "u2" }, { result: { userId: "u1", verdict: { status: "satisfied" } } }),
    ).toEqual({ status: "checking" })
  })

  it("fails closed on a failed read", () => {
    expect(decide(signedIn, { result: { userId: "u1", verdict: { status: "failed" } } })).toEqual({
      status: "failed",
    })
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

  it("lists the routes a signed-out visitor can read as public", () => {
    for (const path of [
      "/",
      "/login",
      "/register",
      "/u/alexis",
      "/p/6f1c2a0e",
      "/invite/abc",
      "/lab",
      "/lab/changelog",
      "/lab/announcements/1",
      "/offline",
      "/sandbox/design",
    ]) {
      expect(isGatePublicPath(path), path).toBe(true)
    }
  })

  it("treats protected and signed-in-only routes as not public", () => {
    // AuthGate's PROTECTED_PREFIXES, then the unprotected signed-in screens.
    for (const path of [
      "/path",
      "/record",
      "/pebble/abc",
      "/collections",
      "/souls/1",
      "/glyphs",
      "/carve",
      "/profile",
      "/connections/1",
      "/settings",
      "/onboarding",
      "/achievements",
      "/drafts",
      "/wallet",
      // Prefix lookalikes and unknown routes are held by default.
      "/users",
      "/pebbles",
      "/labx",
      "/something-new",
    ]) {
      expect(isGatePublicPath(path), path).toBe(false)
    }
  })
})
