import {
  CONSENT_DOCUMENT_VERSION,
  documentVersionFor,
  type ConsentKind,
} from "@/lib/config/consent"
import { isAtLeast, type ActiveConsent } from "@/lib/auth/consent-gate"

/** One `record_consent` call the OAuth callback makes. */
export type OAuthConsentAct = { kind: ConsentKind; version: string }

/** The acts /register's four checkboxes collect, in the order they render. */
const REGISTER_ACTS: readonly ConsentKind[] = ["terms", "privacy", "health_data", "age_assurance"]

/**
 * The consent acts an OAuth callback records, given its `?consent=` param.
 *
 * /register gates both OAuth buttons on all four checkboxes and then puts the
 * privacy version on the callback URL, because no signup metadata survives an
 * OAuth round trip. So a valid param stands for all four acts: ticking every box
 * is what let the redirect happen at all.
 *
 * The only legitimate caller is our own `buildCallbackUrl`, which always sends
 * the current `CONSENT_DOCUMENT_VERSION`. Anything else is a stale bundle or a
 * crafted parameter, and the ledger is an accountability record: a garbage
 * `document_version` is worse than a missing row, since a missing row is at
 * least visibly absent (and the consent gate will ask for it). So a mismatch
 * records nothing. Each act cites the version this server ships for its kind.
 */
export function oauthConsentActs(consentParam: string | null): OAuthConsentAct[] {
  if (consentParam !== CONSENT_DOCUMENT_VERSION) return []
  return REGISTER_ACTS.map((kind) => ({ kind, version: documentVersionFor(kind) }))
}

/**
 * Records the callback's acts, skipping any the account already holds at the
 * same or a newer version. Returns the acts it attempted.
 *
 * /register's OAuth buttons also sign in an EXISTING account, and
 * `record_consent` supersedes an active grant at ANY different version, newer
 * included. Recording blindly would let this deploy replace an acceptance
 * another surface (or a newer web deploy) recorded at a later version. So it
 * reads the live grants first and applies the gate's own same-or-newer rule
 * (`isAtLeast`).
 *
 * If that read fails, it records nothing: a missing row is visibly absent and
 * the consent gate asks for it, while a blind write could downgrade one. Each
 * record is independent; a failed one is logged and the rest still go.
 */
export async function recordOAuthConsents(
  consentParam: string | null,
  loadActive: () => Promise<ActiveConsent[]>,
  record: (act: OAuthConsentAct) => Promise<void>,
): Promise<OAuthConsentAct[]> {
  const acts = oauthConsentActs(consentParam)
  if (acts.length === 0) return []

  let active: ActiveConsent[]
  try {
    active = await loadActive()
  } catch (err) {
    console.error("[auth/callback] consent read failed, recording nothing:", err)
    return []
  }

  const toRecord = acts.filter(
    (act) => !active.some((row) => row.kind === act.kind && isAtLeast(row.document_version, act.version)),
  )
  for (const act of toRecord) {
    try {
      await record(act)
    } catch (err) {
      console.error(`[auth/callback] record_consent (${act.kind}) failed:`, err)
    }
  }
  return toRecord
}
