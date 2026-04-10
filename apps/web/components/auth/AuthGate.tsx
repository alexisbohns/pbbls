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

export function AuthGate() {
  const pathname = usePathname()
  const router = useRouter()
  const { isAuthenticated, isLoading } = useAuth()

  useEffect(() => {
    if (isLoading) return
    if (!isAuthenticated && PROTECTED_PREFIXES.some((p) => pathname === p || pathname.startsWith(p + "/"))) {
      router.replace("/")
    }
  }, [pathname, router, isAuthenticated, isLoading])

  return null
}
