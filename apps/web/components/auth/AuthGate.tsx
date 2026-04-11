"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { useAuth } from "@/lib/data/auth-context"

const PROTECTED_PREFIXES = [
  "/path",
  "/record",
  "/pebble",
  "/collections",
  "/souls",
  "/glyphs",
  "/carve",
  "/profile",
]

interface AuthGateProps {
  children: React.ReactNode
}

export function AuthGate({ children }: AuthGateProps) {
  const pathname = usePathname()
  const router = useRouter()
  const { isAuthenticated, isLoading } = useAuth()

  const isProtected = PROTECTED_PREFIXES.some(
    (p) => pathname === p || pathname.startsWith(p + "/"),
  )

  useEffect(() => {
    if (isLoading) return
    if (!isAuthenticated && isProtected) {
      router.replace("/")
    }
  }, [pathname, router, isAuthenticated, isLoading, isProtected])

  // While auth is loading or redirect is in progress, show nothing on protected routes.
  if (isProtected && (isLoading || !isAuthenticated)) {
    return null
  }

  return <>{children}</>
}
