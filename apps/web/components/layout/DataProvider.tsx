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
  const inFlightRef = useRef(false)

  const loadData = useCallback(async (userId: string) => {
    activeUserIdRef.current = userId
    inFlightRef.current = true
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
      // docs/agents/data-and-async.md: no async failure path is silent. The #651
      // postmortem is what a failure with no error and no log costs.
      console.error("[DataProvider] store load failed", err)
      setError(err instanceof Error ? err : new Error("Failed to load data"))
      setProvider(null)
      setStore(EMPTY_STORE)
    } finally {
      // Same condition as setLoading: a superseded load must not clear the flag
      // out from under the newer one.
      if (activeUserIdRef.current === userId) {
        inFlightRef.current = false
        setLoading(false)
      }
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
    if (!user || inFlightRef.current) return
    void loadData(user.id)
  }, [user, loadData])

  // The load effect fires once per user per page lifetime, because
  // activeUserIdRef is set BEFORE the request — so a failed load latches exactly
  // like a successful one. That latch is kept deliberately: making the guard
  // error-aware would retry on every render against an already-failing backend,
  // and each failure sets state, which re-renders, which re-fires.
  //
  // Recovery is event-driven instead, bounded to the two events that actually
  // mean conditions may have changed. Returning to a backgrounded tab is the
  // dominant case for a mobile-first PWA.
  useEffect(() => {
    if (!error || !user) return

    const retry = () => {
      if (document.visibilityState !== "visible") return
      if (inFlightRef.current) return
      void loadData(user.id)
    }

    window.addEventListener("online", retry)
    document.addEventListener("visibilitychange", retry)
    return () => {
      window.removeEventListener("online", retry)
      document.removeEventListener("visibilitychange", retry)
    }
  }, [error, user, loadData])

  return (
    <DataContext.Provider value={{ provider, store, setStore, loading, error, refreshStore }}>
      {children}
    </DataContext.Provider>
  )
}
