import {
  CONSENT_DOCUMENT_VERSION,
  documentVersionFor,
  type ConsentKind,
} from "@/lib/config/consent"

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
