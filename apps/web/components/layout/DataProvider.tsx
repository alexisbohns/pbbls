"use client"

import { useState, useEffect, useCallback, useRef } from "react"
import { DataContext } from "@/lib/data/provider-context"
import { SupabaseProvider } from "@/lib/data/supabase-provider"
import { useAuth } from "@/lib/data/auth-context"
import { createClient } from "@/lib/supabase/client"
import { EMPTY_STORE, type Store } from "@/lib/data/data-provider"

export function DataProvider({ children }: { children: React.ReactNode }) {
  const { user, isLoading: authLoading } = useAuth()

  const [provider, setProvider] = useState<SupabaseProvider | null>(null)
  const [store, setStore] = useState<Store>(EMPTY_STORE)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  const activeUserIdRef = useRef<string | null>(null)

  const loadData = useCallback(async (userId: string) => {
    activeUserIdRef.current = userId
    setLoading(true)
    setError(null)

    try {
      const supabase = createClient()
      const sp = new SupabaseProvider(userId, supabase)
      const freshStore = await sp.loadFromSupabase()

      if (activeUserIdRef.current !== userId) return
      setProvider(sp)
      setStore(freshStore)
    } catch (err) {
      if (activeUserIdRef.current !== userId) return
      setError(err instanceof Error ? err : new Error("Failed to load data"))
      setProvider(null)
      setStore(EMPTY_STORE)
    } finally {
      if (activeUserIdRef.current === userId) setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (authLoading) return

    if (!user) {
      activeUserIdRef.current = null
      void Promise.resolve().then(() => {
        setProvider(null)
        setStore(EMPTY_STORE)
        setLoading(false)
        setError(null)
      })
      return
    }

    if (activeUserIdRef.current === user.id) return

    void loadData(user.id)
  }, [user, authLoading, loadData])

  const refreshStore = useCallback(() => {
    if (user) void loadData(user.id)
  }, [user, loadData])

  return (
    <DataContext.Provider value={{ provider, store, setStore, loading, error, refreshStore }}>
      {children}
    </DataContext.Provider>
  )
}
