"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { NAV_ITEMS } from "@/lib/config/navigation"
import { ThemeToggle } from "@/components/layout/ThemeToggle"
import { HapticsToggle } from "@/components/layout/HapticsToggle"
import { ResetDataButton } from "@/components/layout/ResetDataButton"

export function Sidebar() {
  const pathname = usePathname()
  const hidden = pathname.startsWith("/onboarding")

  return (
    <aside className={cn("hidden w-56 shrink-0 border-r border-border md:flex md:flex-col", hidden && "md:hidden")}>
      <div className="flex h-14 items-center px-4 text-lg font-semibold">
        pbbls
      </div>

      <nav aria-label="Main navigation" className="flex-1 px-2 py-2">
        <ul className="flex flex-col gap-1">
          {NAV_ITEMS.map(({ href, label, icon: Icon, primary }) => {
            const isActive = pathname.startsWith(href)
            return (
              <li key={href}>
                <Link
                  href={href}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-muted text-foreground"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground",
                    primary && !isActive && "text-primary hover:text-primary",
                  )}
                  aria-current={isActive ? "page" : undefined}
                >
                  <Icon className="size-4" />
                  {label}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>

      <div className="flex items-center gap-1 border-t border-border px-4 py-3">
        <ResetDataButton />
        <ThemeToggle />
        <HapticsToggle />
      </div>
    </aside>
  )
}
