"use client"

import { useEffect } from "react"

/** The M47 composer's local autosave, retired with it by Capture (#1050). */
const RETIRED_COMPOSER_DRAFT_KEY = "pbbls-composer-draft"

/**
 * Removes the retired composer's autosave from this device, once per Capture
 * mount (PD-W7). Capture has no autosave, so the snapshot can never be
 * restored, and it may still hold a name or a description the user typed.
 */
export function useRetiredComposerDraftCleanup() {
  useEffect(() => {
    try {
      window.localStorage.removeItem(RETIRED_COMPOSER_DRAFT_KEY)
    } catch (err) {
      console.warn("[capture] could not remove the retired composer draft", err)
    }
  }, [])
}
