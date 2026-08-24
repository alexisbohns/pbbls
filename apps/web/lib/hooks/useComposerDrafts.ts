"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { usePebbleDrafts } from "@/lib/data/usePebbleDrafts"
import { useComposerAutosave } from "@/lib/hooks/useComposerAutosave"
import {
  buildAutosavePayload,
  buildDraftPayload,
  isDraftEmpty,
  type ComposerState,
} from "@/components/record/draft-payload"
import type { PebbleDraftPayload } from "@/lib/data/data-provider"

type UseComposerDraftsOptions = {
  /** Resumed server draft id, if the composer was opened from /drafts. */
  draftId?: string
  /** That draft's payload. Applied once, keyed by `draftId`. */
  initialPayload?: PebbleDraftPayload
  /** The composer's current state — the one projection both paths derive from. */
  state: ComposerState
  /** Writes a payload back into the composer, sanitized against what still exists. */
  applyPayload: (payload: PebbleDraftPayload) => void
  /**
   * False while the store is still loading. Gates hydration and the restore
   * prompt: sanitizing against empty sets would drop every reference.
   */
  ready: boolean
}

/**
 * The composer's draft lifecycle (M47), shared by both composers.
 *
 * Every rule in here is a bug that was found and fixed once: hydrate a resumed
 * server draft only once the store can sanitize it; never offer the local
 * snapshot on top of a resumed server draft; ask about the snapshot once per
 * mount rather than on every keystroke; hold autosave off while the restore
 * prompt is up so the pending answer is not overwritten first; and drop the
 * snapshot the moment the draft reaches the server.
 *
 * Copying that into a second composer is how the two drift apart, and the
 * failure mode is silent — the copy keeps working while quietly losing the
 * fixes. So the flow and the single-screen editor are both consumers of this.
 */
export function useComposerDrafts({
  draftId,
  initialPayload,
  state,
  applyPayload,
  ready,
}: UseComposerDraftsOptions) {
  const { saveDraft, removeDraft } = usePebbleDrafts()
  // Destructured so effects can depend on the stable callbacks rather than on
  // the hook's result object, which is a fresh literal every render.
  const { snapshot, save: saveSnapshot, clear: clearSnapshot } = useComposerAutosave()

  // Set once a resumed/created server draft is in play, so publishing knows
  // which row to delete.
  const [serverDraftId, setServerDraftId] = useState<string | undefined>(draftId)
  const [restorePromptOpen, setRestorePromptOpen] = useState(false)

  const isEmpty = isDraftEmpty(buildDraftPayload(state))

  // Hydrate a resumed server draft. Keyed by draft id so it runs once per
  // resumed draft, even though `initialPayload` is a fresh object each render.
  const hydratedDraftRef = useRef<string | undefined>(undefined)
  useEffect(() => {
    if (!initialPayload || !draftId || !ready) return
    if (hydratedDraftRef.current === draftId) return
    hydratedDraftRef.current = draftId
    applyPayload(initialPayload)
  }, [initialPayload, draftId, ready, applyPayload])

  // Offer to restore the local snapshot — but never on top of a resumed server
  // draft, which is the more deliberate of the two. Asked once per mount: the
  // snapshot is rewritten as the user types, so re-prompting on every change
  // would be maddening.
  const askedToRestoreRef = useRef(false)
  const restorableRef = useRef<PebbleDraftPayload | null>(null)
  useEffect(() => {
    if (askedToRestoreRef.current || draftId || !ready) return
    askedToRestoreRef.current = true
    if (!snapshot || isDraftEmpty(snapshot)) return
    restorableRef.current = snapshot
    // `await Promise.resolve()` defers the state update past the synchronous
    // render boundary (mirrors usePebbleDrafts.ts) to satisfy
    // react-hooks/set-state-in-effect. Safe to land a microtask late: autosave
    // is gated on the composer being non-empty, and a composer with a snapshot
    // worth restoring is by definition still empty.
    let cancelled = false
    void (async () => {
      await Promise.resolve()
      if (!cancelled) setRestorePromptOpen(true)
    })()
    return () => {
      cancelled = true
    }
  }, [snapshot, draftId, ready])

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

  /**
   * Intentional "save as draft" — ungated, unlike publishing. Resolves true on
   * success; the caller owns what happens next (reset, navigate, close).
   */
  const saveAsDraft = useCallback(async (): Promise<boolean> => {
    try {
      const saved = await saveDraft(buildDraftPayload(state), serverDraftId)
      setServerDraftId(saved.id)
      // The snapshot exists to survive a crash in the open composer; once the
      // draft is on the server it is redundant.
      clearSnapshot()
      return true
    } catch (err) {
      console.error("[composer-drafts] draft save failed", err)
      return false
    }
  }, [state, serverDraftId, saveDraft, clearSnapshot])

  /**
   * Called once the pebble exists. The draft has served its purpose, so a
   * failure to delete must not fail the publish — worst case a stale draft is
   * left behind.
   */
  const consumeAfterPublish = useCallback(async () => {
    if (serverDraftId) {
      try {
        await removeDraft(serverDraftId)
      } catch (err) {
        console.warn("[composer-drafts] draft cleanup after publish failed", err)
      }
      setServerDraftId(undefined)
    }
    clearSnapshot()
  }, [serverDraftId, removeDraft, clearSnapshot])

  return {
    isEmpty,
    restorePromptOpen,
    setRestorePromptOpen,
    restore,
    discardSnapshot,
    saveAsDraft,
    consumeAfterPublish,
  }
}
