"use client"

import { ConsentGate } from "@/components/onboarding/ConsentGate"
import { OnboardingStepper } from "@/components/onboarding/OnboardingStepper"
import { useAuth } from "@/lib/data/auth-context"
import { useConsents } from "@/lib/data/useConsents"

export default function OnboardingPage() {
  const { profile, isProfileLoading, isAuthenticated } = useAuth()
  const {
    healthData: healthConsent,
    loading: consentsLoading,
    record,
  } = useConsents()

  // An account can only reach onboarding WITHOUT a consent record by signing in
  // through the login page's OAuth buttons (F-2026-08-GDP-web-02): /register
  // gates all three of its paths on the checkbox.
  //
  // Each clause does exactly one job, and conflating them is how this gate
  // failed open once already:
  //
  //   isAuthenticated — useConsents cannot tell a signed-out visitor from a
  //     signed-in user who never consented (an owner select returns zero rows
  //     either way), so without this a logged-out visitor typing /onboarding
  //     would be shown a consent gate.
  //
  //   !profile?.onboarding_completed — keeps the gate off the M55 re-consent
  //     cohort, who lack a consent row but finished onboarding long ago and can
  //     land here by bookmark (OnboardingGate deliberately does not police this
  //     route inward).
  //
  // The optional chaining is deliberate and fails CLOSED. When the profile
  // fetch failed or timed out, `profile` is null and we cannot tell a brand-new
  // account from the M55 cohort — so we ask. Rendering the stepper instead
  // would let the account complete onboarding, which upserts the profile with
  // onboarding_completed = true, and the gate would never show again: an
  // account permanently past an Art. 9 gate with no consent row. Asking an
  // established user to re-confirm on a degraded network is a far smaller harm
  // than that, and record_consent is idempotent so a needless grant is a no-op.
  if (consentsLoading || isProfileLoading) return null
  if (!healthConsent && isAuthenticated && !profile?.onboarding_completed) {
    return <ConsentGate onAccept={() => record("health_data", "web_oauth")} />
  }

  return <OnboardingStepper />
}
