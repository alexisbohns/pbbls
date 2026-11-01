"use client"

import type { ReactNode } from "react"
import { usePathname } from "next/navigation"
import { routeShell } from "@/lib/config/app-routes"

/**
 * A tab's page in its route group's layout (#1384, two-area spec D2): shown on
 * the tab root and on every detail of that tab, so it stays mounted (state,
 * scroll) while a detail opens beside it or over it. On any other route of
 * the group (a page not moved into the detail slot yet) it steps aside and
 * the route's own page shows.
 */
export function TabArea({ tab, children }: { tab: string; children: ReactNode }) {
  const pathname = usePathname()
  return routeShell(pathname).parentTab === tab ? children : null
}
