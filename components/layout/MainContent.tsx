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
  const isFullScreen = isLanding || isOnboarding || isAuth
  const hideBottomNav = pathname.startsWith("/record") || isFullScreen

  return (
    <main
      className={cn(
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden overflow-y-auto",
        "transition-[padding-bottom] duration-300 ease-in-out motion-reduce:transition-none",
        isFullScreen
          ? "flex flex-col"
          : "px-4 pt-[calc(4rem+var(--safe-area-top))] md:pt-[calc(2rem+var(--safe-area-top))] pb-8 md:pb-8",
        !isFullScreen && (
          hideBottomNav
            ? "pb-[calc(2rem+var(--safe-area-bottom))]"
            : "pb-[calc(5rem+var(--safe-area-bottom))]"
        ),
      )}
    >
      {!isLanding && !isAuth && <AuthGate />}
      {!isLanding && !isAuth && <OnboardingGate />}
      {isFullScreen ? children : <div className="mx-auto max-w-5xl">{children}</div>}
    </main>
  )
}
