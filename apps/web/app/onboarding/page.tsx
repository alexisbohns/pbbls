"use client"

import { ConsentGate } from "@/components/onboarding/ConsentGate"
import { OnboardingStepper } from "@/components/onboarding/OnboardingStepper"
import { useAuth } from "@/lib/data/auth-context"
import { useConsents } from "@/lib/data/useConsents"

export default function OnboardingPage() {
  const { profile, isProfileLoading } = useAuth()
  const {
    healthData: healthConsent,
    loading: consentsLoading,
    record,
  } = useConsents()

  // An account can only reach onboarding WITHOUT a consent record by signing in
  // through the login page's OAuth buttons (F-2026-08-GDP-web-02): /register
  // gates all three of its paths on the checkbox.
  //
  // `profile &&` is load-bearing twice over. useConsents cannot tell a
  // signed-out visitor from a signed-in user who never consented — an owner
  // select returns zero rows either way — so without it a logged-out visitor
  // typing /onboarding would be shown a consent gate. And every pre-existing
  // account also lacks an active consent, while nothing stops one landing here
  // by bookmark (OnboardingGate deliberately does not police this route), so
  // testing onboarding_completed is what keeps the gate off the M55 cohort.
  if (consentsLoading || isProfileLoading) return null
  if (!healthConsent && profile && !profile.onboarding_completed) {
    return <ConsentGate onAccept={() => record("health_data", "web_oauth")} />
  }

  return <OnboardingStepper />
}
