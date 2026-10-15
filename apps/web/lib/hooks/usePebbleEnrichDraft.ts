"use client"

import { useCallback, useState } from "react"
import { canSaveEnrich, enrichDraftFrom, isEnrichDraftDirty } from "@/lib/data/moment-edit"
import type { CompleteDraft } from "@/lib/data/moment-payloads"
import type { Intensity, MomentPebble, Positiveness, Visibility } from "@/lib/types"

/**
 * Edit pebble's form state (R5), a `CompleteDraft` seeded from the caller's
 * pebble with its whole domain and collection sets (PD-W11): the tiles in
 * `single` mode collapse a set to one only once it is touched. The baseline is
 * frozen on mount, like Edit moment's.
 */
export function usePebbleEnrichDraft(pebble: MomentPebble) {
  const [baseline] = useState(() => enrichDraftFrom(pebble))
  const [draft, setDraft] = useState<CompleteDraft>(baseline)

  const setValence = useCallback(
    (intensity: Intensity, positiveness: Positiveness) =>
      setDraft((d) => ({ ...d, intensity, positiveness, valenceConfirmed: true })),
    [],
  )
  const setEmotion = useCallback((id: string) => setDraft((d) => ({ ...d, emotionId: id })), [])
  const setDomains = useCallback((ids: string[]) => setDraft((d) => ({ ...d, domainIds: ids })), [])
  const setCollections = useCallback((ids: string[]) => setDraft((d) => ({ ...d, collectionIds: ids })), [])
  const setVisibility = useCallback((visibility: Visibility) => setDraft((d) => ({ ...d, visibility })), [])
  const toggleSoul = useCallback(
    (id: string) =>
      setDraft((d) => ({
        ...d,
        soulIds: d.soulIds.includes(id) ? d.soulIds.filter((s) => s !== id) : [...d.soulIds, id],
      })),
    [],
  )
  const addSoul = useCallback(
    (id: string) => setDraft((d) => (d.soulIds.includes(id) ? d : { ...d, soulIds: [...d.soulIds, id] })),
    [],
  )

  return {
    draft,
    setValence,
    setEmotion,
    setDomains,
    setCollections,
    setVisibility,
    toggleSoul,
    addSoul,
    isDirty: isEnrichDraftDirty(baseline, draft),
    canSave: canSaveEnrich(baseline, draft),
  }
}
