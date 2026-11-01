"use client"

import { useCallback } from "react"
import { usePathname, useRouter } from "next/navigation"
import { routeShell } from "@/lib/config/app-routes"
import { useLastTab } from "./useLastTab"
import { usePreviousPathname } from "./usePreviousPathname"

/**
 * Closing a detail (#1384, two-area spec D5). Opened from inside the app (this
 * page load has a previous route), it pops the entry it pushed, so Back does
 * not reopen what was closed and a pebble opened from a soul returns to the
 * soul. Opened by a hard load or a shared link, its tab replaces it.
 */
export function useDetailClose(): () => void {
  const pathname = usePathname()
  const router = useRouter()
  const previous = usePreviousPathname()
  const lastTab = useLastTab()
  const shell = routeShell(pathname)
  // A pebble closes onto the tab it opened over.
  const parent = (shell.overAnyTab ? lastTab : null) ?? shell.parentTab ?? "/home"
  return useCallback(() => {
    if (previous !== null) router.back()
    else router.replace(parent)
  }, [previous, parent, router])
}
