"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { PBBLS_ICON_SIZE } from "@/lib/icons/pbbls-icon"
import { directoryHref } from "./DesignContext"
import { currentPage, DIRECTORY_PAGES } from "./directory-pages"

/**
 * The directory's navigation, drawn like the app's own (`AppNavigation`):
 * an M3 navigation bar at the foot of a narrow window, a rail on the start
 * edge from the rail breakpoint (600 px), the current page marked by the
 * `primaryContainer` indicator. It is chrome, so it wears the M3 roles
 * whatever theme the page is set to.
 */
export function DesignRail({ themeKey }: { themeKey: string }) {
  const current = currentPage(usePathname())
  return (
    <nav
      aria-label="Design system"
      className="m3 fixed inset-x-0 bottom-0 z-40 bg-m3-surface pb-[var(--safe-area-bottom)] rail:sticky rail:top-0 rail:h-dvh rail:w-24 rail:shrink-0 rail:border-r rail:border-m3-outline-variant rail:pt-[calc(var(--safe-area-top)+1.25rem)] rail:pb-0"
    >
      <ul className="mx-auto flex h-16 max-w-xl rail:h-auto rail:flex-col rail:gap-3">
        {DIRECTORY_PAGES.map(({ path, label, icon: Icon }) => (
          <li key={path} className="min-w-0 flex-1 rail:flex-none">
            <Link
              href={directoryHref(path, themeKey)}
              aria-current={current?.path === path ? "page" : undefined}
              className="group flex h-full min-h-11 flex-col items-center justify-center gap-1 px-1 text-m3-on-surface-variant outline-none aria-[current=page]:text-m3-primary rail:py-1"
            >
              <span className="flex h-8 w-14 items-center justify-center rounded-full transition-colors duration-200 group-hover:text-m3-on-surface group-focus-visible:ring-2 group-focus-visible:ring-m3-primary group-aria-[current=page]:bg-m3-primary-container group-aria-[current=page]:text-m3-on-primary-container motion-reduce:transition-none">
                <Icon className={PBBLS_ICON_SIZE.toolbar} />
              </span>
              <span className="max-w-full truncate text-[0.75rem] leading-4 font-medium">{label}</span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  )
}
