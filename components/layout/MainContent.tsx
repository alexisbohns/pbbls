"use client"

import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { OnboardingGate } from "@/components/onboarding/OnboardingGate"

interface MainContentProps {
  children: React.ReactNode
}

export function MainContent({ children }: MainContentProps) {
  const pathname = usePathname()
  const isOnboarding = pathname.startsWith("/onboarding")
  const hideBottomNav = pathname.startsWith("/record") || isOnboarding

  return (
    <main
      className={cn(
        "min-w-0 flex-1 touch-pan-y overflow-x-hidden overflow-y-auto",
        "transition-[padding-bottom] duration-300 ease-in-out motion-reduce:transition-none",
        isOnboarding
          ? "flex flex-col"
          : "px-4 pt-[calc(2rem+var(--safe-area-top))] pb-8 md:pb-8",
        !isOnboarding && (
          hideBottomNav
            ? "pb-[calc(2rem+var(--safe-area-bottom))]"
            : "pb-[calc(5rem+var(--safe-area-bottom))]"
        ),
      )}
    >
      <OnboardingGate />
      {isOnboarding ? children : <div className="mx-auto max-w-5xl">{children}</div>}
    </main>
  )
}
