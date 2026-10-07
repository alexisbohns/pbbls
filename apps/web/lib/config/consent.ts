/**
 * The privacy-policy version the consent copy on /register describes.
 *
 * Bump this in the SAME commit as the policy's `version:` frontmatter —
 * `consent.test.ts` fails otherwise. A consent row records the version of the
 * document the person actually read, so an older row citing an older version is
 * correct, not stale.
 */
export const CONSENT_DOCUMENT_VERSION = "1.4.0"

/**
 * The Terms of Service version a `terms` act is recorded against. Pinned to the
 * `docs/terms/*.md` frontmatter by `consent.test.ts`, as the privacy version is.
 * Android carries the same value as `LegalVersions.TERMS`.
 */
export const TERMS_DOCUMENT_VERSION = "1.2.0"

/** The consent kinds `user_consents.kind` accepts. */
export const CONSENT_KINDS = [
  "health_data",
  "public_profile",
  "age_assurance",
  "terms",
  "privacy",
] as const
export type ConsentKind = (typeof CONSENT_KINDS)[number]

/**
 * The kinds `withdraw_consent` accepts. `age_assurance`, `terms` and `privacy`
 * are absent on purpose: you cannot un-attest your age, and accepting the Terms
 * or the policy ends with the account rather than with a toggle. The RPC's own
 * allowlist refuses them with `invalid_kind` and the
 * `user_consents_not_withdrawable` CHECK refuses them structurally. This type
 * refuses them at compile time too.
 */
export type WithdrawableConsentKind = Exclude<ConsentKind, "age_assurance" | "terms" | "privacy">

/**
 * The document version this build records `kind` against. Terms cite the Terms;
 * every other act cites the privacy policy, which is where health data, the age
 * minimum and public profiles are described. Same split as Android's
 * `ConsentGateLogic.versionFor`.
 */
export function documentVersionFor(kind: ConsentKind): string {
  return kind === "terms" ? TERMS_DOCUMENT_VERSION : CONSENT_DOCUMENT_VERSION
}

/**
 * Where a consent act was collected. Mirrors the `user_consents.source` CHECK,
 * which is surface-neutral because iOS and Android will use the same RPCs.
 */
export type ConsentSource =
  | "web_register"
  | "web_oauth"
  | "web_settings"
  | "ios_register"
  | "ios_oauth"
  | "ios_settings"
  | "android_register"
  | "android_oauth"
  | "android_settings"
