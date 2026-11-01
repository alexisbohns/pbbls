"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { ProfileEngagement } from "@/lib/types"

const EMPTY: ProfileEngagement = { daysPracticed: 0, assiduity: [], glyphsCarved: null, glyphsOwned: null }

// Days-practiced + 28-day assiduity (get_profile_engagement RPC), fetched on
// demand and bucketed in the caller's local timezone. Mirrors useWallet.ts.
export function useProfileEngagement() {
  const { provider } = useDataProvider()
  const [engagement, setEngagement] = useState<ProfileEngagement>(EMPTY)
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!provider) return
    let cancelled = false

    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      try {
        const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"
        const data = await provider.getProfileEngagement(tz)
        if (cancelled) return
        setEngagement(data)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error("[you] get_profile_engagement failed", err)
        setError(
          err instanceof Error ? err : new Error("Failed to load engagement"),
        )
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider, attempt])

  // You's counters offer a retry after a failed read (#1384 D23).
  const retry = useCallback(() => setAttempt((n) => n + 1), [])

  return { engagement, loading, error, retry }
}
