"use client"

import { useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { RippleSummary } from "@/lib/types"

const EMPTY: RippleSummary = { level: 0, activeToday: false, pebbles28d: 0 }

// Ripple summary (v_ripple) is fetched on demand — it's not part of the eager
// global store load. Mirrors the useWallet fetch pattern.
export function useRipple() {
  const { provider } = useDataProvider()
  const [ripple, setRipple] = useState<RippleSummary>(EMPTY)
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    if (!provider) return
    let cancelled = false

    // Defer past the synchronous render boundary (mirrors useWallet.ts) to
    // satisfy react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      try {
        const data = await provider.getRipple()
        if (cancelled) return
        setRipple(data)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err : new Error("Failed to load ripple"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider])

  return { ripple, loading, error }
}
