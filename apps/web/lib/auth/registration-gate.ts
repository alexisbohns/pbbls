/** The four acceptances /register requires before an account can be created. */
export type ConsentChecks = {
  terms: boolean
  privacy: boolean
  /** Art. 9 explicit consent — a separate act, not a document acknowledgement. */
  healthData: boolean
  /**
   * The 16+ minimum-age attestation. An attestation, not a verification: the
   * person states they are old enough and we record that statement. It is
   * deliberately not a birthdate — a date of birth would be more personal data
   * to hold for a question whose only answer we need is yes or no.
   */
  age: boolean
}

/**
 * Whether registration may proceed. Shared by the email submit button and both
 * OAuth buttons: before this existed the OAuth buttons were gated on
 * `submitting` alone, so an OAuth account was created with no consent record at
 * all (Kritik F-2026-08-GDP-web-02).
 */
export function canSubmitRegistration(checks: ConsentChecks): boolean {
  return checks.terms && checks.privacy && checks.healthData && checks.age
}
