"use client"

import { usePathname } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { useConsentGate } from "@/lib/data/useConsentGate"
import { gateApplies, isGateExemptPath, isGatePublicPath } from "@/lib/auth/consent-gate"
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
 * invite gets around it. It fails closed: while deciding, and when the ledger
 * cannot be read, the route is not rendered either. The one exception is a
 * public route while sign-in is still loading: signed-out visitors read those
 * pages anyway, so the server-rendered HTML is the page, and the gate takes
 * over once a signed-in user who still owes consent is known.
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
  // as signed out: on a route that needs sign-in the gate holds its loading
  // state until auth knows. Public routes render meanwhile.
  const applies = exempt
    ? "skip"
    : gateApplies({ isLoading, isAuthenticated, isProfileLoading, profile })
  const { state, submit, retry } = useConsentGate(
    user?.id ?? null,
    applies,
    isGatePublicPath(pathname),
  )

  if (state.status === "open") return <>{children}</>
  return <ReconsentScreen state={state} onSubmit={submit} onRetry={retry} />
}
