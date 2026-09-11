/** The three acceptances /register requires before an account can be created. */
export type ConsentChecks = {
  terms: boolean
  privacy: boolean
  /** Art. 9 explicit consent — a separate act, not a document acknowledgement. */
  healthData: boolean
}

/**
 * Whether registration may proceed. Shared by the email submit button and both
 * OAuth buttons: before this existed the OAuth buttons were gated on
 * `submitting` alone, so an OAuth account was created with no consent record at
 * all (Kritik F-2026-08-GDP-web-02).
 */
export function canSubmitRegistration(checks: ConsentChecks): boolean {
  return checks.terms && checks.privacy && checks.healthData
}
