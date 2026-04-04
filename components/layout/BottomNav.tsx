"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { NAV_ITEMS } from "@/lib/config/navigation"

export function BottomNav() {
  const pathname = usePathname()
  const hidden = pathname === "/" || pathname.startsWith("/record") || pathname.startsWith("/onboarding") || pathname === "/login" || pathname === "/register"

  return (
    <nav
      aria-label="Main navigation"
      aria-hidden={hidden}
      className={cn(
        "fixed inset-x-0 bottom-0 z-50 border-t border-border bg-background pb-[var(--safe-area-bottom)] md:hidden",
        "transition-transform duration-300 ease-in-out motion-reduce:transition-none",
        hidden && "translate-y-full",
      )}
    >
      <ul className="flex items-center justify-around py-2">
        {NAV_ITEMS.map(({ href, label, icon: Icon }) => {
          const isActive = pathname.startsWith(href)
          return (
            <li key={href} className="flex-1 flex justify-center">
              <Link
                href={href}
                className={cn(
                  "flex flex-col items-center gap-0.5 px-3 py-1 text-xs font-medium transition-opacity duration-75 active:opacity-70",
                  isActive
                    ? "text-primary"
                    : "text-muted-foreground",
                )}
                aria-current={isActive ? "page" : undefined}
                tabIndex={hidden ? -1 : undefined}
              >
                <Icon className="size-5" />
                {label}
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
