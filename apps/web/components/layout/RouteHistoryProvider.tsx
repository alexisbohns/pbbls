"use client"

import { useState, type ReactNode } from "react"
import { usePathname } from "next/navigation"
import { PreviousPathnameContext } from "@/lib/hooks/usePreviousPathname"

/** Remembers the previous pathname of this page load for `usePreviousPathname`. */
export function RouteHistoryProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname()
  // Adjusted during render when the pathname changes, so the value is right
  // on the render that shows the new route.
  const [history, setHistory] = useState<{ current: string; previous: string | null }>({ current: pathname, previous: null })
  if (history.current !== pathname) setHistory({ current: pathname, previous: history.current })
  return <PreviousPathnameContext value={history.previous}>{children}</PreviousPathnameContext>
}
