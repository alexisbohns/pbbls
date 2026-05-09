"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useTranslations } from "next-intl"
import { cn } from "@/lib/utils"
import { useNavItems } from "@/lib/hooks/useNavItems"

export function BottomNav() {
  const pathname = usePathname()
  const navItems = useNavItems()
  const t = useTranslations("common.aria")
  const hidden = pathname === "/" || pathname.startsWith("/record") || pathname.startsWith("/onboarding") || pathname === "/login" || pathname === "/register"

  return (
    <nav
      aria-label={t("mainNavigation")}
      aria-hidden={hidden}
      className={cn(
        "fixed inset-x-0 bottom-0 z-50 border-t border-border bg-background pb-[var(--safe-area-bottom)] md:hidden",
        "transition-transform duration-300 ease-in-out motion-reduce:transition-none",
        hidden && "translate-y-full",
      )}
    >
      <ul className="flex items-center justify-around py-2">
        {navItems.map(({ href, label, icon: Icon }) => {
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
