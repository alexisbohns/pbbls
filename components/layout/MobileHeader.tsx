"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { CircleUser } from "lucide-react"
import { cn } from "@/lib/utils"
import { useAuth } from "@/lib/data/auth-context"

export function MobileHeader() {
  const pathname = usePathname()
  const { isAuthenticated } = useAuth()
  const hidden =
    pathname.startsWith("/record") || pathname.startsWith("/onboarding")

  return (
    <header
      aria-hidden={hidden}
      className={cn(
        "fixed inset-x-0 top-0 z-50 flex h-12 items-center justify-between bg-background px-4 pt-[var(--safe-area-top)] md:hidden",
        hidden && "hidden",
      )}
    >
      <span className="text-lg font-semibold">pbbls</span>

      {isAuthenticated && (
        <Link
          href="/profile"
          className={cn(
            "flex items-center justify-center rounded-lg p-2 transition-colors",
            pathname.startsWith("/profile")
              ? "text-primary"
              : "text-muted-foreground hover:text-foreground",
          )}
          aria-label="Profile"
          aria-current={pathname.startsWith("/profile") ? "page" : undefined}
        >
          <CircleUser className="size-5" />
        </Link>
      )}
    </header>
  )
}
