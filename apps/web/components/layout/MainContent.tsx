"use client"

import { useEffect } from "react"
import { usePathname } from "next/navigation"
import { AuthGate } from "@/components/auth/AuthGate"
import { StoreGate } from "@/components/layout/StoreGate"
import { AppNavigation } from "@/components/layout/AppNavigation"
import { mainClass } from "@/components/layout/main-class"
import { OnboardingGate } from "@/components/onboarding/OnboardingGate"
import { PendingInviteRedirect } from "@/components/connections/PendingInviteRedirect"
import { ReconsentGate } from "@/components/consent/ReconsentGate"
import { useAuth } from "@/lib/data/auth-context"
import { routeShell } from "@/lib/config/app-routes"

interface MainContentProps {
  children: React.ReactNode
}

/**
 * The app shell below the providers. What a route gets (the navigation, the
 * M3 ground, full-screen, immersive, the gates) comes from one table,
 * `routeShell` (#1384). On a tab root the navigation sits beside <main> (a
 * rail) or over its foot (a bar), and <main> leaves it room through
 * `app-nav-room`, the one place that room is decided.
 */
export function MainContent({ children }: MainContentProps) {
  const pathname = usePathname()
  const shell = routeShell(pathname)
  const { isAuthenticated } = useAuth()
  const showNavigation = shell.tab && isAuthenticated

  // <body> carries the route's ground, so the overscroll, the safe areas and
  // portalled dialogs and toasts stand on it: the M3 roles (#1384, spec D3)
  // and, on the Path, the Spine paper (#1052), which is M3 `surface` there.
  useEffect(() => {
    document.body.classList.toggle("path-route", shell.path)
  }, [shell.path])

  useEffect(() => {
    document.body.classList.toggle("m3", shell.m3)
  }, [shell.m3])

  return (
    <>
      {showNavigation && <AppNavigation />}
      <main className={mainClass(shell, showNavigation)}>
        {shell.gated && <OnboardingGate />}
        {shell.gated && <PendingInviteRedirect />}
        <AuthGate>
          {/* Above the store shell and every route: while it blocks, nothing
              below is mounted, so no navigation reaches around it. */}
          <ReconsentGate>
            <StoreGate>{children}</StoreGate>
          </ReconsentGate>
        </AuthGate>
      </main>
    </>
  )
}
