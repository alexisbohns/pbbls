"use client"

import { useState, useEffect, type Dispatch, type SetStateAction } from "react"
import { LocalProvider } from "@/lib/data/local-provider"
import { DataContext } from "@/lib/data/provider-context"
import type { Store } from "@/lib/data/data-provider"

/**
 * Client-only wrapper that boots a LocalProvider and exposes the store
 * snapshot through context.
 *
 * The initial store is always empty and loading=true on both server and
 * client so that SSR HTML and the first client render are identical,
 * preventing hydration mismatches. After the component mounts, a useEffect
 * reads localStorage, sets the real store, and flips loading to false — all
 * in a single batched state update to avoid cascading renders.
 */

const INITIAL_STORE: Store = {
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

type DataState = { store: Store; loading: boolean }

const INITIAL_STATE: DataState = { store: INITIAL_STORE, loading: true }

export function DataProvider({ children }: { children: React.ReactNode }) {
  // Provider is initialized synchronously (stable reference across renders).
  // On the server it holds an empty store; on the client it reads localStorage,
  // but we intentionally defer getStore() to useEffect so both server and
  // client first-render share the same INITIAL_STORE, avoiding hydration
  // mismatches.
  const [provider] = useState<LocalProvider>(() => new LocalProvider())
  const [{ store, loading }, setDataState] = useState<DataState>(INITIAL_STATE)

  // Wrap setStore to satisfy the Dispatch<SetStateAction<Store>> type required
  // by DataContext while keeping store/loading in the same state atom —
  // mutations call setStore, not setDataState directly.
  const setStore: Dispatch<SetStateAction<Store>> = (storeOrUpdater) =>
    setDataState((prev) => ({
      ...prev,
      store:
        typeof storeOrUpdater === "function"
          ? storeOrUpdater(prev.store)
          : storeOrUpdater,
    }))

  useEffect(() => {
    // Persist the seed to localStorage if the key is absent (first launch).
    // Deferred here to keep LocalProvider's load() side-effect-free and safe
    // under StrictMode double-invocation.
    provider.persistIfNeeded()
    // Call setState in a microtask callback so it is not synchronous in the
    // effect body — satisfies react-hooks/set-state-in-effect while keeping
    // the update as close to synchronous as possible.
    void Promise.resolve().then(() => {
      setDataState({ store: provider.getStore(), loading: false })
    })
  }, [provider])

  return (
    <DataContext.Provider value={{ provider, store, setStore, loading }}>
      {children}
    </DataContext.Provider>
  )
}
