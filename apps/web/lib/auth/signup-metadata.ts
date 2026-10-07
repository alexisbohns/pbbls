import { documentVersionFor } from "@/lib/config/consent"
import type { RegisterInput } from "@/lib/types"

/**
 * The consent half of the email sign-up metadata. `handle_new_user`
 * (`20260927090000_consent_terms_privacy.sql`) reads exactly these keys and
 * turns each timestamp + version pair into a `user_consents` row. A key it does
 * not read records nothing, silently, so the shape is pinned by a test against
 * the trigger's key list and Android's payload (`SupabaseService.consentMetadata`).
 *
 * Sent as metadata rather than a post-signup RPC because there is no session
 * yet when email confirmations are on, so a client call would be lost.
 */
export type SignupConsentMetadata = {
  terms_accepted_at: string | null
  terms_version: string | null
  privacy_accepted_at: string | null
  privacy_version: string | null
  health_data_consent_at: string | null
  health_data_consent_version: string | null
  age_attested_at: string | null
  age_attestation_version: string | null
  /**
   * Provenance for the trigger's `source` mapping. Sent explicitly so
   * 'web_register' is a stated fact rather than the fallback branch:
   * handle_new_user maps this through a closed `case` whose default is web.
   */
  signup_surface: "web"
}

type SignupConsents = Pick<
  RegisterInput,
  "terms_accepted" | "privacy_accepted" | "health_data_consent" | "age_attested"
>

/**
 * ISO 8601 at whole seconds, e.g. `2026-09-27T10:00:00Z`. Truncated, not
 * rounded, and with no fractional part at all: the narrowest precision every
 * reader of a cross-surface timestamp accepts (root CLAUDE.md), and the exact
 * form Android's `signupInstant` emits.
 */
export function wholeSecondIso(date: Date): string {
  return date.toISOString().replace(/\.\d+Z$/, "Z")
}

/**
 * Every act is stamped with the one sign-up instant. An unticked act sends an
 * explicit null for both its timestamp and its version, so the trigger skips
 * it; /register cannot submit without all four ticked, so in practice every
 * key is set.
 */
export function signupConsentMetadata(
  input: SignupConsents,
  now: Date,
): SignupConsentMetadata {
  const at = wholeSecondIso(now)
  return {
    terms_accepted_at: input.terms_accepted ? at : null,
    terms_version: input.terms_accepted ? documentVersionFor("terms") : null,
    privacy_accepted_at: input.privacy_accepted ? at : null,
    privacy_version: input.privacy_accepted ? documentVersionFor("privacy") : null,
    health_data_consent_at: input.health_data_consent ? at : null,
    health_data_consent_version: input.health_data_consent ? documentVersionFor("health_data") : null,
    age_attested_at: input.age_attested ? at : null,
    age_attestation_version: input.age_attested ? documentVersionFor("age_assurance") : null,
    signup_surface: "web",
  }
}
