"use client"

import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { AuthGate } from "@/components/auth/AuthGate"
import { OnboardingGate } from "@/components/onboarding/OnboardingGate"

interface MainContentProps {
  children: React.ReactNode
}

export function MainContent({ children }: MainContentProps) {
  const pathname = usePathname()
  const isLanding = pathname === "/"
  const isOnboarding = pathname.startsWith("/onboarding")
  const isAuth = pathname === "/login" || pathname === "/register"
  const isDocs = pathname.startsWith("/docs")
  const isFullScreen = isLanding || isOnboarding || isAuth
  const isRecord = pathname.startsWith("/record")
  const isCarve = pathname.startsWith("/carve")
  const isImmersive = isRecord || isCarve

  return (
    <main
      className={cn(
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden overflow-y-auto",
        isFullScreen
          ? "flex flex-col"
          : isImmersive
            ? "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]"
            : "pt-[var(--safe-area-top)] pb-[calc(2rem+var(--safe-area-bottom))]",
      )}
    >
      {!isLanding && !isAuth && !isDocs && <OnboardingGate />}
      <AuthGate>{children}</AuthGate>
    </main>
  )
}
