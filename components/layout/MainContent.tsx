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
  const isRecord = pathname.startsWith("/record")
  const isPath = pathname === "/path"

  return (
    <main
      className={cn(
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden overflow-y-auto",
        isFullScreen
          ? "flex flex-col"
          : isRecord
            ? "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]"
            : isPath
              ? "pt-[var(--safe-area-top)] pb-[calc(2rem+var(--safe-area-bottom))]"
              : "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-[calc(2rem+var(--safe-area-bottom))]",
      )}
    >
      {!isLanding && !isAuth && <AuthGate />}
      {!isLanding && !isAuth && <OnboardingGate />}
      {isFullScreen ? (
        children
      ) : isPath ? (
        children
      ) : (
        <div className="mx-auto max-w-md">{children}</div>
      )}
    </main>
  )
}
