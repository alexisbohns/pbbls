"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { DataContext } from "@/lib/data/provider-context"
import { LocalProvider } from "@/lib/data/local-provider"
import { SupabaseProvider } from "@/lib/data/supabase-provider"
import { useAuth } from "@/lib/data/auth-context"
import { createClient } from "@/lib/supabase/client"
import { EMPTY_STORE, type DataProvider as DataProviderInterface, type Store } from "@/lib/data/data-provider"

// Fallback provider for unauthenticated state — safe to call methods on.
const fallbackProvider = new LocalProvider()

export function DataProvider({ children }: { children: React.ReactNode }) {
  const { user, isLoading: authLoading } = useAuth()

  const [provider, setProvider] = useState<DataProviderInterface>(fallbackProvider)
  const [store, setStore] = useState<Store>(EMPTY_STORE)
  const [loading, setLoading] = useState(true)

  // Track which user ID the current provider belongs to, so we only
  // recreate the SupabaseProvider when the actual identity changes —
  // not on every user object reference change.
  const activeUserIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (authLoading) {
      return
    }

    if (!user) {
      // Not authenticated — use fallback provider
      const wasAuthenticated = activeUserIdRef.current !== null
      activeUserIdRef.current = null
      void Promise.resolve().then(() => {
        if (wasAuthenticated) {
          setProvider(fallbackProvider)
          setStore(EMPTY_STORE)
        }
        setLoading(false)
      })
      return
    }

    // Same user as before — don't recreate provider
    if (activeUserIdRef.current === user.id) {
      return
    }

    activeUserIdRef.current = user.id
    const supabase = createClient()
    const sp = new SupabaseProvider(user.id, supabase)
    const userId = user.id

    void Promise.resolve().then(() => {
      setProvider(sp)
      setStore(sp.getStore())
      setLoading(false)
    })

    // Sync from Supabase in background — only apply if still the active user
    sp.syncFromSupabase().then((freshStore) => {
      if (activeUserIdRef.current === userId) setStore(freshStore)
    }).catch(() => {
      // Sync failed — keep localStorage data
    })
  }, [user, authLoading])

  const wrappedSetStore = useCallback(
    (storeOrUpdater: Store | ((prev: Store) => Store)) => {
      setStore((prev) => {
        const next = typeof storeOrUpdater === "function" ? storeOrUpdater(prev) : storeOrUpdater
        return next
      })
    },
    [],
  )

  return (
    <DataContext.Provider value={{ provider, store, setStore: wrappedSetStore, loading }}>
      {children}
    </DataContext.Provider>
  )
}
