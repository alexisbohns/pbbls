"use client"

import { useCallback, useEffect, useMemo, useRef, useSyncExternalStore } from "react"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"

const STORAGE_KEY = "pbbls-composer-draft"
const CHANGE_EVENT = "pbbls-composer-draft-change"
const DEBOUNCE_MS = 800

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback)
  window.addEventListener(CHANGE_EVENT, callback)
  return () => {
    window.removeEventListener("storage", callback)
    window.removeEventListener(CHANGE_EVENT, callback)
  }
}

/**
 * Read-and-validate. A snapshot written by an older build (or hand-edited) must
 * never crash the composer, so anything that is not a JSON object is discarded
 * rather than trusted. Returns the raw string so `useSyncExternalStore` can
 * compare snapshots by identity; parsing happens once in the hook.
 */
function getSnapshot(): string | null {
  return localStorage.getItem(STORAGE_KEY)
}

function getServerSnapshot(): string | null {
  return null
}

function parse(raw: string | null): PebbleDraftPayload | null {
  if (!raw) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) return null
    return parsed
  } catch {
    return null
  }
}

/**
 * Crash insurance for the open composer (M47). Debounced snapshot of the draft
 * payload in `localStorage` — device-local, single-composer, no merge logic and
 * no cross-device sync. This is deliberately NOT offline support: see the
 * 2026-07-29 "Offline is a non-goal on every surface" decision-log entry.
 *
 * The server `pebble_drafts` table is the intentional "save as draft"; this is
 * only for the reload/crash you did not choose. Media is excluded (D3).
 *
 * Lives in `lib/hooks/` rather than `lib/data/`: it is UI state, not
 * provider-backed data, and components must never touch `localStorage`
 * directly (docs/agents/data-and-async.md).
 */
export function useComposerAutosave() {
  const raw = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pendingRef = useRef<PebbleDraftPayload | null>(null)

  // Memoized on the raw string so `snapshot` keeps a stable identity across
  // renders and is safe to use as an effect dependency.
  const snapshot = useMemo(() => parse(raw), [raw])

  const clear = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current)
      timer.current = null
    }
    pendingRef.current = null
    localStorage.removeItem(STORAGE_KEY)
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }, [])

  /**
   * Queue a snapshot. Debounced so typing does not thrash storage; the pending
   * write is flushed on hide/unmount so a reload mid-keystroke still recovers.
   *
   * Deliberately does NOT dispatch CHANGE_EVENT: subscribers are for restoring,
   * and waking them on every keystroke would re-render the composer from its own
   * snapshot while the user is typing into it.
   */
  const save = useCallback((payload: PebbleDraftPayload) => {
    pendingRef.current = payload
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => {
      timer.current = null
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
    }, DEBOUNCE_MS)
  }, [])

  // Flush the debounced write when the tab is hidden or the composer unmounts —
  // `visibilitychange` is the one event mobile browsers reliably fire before a
  // tab is discarded (`beforeunload` is not).
  useEffect(() => {
    const flush = () => {
      if (!timer.current || !pendingRef.current) return
      clearTimeout(timer.current)
      timer.current = null
      localStorage.setItem(STORAGE_KEY, JSON.stringify(pendingRef.current))
    }
    const onHide = () => {
      if (document.visibilityState === "hidden") flush()
    }
    document.addEventListener("visibilitychange", onHide)
    return () => {
      document.removeEventListener("visibilitychange", onHide)
      flush()
    }
  }, [])

  return { snapshot, save, clear }
}
