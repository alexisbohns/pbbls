"use client"

import { useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { Achievement } from "@/lib/types"

// Achievements are fetched on demand (not part of the eager global store load).
// Opening the screen FIRST fires check_achievements() — the screen-open call IS
// the retroactive grant (D5): a veteran's first visit unlocks their whole
// history in one call — and only THEN reads catalog + unlocks, so fresh grants
// are already in the ledger the grid renders from.
export function useAchievements() {
  const { provider } = useDataProvider()
  const [achievements, setAchievements] = useState<Achievement[]>([])
  // Unlocked achievement id → unlocked_at timestamp.
  const [unlockedAt, setUnlockedAt] = useState<Map<string, string>>(new Map())
  // Start in the loading state only when a provider is available to load from;
  // without one (unauthenticated) there is nothing to fetch.
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    if (!provider) return
    let cancelled = false

    // The `await Promise.resolve()` defers state updates past the synchronous
    // render boundary (mirrors useLab.ts) to satisfy react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      // Evaluation failures are non-fatal: the reads below still render the
      // current state, and the next check self-heals by construction (D5).
      try {
        await provider.checkAchievements()
      } catch (err) {
        console.warn("[achievements] check_achievements failed", err)
      }
      if (cancelled) return
      try {
        const [catalog, unlocks] = await Promise.all([
          provider.getAchievements(),
          provider.getAchievementUnlocks(),
        ])
        if (cancelled) return
        setAchievements(catalog)
        setUnlockedAt(new Map(unlocks.map((u) => [u.achievementId, u.unlockedAt])))
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err : new Error("Failed to load achievements"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider])

  return { achievements, unlockedAt, loading, error }
}
