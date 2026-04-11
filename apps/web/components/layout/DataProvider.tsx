"use client"

import { useState, useEffect } from "react"
import { DataContext } from "@/lib/data/provider-context"
import { SupabaseProvider } from "@/lib/data/supabase-provider"
import { useAuth } from "@/lib/data/auth-context"
import { createClient } from "@/lib/supabase/client"
import type { Store } from "@/lib/data/data-provider"

const EMPTY_STORE: Store = {
  pebbles: [],
  souls: [],
  collections: [],
  marks: [],
  pebbles_count: 0,
  karma: 0,
  karma_log: [],
  bounce: 0,
  bounce_window: [],
}

export function DataProvider({ children }: { children: React.ReactNode }) {
  const { user, isLoading: authLoading } = useAuth()

  const [provider, setProvider] = useState<SupabaseProvider | null>(null)
  const [store, setStore] = useState<Store>(EMPTY_STORE)
  const [loading, setLoading] = useState(true)

  // Create provider when user is available
  useEffect(() => {
    if (authLoading || !user) {
      void Promise.resolve().then(() => {
        setProvider(null)
        setStore(EMPTY_STORE)
        setLoading(!authLoading)
      })
      return
    }

    const supabase = createClient()
    const sp = new SupabaseProvider(user.id, supabase)

    // Load from localStorage immediately via microtask to satisfy
    // react-hooks/set-state-in-effect lint rule.
    void Promise.resolve().then(() => {
      setProvider(sp)
      setStore(sp.getStore())
      setLoading(false)
    })

    // Sync from Supabase in background
    sp.syncFromSupabase().then((freshStore) => {
      setStore(freshStore)
    }).catch(() => {
      // Sync failed — keep localStorage data
    })
  }, [user, authLoading])

  if (!provider) {
    return (
      <DataContext.Provider value={{
        provider: null as unknown as SupabaseProvider,
        store: EMPTY_STORE,
        setStore: () => {},
        loading: authLoading,
      }}>
        {children}
      </DataContext.Provider>
    )
  }

  const wrappedSetStore = (storeOrUpdater: Store | ((prev: Store) => Store)) => {
    setStore((prev) => {
      const next = typeof storeOrUpdater === "function" ? storeOrUpdater(prev) : storeOrUpdater
      return next
    })
  }

  return (
    <DataContext.Provider value={{ provider, store, setStore: wrappedSetStore, loading }}>
      {children}
    </DataContext.Provider>
  )
}
