"use client"

import { useCallback, useEffect, useState } from "react"
import { useDataProvider } from "@/lib/data/provider-context"
import type {
  AcceptConnectionInviteResult,
  ConnectionInvite,
} from "@/lib/data/data-provider"
import type { Connection } from "@/lib/types"

/**
 * Mutual connections (M49). Fetched on demand rather than joining the eager
 * global store: connections are their own surface, refreshed on screen open —
 * no push, no realtime.
 *
 * Nothing here touches karma (design D9): the social graph must not be
 * karma-farmable, and none of the five connection RPCs emit events.
 */
export function useConnections() {
  const { provider } = useDataProvider()
  const [connections, setConnections] = useState<Connection[]>([])
  // Only "loading" when there is a provider to load from; unauthenticated has
  // nothing to fetch.
  const [loading, setLoading] = useState(() => provider !== null)
  const [error, setError] = useState<Error | null>(null)

  // Invite creation is a WRITE (it mints a token), so consumers must gate it
  // on `ready` — calling before the provider exists throws.
  const ready = provider !== null

  useEffect(() => {
    if (!provider) return
    let cancelled = false

    // `await Promise.resolve()` defers the state updates past the synchronous
    // render boundary (mirrors usePebbleDrafts) to satisfy
    // react-hooks/set-state-in-effect.
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError(null)
      try {
        const rows = await provider.listConnections()
        if (cancelled) return
        setConnections(rows)
        setLoading(false)
      } catch (err) {
        if (cancelled) return
        console.error("[connections] load failed", err)
        setError(err instanceof Error ? err : new Error("Failed to load connections"))
        setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [provider])

  const createInvite = useCallback(
    async (rotate = false): Promise<ConnectionInvite> => {
      if (!provider) throw new Error("Not authenticated")
      return provider.createConnectionInvite(rotate)
    },
    [provider],
  )

  const acceptInvite = useCallback(
    async (token: string): Promise<AcceptConnectionInviteResult> => {
      if (!provider) throw new Error("Not authenticated")
      const result = await provider.acceptConnectionInvite(token)
      // Patch the local list so a same-session visit to /connections is fresh
      // even before its own load lands.
      const accepted: Connection = {
        id: result.connectionId,
        connectedAt: result.connectedAt,
        peer: result.peer,
      }
      setConnections((prev) => [accepted, ...prev.filter((c) => c.id !== accepted.id)])
      return result
    },
    [provider],
  )

  const removeConnection = useCallback(
    async (id: string, block = false): Promise<void> => {
      if (!provider) throw new Error("Not authenticated")
      if (block) {
        // Blocking is deliberate and heavier — keep the confirm dialog's busy
        // state honest: wait for the server before dropping the row.
        try {
          await provider.removeConnection(id, true)
        } catch (err) {
          console.error("[connections] remove-and-block failed", err)
          throw err instanceof Error ? err : new Error("Failed to block connection")
        }
        setConnections((prev) => prev.filter((c) => c.id !== id))
        return
      }
      // Optimistic remove: drop the row immediately, restore it on failure.
      const previous = connections
      setConnections((prev) => prev.filter((c) => c.id !== id))
      try {
        await provider.removeConnection(id, false)
      } catch (err) {
        console.error("[connections] remove failed", err)
        setConnections(previous)
        throw err instanceof Error ? err : new Error("Failed to remove connection")
      }
    },
    [provider, connections],
  )

  return { connections, loading, error, ready, createInvite, acceptInvite, removeConnection }
}
