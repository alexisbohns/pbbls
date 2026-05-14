"use client"

import { useCallback, useSyncExternalStore } from "react"

const getServerSnapshot = () => false

/**
 * SSR-safe wrapper around `window.matchMedia`. Returns `false` on the
 * server pass and the first client render, then subscribes to changes.
 * Implemented with `useSyncExternalStore` to play nicely with React 19
 * concurrent rendering and the `react-hooks/set-state-in-effect` rule.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (callback: () => void) => {
      const mql = window.matchMedia(query)
      mql.addEventListener("change", callback)
      return () => mql.removeEventListener("change", callback)
    },
    [query],
  )

  const getSnapshot = useCallback(
    () => window.matchMedia(query).matches,
    [query],
  )

  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}
