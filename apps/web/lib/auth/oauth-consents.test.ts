import { describe, it, expect, vi } from "vitest"
import { CONSENT_DOCUMENT_VERSION, TERMS_DOCUMENT_VERSION } from "@/lib/config/consent"
import { isAtLeast, parseActiveConsents } from "./consent-gate"
import { oauthConsentActs, recordOAuthConsents, type OAuthConsentAct } from "./oauth-consents"

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

describe("recordOAuthConsents", () => {
  const recorder = () => {
    const recorded: OAuthConsentAct[] = []
    return {
      recorded,
      record: async (act: OAuthConsentAct) => {
        recorded.push(act)
      },
    }
  }

  /**
   * Verbatim PostgREST rows (`select kind, document_version`, the callback's
   * read) for an existing account that accepted NEWER terms and privacy on
   * Android (through its consent gate) than this build ships, and holds
   * health_data / age_assurance at an OLDER privacy version.
   */
  const ANDROID_MIXED_ROWS: unknown = JSON.parse(
    '[{"kind":"terms","document_version":"2.0.0"},' +
      '{"kind":"privacy","document_version":"2.0.0"},' +
      '{"kind":"health_data","document_version":"1.0.0"},' +
      '{"kind":"age_assurance","document_version":"1.0.0"}]',
  )

  it("records all four acts for a new account with an empty ledger", async () => {
    const { recorded, record } = recorder()
    await recordOAuthConsents(CONSENT_DOCUMENT_VERSION, async () => [], record)
    expect(recorded).toEqual(oauthConsentActs(CONSENT_DOCUMENT_VERSION))
  })

  it("never downgrades a newer grant recorded on Android, and updates an older one", async () => {
    // The fixture only means something while 2.0.0 is newer, and 1.0.0 older, than this build.
    expect(isAtLeast(TERMS_DOCUMENT_VERSION, "2.0.0")).toBe(false)
    expect(isAtLeast(CONSENT_DOCUMENT_VERSION, "2.0.0")).toBe(false)
    expect(isAtLeast("1.0.0", CONSENT_DOCUMENT_VERSION)).toBe(false)

    const { recorded, record } = recorder()
    await recordOAuthConsents(
      CONSENT_DOCUMENT_VERSION,
      async () => parseActiveConsents(ANDROID_MIXED_ROWS),
      record,
    )
    expect(recorded).toEqual([
      { kind: "health_data", version: CONSENT_DOCUMENT_VERSION },
      { kind: "age_assurance", version: CONSENT_DOCUMENT_VERSION },
    ])
  })

  it("skips acts already held at the same version (a replayed callback)", async () => {
    const { recorded, record } = recorder()
    const held = oauthConsentActs(CONSENT_DOCUMENT_VERSION).map((a) => ({
      kind: a.kind,
      document_version: a.version,
    }))
    await recordOAuthConsents(CONSENT_DOCUMENT_VERSION, async () => held, record)
    expect(recorded).toEqual([])
  })

  it("records nothing when the ledger cannot be read, leaving it to the gate", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const { recorded, record } = recorder()
    await recordOAuthConsents(
      CONSENT_DOCUMENT_VERSION,
      async () => {
        throw new Error("network")
      },
      record,
    )
    // A malformed payload is a failed read too, not an empty ledger.
    await recordOAuthConsents(CONSENT_DOCUMENT_VERSION, async () => parseActiveConsents(null), record)
    expect(recorded).toEqual([])
  })

  it("does not read the ledger without a valid consent param", async () => {
    const load = vi.fn(async () => [])
    const { recorded, record } = recorder()
    await recordOAuthConsents(null, load, record)
    expect(load).not.toHaveBeenCalled()
    expect(recorded).toEqual([])
  })

  it("keeps going after one record fails", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    const attempted: string[] = []
    await recordOAuthConsents(CONSENT_DOCUMENT_VERSION, async () => [], async (act) => {
      attempted.push(act.kind)
      if (act.kind === "terms") throw new Error("invalid_kind")
    })
    expect(attempted).toEqual(["terms", "privacy", "health_data", "age_assurance"])
  })
})
