"use client"

import { useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type { SharedConnectionPebble } from "@/lib/types"

/**
 * The pebbles one connection shares with the viewer (M51). Fetched on screen
 * open like the connections list; `notFound` covers both a foreign and a
 * removed connection id (indistinguishable by design).
 */
export function useConnectionPebbles(connectionId: string) {
  const { provider } = useDataProvider()
  const [pebbles, setPebbles] = useState<SharedConnectionPebble[]>([])
  const [notFound, setNotFound] = useState(false)
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    if (!provider) return
    let cancelled = false
    // `await Promise.resolve()` defers the state updates past the synchronous
    // render boundary (mirrors useConnections) to satisfy
    // react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      try {
        const rows = await provider.listConnectionSharedPebbles(connectionId)
        if (cancelled) return
        if (rows === null) setNotFound(true)
        else setPebbles(rows)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error("[connection-pebbles] load failed", err)
        setError(err instanceof Error ? err : new Error("Failed to load shared pebbles"))
        setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [provider, connectionId])

  return { pebbles, notFound, loading, error }
}
