"use client"

import { useEffect } from "react"
import { usePathname, useSelectedLayoutSegment } from "next/navigation"
import { AuthGate } from "@/components/auth/AuthGate"
import { StoreGate } from "@/components/layout/StoreGate"
import { AppNavigation } from "@/components/layout/AppNavigation"
import { RightArea } from "@/components/layout/RightArea"
import { RouteHistoryProvider } from "@/components/layout/RouteHistoryProvider"
import { mainClass } from "@/components/layout/main-class"
import { OnboardingGate } from "@/components/onboarding/OnboardingGate"
import { PendingInviteRedirect } from "@/components/connections/PendingInviteRedirect"
import { ReconsentGate } from "@/components/consent/ReconsentGate"
import { PathFocusProvider } from "@/components/path/PathFocusProvider"
import { StoneSprite } from "@/components/pebble/stone/StoneSprite"
import { useAuth } from "@/lib/data/auth-context"
import { PANES_QUERY, routeShell } from "@/lib/config/app-routes"
import { useMediaQuery } from "@/lib/hooks/useMediaQuery"
import { cn } from "@/lib/utils"

interface MainContentProps {
  children: React.ReactNode
  /** The `@detail` slot (#1384, two-area spec D2). */
  detail: React.ReactNode
}

/**
 * The app shell below the providers. What a route gets (the navigation, the
 * M3 ground, full-screen, immersive, the gates, its detail) comes from one
 * table, `routeShell` (#1384).
 *
 * On a tab or a detail (two-area spec D1): the navigation, the left area
 * (<main>: the tab's page, from its route group's layout) and the right area
 * (the Path's line from 1024 px, under the `@detail` slot). Below 1024 px the
 * right area exists only while a detail is open, as the overlay; the left
 * area under it is inert and does not scroll. The Path's focus and the
 * stones' sprite sit above both areas, so the verso on the left and the line
 * on the right share one week and one set of symbols.
 */
export function MainContent({ children, detail }: MainContentProps) {
  const pathname = usePathname()
  const shell = routeShell(pathname)
  const { isAuthenticated } = useAuth()
  const showNavigation = (shell.tab || shell.detail) && isAuthenticated
  const wide = useMediaQuery(PANES_QUERY)
  // The slot's details live in its `(pane)` group; its null pages do not.
  const detailOpen = useSelectedLayoutSegment("detail") === "(pane)"
  const overlay = detailOpen && !wide

  // <body> carries the route's ground, so the overscroll, the safe areas and
  // portalled dialogs and toasts stand on it: the M3 roles (#1384, spec D3)
  // and, on the Path, the Spine paper (#1052), which is M3 `surface` there.
  useEffect(() => {
    document.body.classList.toggle("path-route", shell.path)
  }, [shell.path])

  useEffect(() => {
    document.body.classList.toggle("m3", shell.m3)
  }, [shell.m3])

  const gated = (content: React.ReactNode) => (
    <AuthGate>
      {/* Above the store shell and every route: while it blocks, nothing
          below is mounted, so no navigation reaches around it. */}
      <ReconsentGate>
        <StoreGate>{content}</StoreGate>
      </ReconsentGate>
    </AuthGate>
  )

  return (
    <RouteHistoryProvider>
      <PathFocusProvider>
        {showNavigation && <AppNavigation />}
        <main inert={overlay} className={cn(mainClass(shell, showNavigation), overlay && "overflow-y-hidden")}>
          {shell.gated && <OnboardingGate />}
          {shell.gated && <PendingInviteRedirect />}
          {gated(children)}
        </main>
        {showNavigation && <RightArea wide={wide} detailOpen={detailOpen} detail={gated(detail)} />}
        {showNavigation && <StoneSprite />}
      </PathFocusProvider>
    </RouteHistoryProvider>
  )
}
