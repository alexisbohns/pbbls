"use client"

import { useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"

/**
 * Just the number of drafts, for the Path entry badge.
 *
 * Separate from `usePebbleDrafts` on purpose: /path is the app's home screen, and
 * fetching every draft's jsonb payload to render one integer is waste there. A
 * failure resolves to 0 — a missing badge beats an error state on the timeline.
 */
export function usePebbleDraftCount(): number {
  const { provider } = useDataProvider()
  const [count, setCount] = useState(0)

  useEffect(() => {
    if (!provider) return
    let cancelled = false
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      try {
        const n = await provider.countPebbleDrafts()
        if (!cancelled) setCount(n)
      } catch (err) {
        console.error("[drafts] count failed", err)
        if (!cancelled) setCount(0)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [provider])

  return count
}
