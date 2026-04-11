"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"

export function OnboardingGate() {
  const pathname = usePathname()
  const router = useRouter()
  const { profile, isAuthenticated, isLoading } = useAuth()

  useEffect(() => {
    if (isLoading) return
    if (pathname === "/" || pathname.startsWith("/onboarding") || pathname === "/login" || pathname === "/register" || pathname.startsWith("/docs")) return
    if (isAuthenticated && (!profile || !profile.onboarding_completed)) {
      router.replace("/onboarding")
    }
  }, [pathname, router, profile, isAuthenticated, isLoading])

  return null
}
