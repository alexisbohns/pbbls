import type { CompleteDraft } from "@/lib/data/moment-payloads"
import type { Intensity, Positiveness, Visibility } from "@/lib/types"

// ---------------------------------------------------------------------------
// Device-local autosave for a half-finished Complete flow, one entry per
// moment (#1050, PD-W7; iOS `CompleteDraftStore`). Crash insurance, not
// offline support: no sync, no merge. Pure over an injected storage so Vitest
// runs it on a fake; `useCompleteAutosave` binds it to `localStorage` and
// guards every access.
//
// Body: `complete_pebble`'s key names with absent keys omitted, plus the owner
// and the moment. An entry with another owner, a mismatched moment, nothing in
// it, or unreadable JSON is deleted on read.
// ---------------------------------------------------------------------------

export const COMPLETE_DRAFT_PREFIX = "pbbls-complete-draft:"

export type DraftStorage = Pick<Storage, "getItem" | "setItem" | "removeItem" | "key" | "length">

export type CompleteDraftSnapshot = {
  owner_id: string
  moment_id: string
  intensity?: Intensity
  positiveness?: Positiveness
  emotion_id?: string
  domain_ids?: string[]
  soul_ids?: string[]
  collection_ids?: string[]
  visibility?: Visibility
}

function isIntensity(v: unknown): v is Intensity {
  return v === 1 || v === 2 || v === 3
}

function isPositiveness(v: unknown): v is Positiveness {
  return v === -1 || v === 0 || v === 1
}

function isVisibility(v: unknown): v is Visibility {
  return v === "secret" || v === "private" || v === "public"
}

function keyFor(momentId: string): string {
  return COMPLETE_DRAFT_PREFIX + momentId.toLowerCase()
}

function lower(ids: readonly string[]): string[] {
  return ids.map((id) => id.toLowerCase())
}

/** Nothing the user picked. Visibility is defaulted, so it never counts. */
function isEmptySnapshot(s: CompleteDraftSnapshot): boolean {
  return (
    s.intensity === undefined &&
    s.emotion_id === undefined &&
    (s.domain_ids?.length ?? 0) === 0 &&
    (s.soul_ids?.length ?? 0) === 0 &&
    (s.collection_ids?.length ?? 0) === 0
  )
}

/**
 * What gets written, or null when there is nothing worth keeping. The valence
 * is only kept once confirmed: the seed is what the user sees, not a choice.
 */
export function snapshotFromDraft(
  ownerId: string,
  momentId: string,
  d: CompleteDraft,
): CompleteDraftSnapshot | null {
  const snap: CompleteDraftSnapshot = {
    owner_id: ownerId.toLowerCase(),
    moment_id: momentId.toLowerCase(),
    ...(d.valenceConfirmed && { intensity: d.intensity, positiveness: d.positiveness }),
    ...(d.emotionId !== null && { emotion_id: d.emotionId.toLowerCase() }),
    ...(d.domainIds.length > 0 && { domain_ids: lower(d.domainIds) }),
    ...(d.soulIds.length > 0 && { soul_ids: lower(d.soulIds) }),
    ...(d.collectionIds.length > 0 && { collection_ids: lower(d.collectionIds) }),
    visibility: d.visibility,
  }
  return isEmptySnapshot(snap) ? null : snap
}

/**
 * Back to a draft, sanitized: souls and collections are deletable and a draft
 * can outlive them, so an unknown one costs a chip rather than a
 * `soul_not_owned` at the write. Complete holds one domain and one collection.
 */
export function draftFromSnapshot(
  s: CompleteDraftSnapshot,
  known: { soulIds: ReadonlySet<string>; collectionIds: ReadonlySet<string> },
): CompleteDraft {
  const confirmed = s.intensity !== undefined
  return {
    intensity: s.intensity ?? 2,
    positiveness: confirmed ? (s.positiveness ?? 0) : 0,
    valenceConfirmed: confirmed,
    emotionId: s.emotion_id ?? null,
    domainIds: (s.domain_ids ?? []).slice(0, 1),
    soulIds: (s.soul_ids ?? []).filter((id) => known.soulIds.has(id)),
    collectionIds: (s.collection_ids ?? []).filter((id) => known.collectionIds.has(id)).slice(0, 1),
    visibility: s.visibility ?? "secret",
  }
}

function stringArray(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined
  return value.filter((v): v is string => typeof v === "string")
}

/** Shape-checks a parsed body. Malformed optional fields are dropped, not fatal. */
function parseSnapshot(raw: unknown): CompleteDraftSnapshot | null {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) return null
  const o = raw as Record<string, unknown>
  if (typeof o.owner_id !== "string" || typeof o.moment_id !== "string") return null
  const domainIds = stringArray(o.domain_ids)
  const soulIds = stringArray(o.soul_ids)
  const collectionIds = stringArray(o.collection_ids)
  return {
    owner_id: o.owner_id,
    moment_id: o.moment_id,
    ...(isIntensity(o.intensity) && { intensity: o.intensity }),
    ...(isPositiveness(o.positiveness) && { positiveness: o.positiveness }),
    ...(typeof o.emotion_id === "string" && { emotion_id: o.emotion_id }),
    ...(domainIds && { domain_ids: domainIds }),
    ...(soulIds && { soul_ids: soulIds }),
    ...(collectionIds && { collection_ids: collectionIds }),
    ...(isVisibility(o.visibility) && { visibility: o.visibility }),
  }
}

/** The moment's draft for this owner, or null. Anything unusable is removed. */
export function readCompleteDraft(
  store: DraftStorage,
  ownerId: string,
  momentId: string,
): CompleteDraftSnapshot | null {
  const key = keyFor(momentId)
  const raw = store.getItem(key)
  if (raw === null) return null

  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    store.removeItem(key)
    return null
  }

  const snap = parseSnapshot(parsed)
  if (
    !snap ||
    snap.owner_id.toLowerCase() !== ownerId.toLowerCase() ||
    snap.moment_id.toLowerCase() !== momentId.toLowerCase() ||
    isEmptySnapshot(snap)
  ) {
    store.removeItem(key)
    return null
  }
  return snap
}

export function writeCompleteDraft(store: DraftStorage, snap: CompleteDraftSnapshot): void {
  store.setItem(keyFor(snap.moment_id), JSON.stringify(snap))
}

export function clearCompleteDraft(store: DraftStorage, momentId: string): void {
  store.removeItem(keyFor(momentId))
}

/** Every moment's draft, on sign-out and account deletion. Other keys survive. */
export function clearAllCompleteDrafts(store: DraftStorage): void {
  const keys: string[] = []
  for (let i = 0; i < store.length; i++) {
    const key = store.key(i)
    if (key?.startsWith(COMPLETE_DRAFT_PREFIX)) keys.push(key)
  }
  for (const key of keys) store.removeItem(key)
}
