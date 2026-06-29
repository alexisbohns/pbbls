"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { KarmaEvent, WalletSnapshot } from "@/lib/types"

const EMPTY: WalletSnapshot = { balance: 0, totalEarned: 0, totalSpent: 0 }

// Wallet history is fetched on demand (not part of the eager global store load).
export function useWallet() {
  const { provider } = useDataProvider()
  const [summary, setSummary] = useState<WalletSnapshot>(EMPTY)
  const [history, setHistory] = useState<KarmaEvent[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  // Start in the loading state only when a provider is available to load from;
  // without one (unauthenticated) there is nothing to fetch.
  const [loading, setLoading] = useState(() => provider !== null)
  const [loadingMore, setLoadingMore] = useState(false)
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
      try {
        const [s, page] = await Promise.all([
          provider.getWallet(),
          provider.getWalletHistory(),
        ])
        if (cancelled) return
        setSummary(s)
        setHistory(page.events)
        setCursor(page.nextCursor)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err : new Error("Failed to load wallet"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider])

  const loadMore = useCallback(async () => {
    if (!cursor || !provider || loadingMore) return
    setLoadingMore(true)
    try {
      const page = await provider.getWalletHistory(cursor)
      setHistory((prev) => [...prev, ...page.events])
      setCursor(page.nextCursor)
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Failed to load more wallet history"))
    } finally {
      setLoadingMore(false)
    }
  }, [provider, cursor, loadingMore])

  return {
    balance: summary.balance,
    totalEarned: summary.totalEarned,
    totalSpent: summary.totalSpent,
    history,
    hasMore: cursor !== null,
    loadMore,
    loadingMore,
    loading,
    error,
  }
}
