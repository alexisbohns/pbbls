"use client"

import { useEffect } from "react"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { AuthGate } from "@/components/auth/AuthGate"
import { StoreGate } from "@/components/layout/StoreGate"
import { OnboardingGate } from "@/components/onboarding/OnboardingGate"
import { PendingInviteRedirect } from "@/components/connections/PendingInviteRedirect"
import { ReconsentGate } from "@/components/consent/ReconsentGate"

interface MainContentProps {
  children: React.ReactNode
}

export function MainContent({ children }: MainContentProps) {
  const pathname = usePathname()
  const isLanding = pathname === "/"
  const isOnboarding = pathname.startsWith("/onboarding")
  const isAuth = pathname === "/login" || pathname === "/register"
  const isDocs = pathname.startsWith("/docs")
  // Public profile pages serve anonymous visitors and signed-in users alike —
  // never bounce a mid-onboarding viewer away from someone's public page.
  const isPublicProfile = pathname.startsWith("/u/")
  // The blog (#1209) is public reading, set edge-to-edge on its own paper:
  // like a public profile, never bounce a mid-onboarding reader away from it.
  const isBlog = pathname === "/blog" || pathname.startsWith("/blog/")
  // /path owns its own sticky bottom dock and scrollable interior — it should
  // fill the dynamic viewport edge-to-edge, with no body-level scrolling.
  const isPath = pathname === "/path" || pathname.startsWith("/path/")
  const isFullScreen = isLanding || isOnboarding || isAuth || isPath || isBlog
  const isRecord = pathname.startsWith("/record")
  const isCarve = pathname.startsWith("/carve")
  const isImmersive = isRecord || isCarve
  // Home and the guides wear the brand M3 roles (#1384, spec D3): their layout
  // renders an .m3 island, and <body> carries it too, so the overscroll, the
  // safe areas and portalled dialogs and toasts stand on the same ground.
  const isM3 = pathname === "/home" || pathname.startsWith("/guides/")

  // Body bg is normally --background (brand-light); /path is set on the
  // Spine's paper. Toggle a route-aware class on <body> so the ground fills
  // the whole viewport (including safe-area insets and under the dock).
  useEffect(() => {
    if (typeof document === "undefined") return
    document.body.classList.toggle("path-route", isPath)
  }, [isPath])

  useEffect(() => {
    if (typeof document === "undefined") return
    document.body.classList.toggle("m3", isM3)
  }, [isM3])

  return (
    <main
      className={cn(
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden",
        isPath ? "overflow-y-hidden" : "overflow-y-auto",
        isFullScreen
          ? "flex flex-col"
          : isImmersive
            ? "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]"
            : "pt-[var(--safe-area-top)] pb-[calc(2rem+var(--safe-area-bottom))]",
      )}
    >
      {!isLanding && !isAuth && !isDocs && !isPublicProfile && !isBlog && <OnboardingGate />}
      {!isLanding && !isAuth && !isDocs && !isPublicProfile && !isBlog && <PendingInviteRedirect />}
      <AuthGate>
        {/* Above the store shell and every route: while it blocks, nothing
            below is mounted, so no navigation reaches around it. */}
        <ReconsentGate>
          <StoreGate>{children}</StoreGate>
        </ReconsentGate>
      </AuthGate>
    </main>
  )
}
