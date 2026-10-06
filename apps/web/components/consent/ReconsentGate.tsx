"use client"

import { usePathname } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { useConsentGate } from "@/lib/data/useConsentGate"
import { gateApplies, isGateExemptPath } from "@/lib/auth/consent-gate"
import { ReconsentScreen } from "@/components/consent/ReconsentScreen"

type ReconsentGateProps = {
  children: React.ReactNode
}

/**
 * The post-auth consent gate (#788), web's mirror of Android's #967.
 *
 * Wraps every route's content and, while the signed-in user's ledger lacks a
 * required act at a current (or newer) version, renders the consent screen
 * INSTEAD of the route. Replacing rather than overlaying means nothing behind
 * it is mounted to be reached: no deep link, bookmark, restored tab or parked
 * invite gets around it. It fails closed: while deciding (sign-in still
 * loading included), and when the ledger cannot be read, the route is not
 * rendered either.
 *
 * Only the legal documents stay reachable, so the Terms and Privacy links on
 * the screen can open them. Accounts mid-onboarding are left to onboarding's
 * own ConsentGate and reach this one when they finish.
 */
export function ReconsentGate({ children }: ReconsentGateProps) {
  const pathname = usePathname()
  const { user, profile, isLoading, isAuthenticated, isProfileLoading } = useAuth()
  const exempt = isGateExemptPath(pathname)
  // While the session check runs there is no user yet, which is not the same
  // as signed out: the gate holds its loading state on every route it wraps
  // (protected or not) until auth knows. A signed-out visitor sees the page
  // as soon as it does.
  const applies = exempt
    ? "skip"
    : gateApplies({ isLoading, isAuthenticated, isProfileLoading, profile })
  const { state, submit, retry } = useConsentGate(user?.id ?? null, applies)

  if (state.status === "open") return <>{children}</>
  return <ReconsentScreen state={state} onSubmit={submit} onRetry={retry} />
}
