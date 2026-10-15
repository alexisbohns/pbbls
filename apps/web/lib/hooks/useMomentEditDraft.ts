"use client"

import { useCallback, useMemo, useState } from "react"
import {
  isMomentEditDirty,
  momentEditDraftFrom,
  momentEditHasContent,
  type MomentEditDraft,
} from "@/lib/data/moment-edit"
import { clampName } from "@/lib/record/name"
import type { Moment, PebbleSnap } from "@/lib/types"

/**
 * Edit moment's form state (R5). The baseline is captured once on mount and
 * frozen: Save navigates away and Cancel discards, so the store reloading
 * under the form never re-seeds it. The rules live in `moment-edit.ts`.
 */
export function useMomentEditDraft(moment: Moment) {
  const [baseline] = useState(() => ({
    draft: momentEditDraftFrom(moment),
    snaps: moment.snaps,
    instantUrl: moment.instants[0] ?? null,
  }))
  const [draft, setDraft] = useState<MomentEditDraft>(baseline.draft)

  const setName = useCallback((raw: string) => setDraft((d) => ({ ...d, name: clampName(raw) })), [])
  const setDescription = useCallback((description: string) => setDraft((d) => ({ ...d, description })), [])
  const setHappenedAt = useCallback((happenedAt: string) => setDraft((d) => ({ ...d, happenedAt })), [])
  const setSnap = useCallback((snap: PebbleSnap | null) => setDraft((d) => ({ ...d, snap })), [])

  const isDirty = useMemo(() => isMomentEditDirty(baseline.draft, draft), [baseline, draft])

  return {
    draft,
    /** The moment's snaps and first photo URL as they were when the form opened. */
    originalSnaps: baseline.snaps,
    originalInstantUrl: baseline.instantUrl,
    setName,
    setDescription,
    setHappenedAt,
    setSnap,
    isDirty,
    hasContent: momentEditHasContent(draft),
  }
}
