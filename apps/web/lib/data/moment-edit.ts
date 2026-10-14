import type { CompleteDraft } from "@/lib/data/moment-payloads"
import { momentDisplayName } from "@/lib/data/moment-row"
import type { Moment, MomentPebble, PebbleSnap } from "@/lib/types"

// ---------------------------------------------------------------------------
// The two edit screens (#1050, R5): Edit moment writes `save_moment` only,
// Edit pebble writes `complete_pebble` only. Their form rules live here, pure,
// so dirtiness, the "a name or a photo" gate, the photo set a save sends, the
// files a save detaches and PD-W11's single-select rule are tested without
// rendering.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Edit moment
// ---------------------------------------------------------------------------

/**
 * Edit moment's form. `snap` is the photo the form shows (web shows one, like
 * iOS): the moment's first snap, a fresh upload, or null once removed.
 */
export type MomentEditDraft = {
  name: string
  description: string
  happenedAt: string
  snap: PebbleSnap | null
}

export function momentEditDraftFrom(moment: Moment): MomentEditDraft {
  return {
    name: moment.name ?? "",
    description: moment.description ?? "",
    happenedAt: moment.happened_at,
    snap: moment.snaps[0] ?? null,
  }
}

/**
 * Whether Save has anything to send. Names and descriptions compare as they
 * are sent (trimmed), so typing spaces into an untitled moment is no change.
 */
export function isMomentEditDirty(original: MomentEditDraft, draft: MomentEditDraft): boolean {
  if (momentDisplayName(original.name) !== momentDisplayName(draft.name)) return true
  if (original.description.trim() !== draft.description.trim()) return true
  if (Date.parse(original.happenedAt) !== Date.parse(draft.happenedAt)) return true
  return (original.snap?.id ?? null) !== (draft.snap?.id ?? null)
}

/** R6: a moment needs a name or a photo; the server says `moment_empty` otherwise. */
export function momentEditHasContent(draft: MomentEditDraft): boolean {
  return momentDisplayName(draft.name) !== null || draft.snap !== null
}

/**
 * The whole snap set a save sends (R6). An untouched photo keeps the moment's
 * set as it is; a removed photo sends `[]`; a replaced one sends just the new
 * upload, as iOS does with its single form snap.
 */
export function snapsAfterEdit(original: readonly PebbleSnap[], staged: PebbleSnap | null): PebbleSnap[] {
  if (staged === null) return []
  if (original[0]?.id === staged.id) return [...original]
  return [staged]
}

/**
 * The snap directories a successful save detached: every original snap the
 * new set no longer carries. Their files are removed best-effort after the
 * save, never before (a failed save keeps them, R6).
 */
export function detachedPaths(original: readonly PebbleSnap[], next: readonly PebbleSnap[]): string[] {
  const kept = new Set(next.map((s) => s.id))
  return original.filter((s) => !kept.has(s.id)).map((s) => s.storage_path)
}

// ---------------------------------------------------------------------------
// Edit pebble
// ---------------------------------------------------------------------------

/**
 * Edit pebble's form, seeded from the caller's pebble. The valence is an
 * answer already, and the domain and collection sets are carried whole
 * (PD-W11): a legacy pebble with several of either keeps them until the user
 * touches that field.
 */
export function enrichDraftFrom(pebble: MomentPebble): CompleteDraft {
  return {
    intensity: pebble.intensity,
    positiveness: pebble.positiveness,
    valenceConfirmed: true,
    emotionId: pebble.emotion_id,
    domainIds: [...pebble.domain_ids],
    soulIds: [...pebble.soul_ids],
    collectionIds: [...pebble.collection_ids],
    visibility: pebble.visibility,
  }
}

/**
 * A pick in a single-select field (PD-W11, R13 "exactly one domain, zero or
 * one collection"). Any pick collapses the set to that one id, legacy
 * multi-sets included. Picking the one selected id clears it when the field may
 * be empty (collection), and keeps it when not (domain).
 */
export function singleSelect(current: readonly string[], id: string, allowEmpty: boolean): string[] {
  if (allowEmpty && current.length === 1 && current[0] === id) return []
  return [id]
}

function sameSet(a: readonly string[], b: readonly string[]): boolean {
  if (a.length !== b.length) return false
  const set = new Set(a)
  return b.every((id) => set.has(id))
}

export function isEnrichDraftDirty(original: CompleteDraft, draft: CompleteDraft): boolean {
  return (
    original.intensity !== draft.intensity ||
    original.positiveness !== draft.positiveness ||
    original.emotionId !== draft.emotionId ||
    original.visibility !== draft.visibility ||
    !sameSet(original.domainIds, draft.domainIds) ||
    !sameSet(original.soulIds, draft.soulIds) ||
    !sameSet(original.collectionIds, draft.collectionIds)
  )
}

/**
 * Save on Edit pebble: something changed and the pebble is still whole. Unlike
 * the Complete flow, an untouched legacy multi-domain set counts (PD-W11).
 */
export function canSaveEnrich(original: CompleteDraft, draft: CompleteDraft): boolean {
  return (
    isEnrichDraftDirty(original, draft) &&
    draft.valenceConfirmed &&
    draft.emotionId !== null &&
    draft.domainIds.length >= 1
  )
}
