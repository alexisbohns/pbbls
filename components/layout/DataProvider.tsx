"use client"

import { useState, useEffect } from "react"
import { LocalProvider } from "@/lib/data/local-provider"
import { DataContext } from "@/lib/data/provider-context"
import type { Store } from "@/lib/data/data-provider"

/**
 * Client-only wrapper that boots a LocalProvider via useState lazy
 * initialization and exposes the store snapshot through context.
 *
 * Lazy init (not useEffect) avoids the react-hooks/set-state-in-effect rule
 * while keeping initialization synchronous. LocalProvider's constructor guards
 * against SSR via a typeof window check — on the server it returns an empty
 * store; on the client it reads from localStorage (or seeds on first launch).
 *
 * Mirrors the ThemeProvider pattern: a thin "use client" shell that all
 * children can safely consume via useDataProvider().
 */
export function DataProvider({ children }: { children: React.ReactNode }) {
  // Lazy init: runs once on mount. Provider is always non-null after init.
  const [provider] = useState<LocalProvider>(() => new LocalProvider())
  const [store, setStore] = useState<Store>(() => provider.getStore())

  // Persist the seed to localStorage after the first render if the key is
  // absent. Deferred here (not in the constructor) to keep LocalProvider's
  // load() side-effect-free and safe under StrictMode double-invocation.
  useEffect(() => {
    provider.persistIfNeeded()
  }, [provider])

  return (
    <DataContext.Provider value={{ provider, store, setStore, loading: false }}>
      {children}
    </DataContext.Provider>
  )
}
