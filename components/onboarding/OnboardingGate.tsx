"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { isOnboardingCompleted } from "@/lib/hooks/useOnboarding"

export function OnboardingGate() {
  const pathname = usePathname()
  const router = useRouter()

  useEffect(() => {
    if (pathname === "/" || pathname.startsWith("/onboarding")) return
    if (!isOnboardingCompleted()) {
      router.replace("/onboarding")
    }
  }, [pathname, router])

  return null
}
