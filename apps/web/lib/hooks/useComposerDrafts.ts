"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { useComposerAutosave } from "@/lib/hooks/useComposerAutosave"
import {
  buildAutosavePayload,
  buildDraftPayload,
  isDraftEmpty,
  type ComposerState,
} from "@/components/record/draft-payload"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"

type UseComposerDraftsOptions = {
  /** The composer's current state — the one projection the snapshot derives from. */
  state: ComposerState
  /** Writes a payload back into the composer, sanitized against what still exists. */
  applyPayload: (payload: PebbleDraftPayload) => void
  /**
   * False while the store is still loading. Gates the restore prompt:
   * sanitizing against empty sets would drop every reference.
   */
  ready: boolean
}

/**
 * The composer's local draft lifecycle (M47), shared by both composers.
 *
 * Server drafts are gone (#1046, D7), so what is left is the device-local
 * snapshot: autosave the open composer, offer it back once per mount, and drop
 * it once the pebble exists. Every rule in here is a bug that was found and
 * fixed once: ask about the snapshot once per mount rather than on every
 * keystroke; only once the store can sanitize it; and hold autosave off while
 * the restore prompt is up so the pending answer is not overwritten first.
 *
 * Copying that into a second composer is how the two drift apart, and the
 * failure mode is silent — the copy keeps working while quietly losing the
 * fixes. So the flow and the single-screen editor are both consumers of this.
 */
export function useComposerDrafts({ state, applyPayload, ready }: UseComposerDraftsOptions) {
  // Destructured so effects can depend on the stable callbacks rather than on
  // the hook's result object, which is a fresh literal every render.
  const { snapshot, save: saveSnapshot, clear: clearSnapshot } = useComposerAutosave()

  const [restorePromptOpen, setRestorePromptOpen] = useState(false)

  const isEmpty = isDraftEmpty(buildDraftPayload(state))

  // Offer to restore the local snapshot. Asked once per mount: the snapshot is
  // rewritten as the user types, so re-prompting on every change would be
  // maddening.
  const askedToRestoreRef = useRef(false)
  const restorableRef = useRef<PebbleDraftPayload | null>(null)
  useEffect(() => {
    if (askedToRestoreRef.current || !ready) return
    askedToRestoreRef.current = true
    if (!snapshot || isDraftEmpty(snapshot)) return
    restorableRef.current = snapshot
    // `await Promise.resolve()` defers the state update past the synchronous
    // render boundary to satisfy react-hooks/set-state-in-effect. Safe to land
    // a microtask late: autosave is gated on the composer being non-empty, and
    // a composer with a snapshot worth restoring is by definition still empty.
    let cancelled = false
    void (async () => {
      await Promise.resolve()
      if (!cancelled) setRestorePromptOpen(true)
    })()
    return () => {
      cancelled = true
    }
  }, [snapshot, ready])

  // Autosave the open composer, debounced inside the hook. Held off while the
  // restore prompt is up so the pending answer cannot be overwritten first.
  useEffect(() => {
    if (restorePromptOpen || isEmpty) return
    saveSnapshot(buildAutosavePayload(state))
  }, [state, isEmpty, restorePromptOpen, saveSnapshot])

  const restore = useCallback(() => {
    const restorable = restorableRef.current
    if (restorable) applyPayload(restorable)
    setRestorePromptOpen(false)
  }, [applyPayload])

  const discardSnapshot = useCallback(() => {
    clearSnapshot()
    setRestorePromptOpen(false)
  }, [clearSnapshot])

  /** Called once the pebble exists: the snapshot has served its purpose. */
  const consumeAfterPublish = useCallback(() => {
    clearSnapshot()
  }, [clearSnapshot])

  return {
    isEmpty,
    restorePromptOpen,
    setRestorePromptOpen,
    restore,
    discardSnapshot,
    consumeAfterPublish,
  }
}
