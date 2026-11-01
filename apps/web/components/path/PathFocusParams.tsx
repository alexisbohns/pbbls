"use client"

import { useEffect } from "react"
import { useSearchParams } from "next/navigation"
import { usePathFocus } from "@/lib/hooks/usePathFocus"
import { parseWeekParam } from "@/lib/utils/week-param"

/**
 * Hands the Path's URL to its focus (#1384): `?focus=<momentId>` (R14, a
 * capture or a completion lands on its week) and `?week=<Monday>` (spec D9,
 * Home's week columns and wrap cards). Read with `useSearchParams` under the
 * Path tab's Suspense boundary: /path is prerendered as a static route. A
 * malformed week is ignored; `focus` wins when both are present.
 */
export function PathFocusParams() {
  const params = useSearchParams()
  const focus = params.get("focus")
  const week = params.get("week")
  const { applyParams } = usePathFocus()
  useEffect(() => {
    applyParams(focus !== null && focus !== "" ? focus : null, parseWeekParam(week))
  }, [focus, week, applyParams])
  return null
}
