import { wholeSecondIso } from "@/lib/auth/signup-metadata"
import type { Intensity, PebbleSnap, Positiveness, Visibility } from "@/lib/types"

// ---------------------------------------------------------------------------
// Wire payloads for `save_moment` and `complete_pebble` (#1050), pure so the
// exact JSON is testable. Conventions shared with iOS and Android:
//   - UUIDs lowercased;
//   - `happened_at` at whole seconds (the narrowest precision every reader
//     accepts), truncated, never rounded;
//   - an absent key is OMITTED, never null (the RPCs read JSON null as absent).
// Key order is part of what the tests pin, so it is written out by hand.
// ---------------------------------------------------------------------------

export type SnapPayload = { id: string; storage_path: string; sort_order: number }

export type SaveMomentPayload = {
  id?: string
  name?: string
  description?: string
  happened_at?: string
  snaps?: SnapPayload[]
}

export type CompletePebblePayload = {
  moment_id: string
  emotion_id: string
  intensity: Intensity
  positiveness: Positiveness
  visibility: Visibility
  domain_ids: string[]
  soul_ids: string[]
  collection_ids: string[]
}

/**
 * What the Complete flow holds (Part 2 re-exports it from
 * `lib/record/complete.ts`). `domainIds` and `collectionIds` are arrays only so
 * Edit pebble can carry an untouched legacy multi-set back; Complete holds at
 * most one of each. `valenceConfirmed` is set by a valence pick or Continue.
 */
export type CompleteDraft = {
  intensity: Intensity
  positiveness: Positiveness
  valenceConfirmed: boolean
  emotionId: string | null
  domainIds: string[]
  soulIds: string[]
  collectionIds: string[]
  visibility: Visibility
}

function snapPayload(snap: PebbleSnap, sortOrder: number): SnapPayload {
  // The storage path is a storage key: the files live at exactly that spelling.
  return { id: snap.id.toLowerCase(), storage_path: snap.storage_path, sort_order: sortOrder }
}

/** `happenedAt` must parse as a date (an ISO string from a picker); an invalid one throws a RangeError. */
function whenPayload(happenedAt: string): string {
  return wholeSecondIso(new Date(happenedAt))
}

/** Capture create: no id, no description; the name omitted when blank, snaps omitted without a photo. */
export function captureSavePayload(input: {
  name: string
  happenedAt: string
  snap: PebbleSnap | undefined
}): SaveMomentPayload {
  const name = input.name.trim()
  return {
    ...(name !== "" && { name }),
    happened_at: whenPayload(input.happenedAt),
    ...(input.snap && { snaps: [snapPayload(input.snap, 0)] }),
  }
}

/**
 * Edit moment: every field. `""` clears the name or the description, and
 * `snaps` is the whole set, so `[]` removes the photo (R6).
 */
export function editMomentSavePayload(
  id: string,
  input: { name: string; description: string; happenedAt: string; snaps: readonly PebbleSnap[] },
): SaveMomentPayload {
  return {
    id: id.toLowerCase(),
    name: input.name.trim(),
    description: input.description.trim(),
    happened_at: whenPayload(input.happenedAt),
    snaps: input.snaps.map((s, i) => snapPayload(s, i)),
  }
}

/** Null unless the draft is complete. Every key is always sent (R13). */
export function completePebblePayload(momentId: string, draft: CompleteDraft): CompletePebblePayload | null {
  if (!draft.valenceConfirmed || draft.emotionId === null || draft.domainIds.length === 0) return null
  return {
    moment_id: momentId.toLowerCase(),
    emotion_id: draft.emotionId.toLowerCase(),
    intensity: draft.intensity,
    positiveness: draft.positiveness,
    visibility: draft.visibility,
    domain_ids: draft.domainIds.map((id) => id.toLowerCase()),
    soul_ids: draft.soulIds.map((id) => id.toLowerCase()),
    collection_ids: draft.collectionIds.map((id) => id.toLowerCase()),
  }
}
