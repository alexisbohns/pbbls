"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { PebbleDraftPayload, PebbleDraftRecord } from "@/lib/data/data-provider"

/**
 * Server-side drafts (M47). Fetched on demand rather than joining the eager
 * global store: drafts are their own surface and every composer mount would
 * otherwise pay for them.
 *
 * Nothing here goes through `usePebbles.addPebble` — that diffs karma and pops
 * the karma pill, and a draft must earn zero karma.
 */
export function usePebbleDrafts() {
  const { provider } = useDataProvider()
  const [drafts, setDrafts] = useState<PebbleDraftRecord[]>([])
  // Only "loading" when there is a provider to load from; unauthenticated has
  // nothing to fetch.
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    if (!provider) return
    let cancelled = false

    // `await Promise.resolve()` defers the state updates past the synchronous
    // render boundary (mirrors useWallet.ts) to satisfy
    // react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      try {
        const rows = await provider.listPebbleDrafts()
        if (cancelled) return
        setDrafts(rows)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err : new Error("Failed to load drafts"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider])

  const saveDraft = useCallback(
    async (payload: PebbleDraftPayload, id?: string): Promise<PebbleDraftRecord> => {
      if (!provider) throw new Error("Not authenticated")
      const saved = await provider.savePebbleDraft(payload, id)
      setDrafts((prev) => [saved, ...prev.filter((d) => d.id !== saved.id)])
      return saved
    },
    [provider],
  )

  const removeDraft = useCallback(
    async (id: string): Promise<void> => {
      if (!provider) throw new Error("Not authenticated")
      await provider.deletePebbleDraft(id)
      setDrafts((prev) => prev.filter((d) => d.id !== id))
    },
    [provider],
  )

  return { drafts, loading, error, saveDraft, removeDraft }
}
