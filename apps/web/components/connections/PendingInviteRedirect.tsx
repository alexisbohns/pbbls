"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"
import { usePendingInvite } from "@/lib/hooks/usePendingInvite"

/**
 * Closes the sign-up-first funnel (M49, design D12): once auth AND onboarding
 * are complete and a pending invite token exists, route the user back to
 * `/invite/<token>` for the explicit accept tap. This component only
 * redirects — the token is cleared by `AcceptInvite` (on accept, terminal
 * error, or once an onboarded user has seen the page), never here, so an
 * un-onboarded user keeps their token through the whole onboarding flow.
 */
export function PendingInviteRedirect() {
  const pathname = usePathname()
  const router = useRouter()
  const { isAuthenticated, isLoading, isProfileLoading, profile } = useAuth()
  const { token } = usePendingInvite()

  useEffect(() => {
    if (isLoading || isProfileLoading) return
    if (!isAuthenticated || !profile?.onboarding_completed) return
    if (!token) return
    if (pathname.startsWith("/invite")) return
    router.replace(`/invite/${token}`)
  }, [pathname, router, token, isAuthenticated, isLoading, isProfileLoading, profile])

  return null
}
