"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"

export function OnboardingGate() {
  const pathname = usePathname()
  const router = useRouter()
  const { profile, isLoading } = useAuth()

  useEffect(() => {
    if (isLoading) return
    if (pathname === "/" || pathname.startsWith("/onboarding") || pathname === "/login" || pathname === "/register") return
    if (profile && !profile.onboarding_completed) {
      router.replace("/onboarding")
    }
  }, [pathname, router, profile, isLoading])

  return null
}
