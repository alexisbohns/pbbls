"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { useTranslations } from "next-intl"
import { useNavItems } from "@/lib/hooks/useNavItems"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"

/**
 * The app's one navigation (#1384 D2), Android's `NavigationSuiteScaffold` on
 * the web: the five tabs as an M3 navigation bar at the foot of a narrow
 * window and as a rail on the start edge from the rail breakpoint (600 px,
 * Android's Medium width class). One component, two layouts, switched by CSS
 * only, so nothing flashes at hydration. It is chrome, so it always wears the
 * M3 roles (`.m3`), whatever ground the page has. The current tab is marked by
 * `aria-current` and by the `primaryContainer` indicator, as on Android.
 *
 * The shell renders it once, beside <main>, on the tab roots (`routeShell`).
 * `at` stands in for the route where the screen is shown elsewhere (the
 * sandbox's views).
 */
export function AppNavigation({ at }: { at?: string }) {
  const route = usePathname()
  const pathname = at ?? route
  const items = useNavItems()
  const t = useTranslations("common.aria")

  return (
    <nav
      aria-label={t("mainNavigation")}
      className="m3 fixed inset-x-0 bottom-0 z-40 bg-m3-surface pb-[var(--safe-area-bottom)] rail:static rail:h-full rail:w-24 rail:shrink-0 rail:pt-[calc(var(--safe-area-top)+2.75rem)] rail:pb-0"
    >
      <ul className="mx-auto flex h-16 max-w-xl rail:h-auto rail:flex-col rail:gap-3">
        {items.map(({ href, label, icon: Icon }) => {
          const current = pathname === href || pathname.startsWith(href + "/")
          return (
            <li key={href} className="min-w-0 flex-1 rail:flex-none">
              <Link
                href={href}
                aria-current={current ? "page" : undefined}
                className="group flex h-full min-h-11 flex-col items-center justify-center gap-1 px-1 text-m3-on-surface-variant outline-none aria-[current=page]:text-m3-primary rail:py-1"
              >
                <span className="flex h-8 w-14 items-center justify-center rounded-full transition-colors duration-200 group-hover:text-m3-on-surface group-focus-visible:ring-2 group-focus-visible:ring-m3-primary group-aria-[current=page]:bg-m3-primary-container group-aria-[current=page]:text-m3-on-primary-container motion-reduce:transition-none">
                  <Icon className={PBBLS_ICON_SIZE.toolbar} />
                </span>
                <span className="max-w-full truncate text-[0.75rem] leading-4 font-medium">{label}</span>
              </Link>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
