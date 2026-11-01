"use client"

import { useState, type ReactNode } from "react"
import { usePathname } from "next/navigation"
import { routeShell } from "@/lib/config/app-routes"
import { LastTabContext } from "@/lib/hooks/useLastTab"
import { PreviousPathnameContext } from "@/lib/hooks/usePreviousPathname"

type History = { current: string; previous: string | null; lastTab: string | null }

/** The tab a route shows on the left, unless it opens over whichever tab was showing. */
function tabOf(pathname: string): string | null {
  const shell = routeShell(pathname)
  return shell.overAnyTab ? null : shell.parentTab
}

/**
 * Remembers this page load's previous pathname (`usePreviousPathname`) and
 * the last tab shown (`useLastTab`, the one a pebble opens over).
 */
export function RouteHistoryProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  // Adjusted during render when the pathname changes, so the values are right
  // on the render that shows the new route.
  const [history, setHistory] = useState<History>(() => ({ current: pathname, previous: null, lastTab: tabOf(pathname) }))
  if (history.current !== pathname) {
    setHistory({ current: pathname, previous: history.current, lastTab: tabOf(pathname) ?? history.lastTab })
  }
  return (
    <PreviousPathnameContext value={history.previous}>
      <LastTabContext value={history.lastTab}>{children}</LastTabContext>
    </PreviousPathnameContext>
  )
}
