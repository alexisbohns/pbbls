"use client"

import { useCallback, useMemo, useReducer, useState } from "react"
import type { Pebble, PebbleSnap } from "@/lib/types"
import type { UpdatePebbleInput } from "@/lib/data/data-provider"

// ---------------------------------------------------------------------------
// Pebble edit draft — mirrors iOS `PebbleDraft` (apps/ios/.../Models/PebbleDraft.swift).
// Holds every editable field locally so the Edit screen can stage changes and
// commit them atomically via `compose-pebble-update`. The baseline (`original`)
// is captured once on mount and frozen — a successful Save navigates away, and
// Cancel discards the draft, so we never need to re-sync mid-edit.
// ---------------------------------------------------------------------------

export type PebbleDraftSnap = PebbleSnap

export type PebbleDraft = {
  name: string
  description: string
  intensity: Pebble["intensity"]
  positiveness: Pebble["positiveness"]
  emotion_id: string
  mark_id: string | null
  domain_ids: string[]
  collection_ids: string[]
  soul_ids: string[]
  snap: PebbleDraftSnap | null
}

type DraftAction =
  | { type: "set"; key: keyof PebbleDraft; value: PebbleDraft[keyof PebbleDraft] }
  | { type: "setMany"; patch: Partial<PebbleDraft> }
  | { type: "reset"; draft: PebbleDraft }

function reducer(state: PebbleDraft, action: DraftAction): PebbleDraft {
  switch (action.type) {
    case "set":
      return { ...state, [action.key]: action.value }
    case "setMany":
      return { ...state, ...action.patch }
    case "reset":
      return action.draft
  }
}

function snapshotFromPebble(pebble: Pebble): PebbleDraft {
  return {
    name: pebble.name,
    description: pebble.description ?? "",
    intensity: pebble.intensity,
    positiveness: pebble.positiveness,
    emotion_id: pebble.emotion_id,
    mark_id: pebble.mark_id ?? null,
    domain_ids: [...pebble.domain_ids],
    collection_ids: [...pebble.collection_ids],
    soul_ids: [...pebble.soul_ids],
    snap: pebble.snaps[0] ?? null,
  }
}

function sameStringArray(a: string[], b: string[]): boolean {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false
  return true
}

function sameSnap(a: PebbleDraftSnap | null, b: PebbleDraftSnap | null): boolean {
  if (a === b) return true
  if (!a || !b) return false
  return a.id === b.id && a.storage_path === b.storage_path
}

function diffFields(original: PebbleDraft, draft: PebbleDraft): Set<keyof PebbleDraft> {
  const dirty = new Set<keyof PebbleDraft>()
  if (original.name !== draft.name) dirty.add("name")
  if (original.description !== draft.description) dirty.add("description")
  if (original.intensity !== draft.intensity) dirty.add("intensity")
  if (original.positiveness !== draft.positiveness) dirty.add("positiveness")
  if (original.emotion_id !== draft.emotion_id) dirty.add("emotion_id")
  if (original.mark_id !== draft.mark_id) dirty.add("mark_id")
  if (!sameStringArray(original.domain_ids, draft.domain_ids)) dirty.add("domain_ids")
  if (!sameStringArray(original.collection_ids, draft.collection_ids)) dirty.add("collection_ids")
  if (!sameStringArray(original.soul_ids, draft.soul_ids)) dirty.add("soul_ids")
  if (!sameSnap(original.snap, draft.snap)) dirty.add("snap")
  return dirty
}

export type UsePebbleDraftResult = {
  draft: PebbleDraft
  setField: <K extends keyof PebbleDraft>(key: K, value: PebbleDraft[K]) => void
  setFields: (patch: Partial<PebbleDraft>) => void
  isDirty: boolean
  dirtyFields: Set<keyof PebbleDraft>
  buildPayload: () => UpdatePebbleInput
  resetToOriginal: () => void
}

export function usePebbleDraft(pebble: Pebble): UsePebbleDraftResult {
  // Capture the baseline once, on first render. `useState`'s lazy initializer
  // runs exactly once per mount, which is what we want: the original is
  // frozen until the user navigates away (Save or Cancel).
  const [original] = useState(() => snapshotFromPebble(pebble))
  const [draft, dispatch] = useReducer(reducer, original)

  const setField = useCallback(
    <K extends keyof PebbleDraft>(key: K, value: PebbleDraft[K]) => {
      dispatch({ type: "set", key, value })
    },
    [],
  )

  const setFields = useCallback((patch: Partial<PebbleDraft>) => {
    dispatch({ type: "setMany", patch })
  }, [])

  const resetToOriginal = useCallback(() => {
    dispatch({ type: "reset", draft: original })
  }, [original])

  const dirtyFields = useMemo(() => diffFields(original, draft), [original, draft])
  const isDirty = dirtyFields.size > 0

  const buildPayload = useCallback((): UpdatePebbleInput => {
    const payload: UpdatePebbleInput = {}
    if (dirtyFields.has("name")) payload.name = draft.name
    if (dirtyFields.has("description")) payload.description = draft.description
    if (dirtyFields.has("intensity")) payload.intensity = draft.intensity
    if (dirtyFields.has("positiveness")) payload.positiveness = draft.positiveness
    if (dirtyFields.has("emotion_id")) payload.emotion_id = draft.emotion_id
    // The provider maps `mark_id` to `glyph_id` and treats `null` as a clear;
    // `Pebble.mark_id` types as `string | undefined`, so cast for the
    // explicit-null clear path.
    if (dirtyFields.has("mark_id")) {
      ;(payload as Record<string, unknown>).mark_id = draft.mark_id
    }
    if (dirtyFields.has("domain_ids")) payload.domain_ids = draft.domain_ids
    if (dirtyFields.has("collection_ids")) payload.collection_ids = draft.collection_ids
    if (dirtyFields.has("soul_ids")) payload.soul_ids = draft.soul_ids
    if (dirtyFields.has("snap")) payload.snaps = draft.snap ? [draft.snap] : []
    return payload
  }, [dirtyFields, draft])

  return {
    draft,
    setField,
    setFields,
    isDirty,
    dirtyFields,
    buildPayload,
    resetToOriginal,
  }
}
