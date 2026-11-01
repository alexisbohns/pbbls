"use client"

import { useCallback } from "react"
import { usePathname, useRouter } from "next/navigation"
import { routeShell } from "@/lib/config/app-routes"
import { usePreviousPathname } from "./usePreviousPathname"

/**
 * Closing a detail (#1384, two-area spec D5) returns to its tab. When the app
 * came here from that tab in this page load, the entry the detail pushed is
 * popped, so Back does not reopen what was closed; otherwise (a hard load, a
 * shared link, another detail before it) the tab replaces the detail.
 */
export function useDetailClose(): () => void {
  const pathname = usePathname()
  const router = useRouter()
  const previous = usePreviousPathname()
  const parent = routeShell(pathname).parentTab ?? "/home"
  return useCallback(() => {
    if (previous === parent) router.back()
    else router.replace(parent)
  }, [previous, parent, router])
}
