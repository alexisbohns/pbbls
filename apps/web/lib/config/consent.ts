/**
 * The privacy-policy version the consent copy on /register describes.
 *
 * Bump this in the SAME commit as the policy's `version:` frontmatter —
 * `consent.test.ts` fails otherwise. A consent row records the version of the
 * document the person actually read, so an older row citing an older version is
 * correct, not stale.
 */
export const CONSENT_DOCUMENT_VERSION = "1.2.0"

/** The consent kinds `user_consents.kind` accepts. */
export const CONSENT_KINDS = ["health_data", "public_profile", "age_assurance"] as const
export type ConsentKind = (typeof CONSENT_KINDS)[number]

/**
 * The kinds `withdraw_consent` accepts. `age_assurance` is absent on purpose:
 * you cannot un-attest your age, so the RPC's own allowlist refuses it with
 * `invalid_kind` and a `user_consents_age_not_withdrawable` CHECK refuses it
 * structurally. This type refuses it at compile time too.
 */
export type WithdrawableConsentKind = Exclude<ConsentKind, "age_assurance">

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
