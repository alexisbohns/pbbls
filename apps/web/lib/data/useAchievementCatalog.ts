"use client"

import { useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { Achievement } from "@/lib/types"

/**
 * The public achievements catalog (#1308): titles and icons by slug for the
 * Profile shelf (D18). Read once per provider; a failed read is logged and
 * leaves the catalog empty, so the shelf draws what it can from its rows.
 */
export function useAchievementCatalog(): Achievement[] {
  const { provider } = useDataProvider()
  const [catalog, setCatalog] = useState<Achievement[]>([])
  useEffect(() => {
    if (!provider) return
    let cancelled = false
    provider
      .getAchievements()
      .then((rows) => {
        if (!cancelled) setCatalog(rows)
      })
      .catch((err: unknown) => {
        console.warn("[achievements] catalog read failed", err)
      })
    return () => {
      cancelled = true
    }
  }, [provider])
  return catalog
}
