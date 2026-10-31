"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import type { DataProvider } from "@/lib/data/data-provider"
import { useDataProvider } from "@/lib/data/provider-context"

export type KeptRead<T> = { data: T | null; loading: boolean; error: Error | null; retry: () => void }

/**
 * One of Home's reads (#1384, spec D23): the useRipple shape (provider from
 * context, the deferred first update, cancellation), plus the last value kept
 * in a module-level cache under `key` (which names the user), so a revisit
 * shows it at once and refreshes behind it, and a failed refresh keeps it.
 * `key` null (signed out, nothing to read yet) reads nothing.
 */
export function useKeptRead<T>(
  cache: Map<string, T>,
  key: string | null,
  read: (provider: DataProvider) => Promise<T>,
  label: string,
): KeptRead<T> {
  const { provider } = useDataProvider()
  const [data, setData] = useState<T | null>(() => (key !== null ? (cache.get(key) ?? null) : null))
  const [loading, setLoading] = useState(() => provider !== null && key !== null)
  const [error, setError] = useState<Error | null>(null)
  const [attempt, setAttempt] = useState(0)
  const readRef = useRef(read)
  useEffect(() => {
    readRef.current = read
  })

  useEffect(() => {
    if (!provider || key === null) return
    let cancelled = false
    void (async () => {
      await Promise.resolve()
      if (cancelled) return
      setData(cache.get(key) ?? null)
      setLoading(true)
      setError(null)
      try {
        const value = await readRef.current(provider)
        if (cancelled) return
        cache.set(key, value)
        setData(value)
      } catch (err) {
        console.error(`[home] ${label} failed`, err)
        if (cancelled) return
        setError(err instanceof Error ? err : new Error(`${label} failed`))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [provider, key, cache, label, attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  return { data, loading, error, retry }
}
