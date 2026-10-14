"use client"

import { useCallback, useEffect, useRef } from "react"
import type { CompleteDraft } from "@/lib/data/moment-payloads"
import {
  clearCompleteDraft,
  readCompleteDraft,
  snapshotFromDraft,
  writeCompleteDraft,
  type CompleteDraftSnapshot,
  type DraftStorage,
} from "@/lib/record/complete-draft-store"

const DEBOUNCE_MS = 800

/**
 * `localStorage`, or null where it throws (private mode, blocked site data).
 * Autosave is a convenience: when storage is unavailable the flow still works,
 * it just will not survive a reload.
 */
function storage(): DraftStorage | null {
  try {
    return window.localStorage
  } catch (err) {
    console.warn("[complete-autosave] localStorage unavailable", err)
    return null
  }
}

/** Runs one storage access; a throw is logged and reads as null. */
function guarded<T>(label: string, run: (store: DraftStorage) => T): T | null {
  const store = storage()
  if (!store) return null
  try {
    return run(store)
  } catch (err) {
    console.warn(`[complete-autosave] ${label} failed`, err)
    return null
  }
}

/** What a flush writes: a snapshot, or null to clear an emptied draft. */
type Pending = { ownerId: string; snapshot: CompleteDraftSnapshot | null }

type Options = {
  /** The signed-in account. Null while signed out: nothing is read or written. */
  ownerId: string | null
  momentId: string
  draft: CompleteDraft
  /**
   * Restore and writes wait for this: the store must be loaded so the
   * restored souls and collections can be sanitized, and nothing is written
   * once the flow has completed.
   */
  ready: boolean
  /** Called once, silently, with the moment's saved draft, if any. */
  onRestore: (snapshot: CompleteDraftSnapshot) => void
}

/**
 * Per-moment autosave for the Complete flow (#1050, PD-W7; iOS
 * `CompleteAutosave`): writes debounced by 800 ms, flushed when the tab is
 * hidden and on unmount, and a silent restore once the flow is ready.
 *
 * Lives in `lib/hooks/`: it is UI state, not provider data, and components
 * never touch `localStorage` themselves (docs/agents/data-and-async.md).
 */
export function useCompleteAutosave({ ownerId, momentId, draft, ready, onRestore }: Options) {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pending = useRef<Pending | null>(null)
  const restored = useRef(false)
  const ownerRef = useRef(ownerId)
  const momentRef = useRef(momentId)

  useEffect(() => {
    ownerRef.current = ownerId
    momentRef.current = momentId
    // An edit made under one account never lands in another's entry.
    if (pending.current && pending.current.ownerId !== ownerId) pending.current = null
  }, [ownerId, momentId])

  const flush = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current)
      timer.current = null
    }
    const next = pending.current
    pending.current = null
    if (!next || next.ownerId !== ownerRef.current) return
    guarded("write", (store) => {
      if (next.snapshot) writeCompleteDraft(store, next.snapshot)
      else clearCompleteDraft(store, momentRef.current)
    })
  }, [])

  const clear = useCallback(() => {
    if (timer.current) {
      clearTimeout(timer.current)
      timer.current = null
    }
    pending.current = null
    guarded("clear", (store) => clearCompleteDraft(store, momentRef.current))
  }, [])

  // Silent restore, once. Runs before the write effect below so a restore's
  // first schedule is the hydrated draft, never a clear of the saved one.
  useEffect(() => {
    if (restored.current || !ready || !ownerId) return
    restored.current = true
    const snapshot = guarded("read", (store) => readCompleteDraft(store, ownerId, momentId))
    if (snapshot) onRestore(snapshot)
  }, [ready, ownerId, momentId, onRestore])

  useEffect(() => {
    if (!restored.current || !ready || !ownerId) return
    pending.current = { ownerId, snapshot: snapshotFromDraft(ownerId, momentId, draft) }
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(flush, DEBOUNCE_MS)
  }, [draft, ready, ownerId, momentId, flush])

  // `visibilitychange` is the one event mobile browsers reliably fire before a
  // tab is discarded (`beforeunload` is not).
  useEffect(() => {
    const onHide = () => {
      if (document.visibilityState === "hidden") flush()
    }
    document.addEventListener("visibilitychange", onHide)
    return () => {
      document.removeEventListener("visibilitychange", onHide)
      flush()
    }
  }, [flush])

  return { flush, clear }
}
