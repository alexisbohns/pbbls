import type {
  CompletedMoment,
  Intensity,
  Moment,
  MomentPebble,
  PebbleCard,
  Positiveness,
  StoneLook,
  Visibility,
} from "@/lib/types"
import { pebbleCover, pebbleGlyphFromRow, snapOriginalPath, sortedSnaps } from "@/lib/data/pebble-row"

// ---------------------------------------------------------------------------
// `v_moments_full` decoding (#1050). The view nests the CALLER's pebble under
// `pebble` (null = a bare moment), its snaps under `snaps`. Every read here is
// tolerant: rows come from four surfaces and two eras of precision, so a field
// that is off maps to "absent" instead of failing the whole load. Timestamps
// stay the server's strings; consumers parse them with `new Date()`, which
// accepts both whole-second and microsecond forms.
// ---------------------------------------------------------------------------

function stringOrNull(raw: unknown): string | null {
  return typeof raw === "string" && raw !== "" ? raw : null
}

function stringOr(raw: unknown, fallback: string): string {
  return typeof raw === "string" ? raw : fallback
}

/** The ids of a jsonb array of `{ id, … }` embeds, skipping anything malformed. */
function idsOf(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  return raw.flatMap((item: unknown) => {
    if (typeof item !== "object" || item === null) return []
    const id = (item as Record<string, unknown>).id
    return typeof id === "string" ? [id] : []
  })
}

function cardsOf(raw: unknown): PebbleCard[] {
  if (!Array.isArray(raw)) return []
  return raw.flatMap((item: unknown) => {
    if (typeof item !== "object" || item === null) return []
    const c = item as Record<string, unknown>
    if (typeof c.species_id !== "string" || typeof c.value !== "string") return []
    return [{ species_id: c.species_id, value: c.value }]
  })
}

function isIntensity(raw: unknown): raw is Intensity {
  return raw === 1 || raw === 2 || raw === 3
}

function isPositiveness(raw: unknown): raw is Positiveness {
  return raw === -1 || raw === 0 || raw === 1
}

function isVisibility(raw: unknown): raw is Visibility {
  return raw === "secret" || raw === "private" || raw === "public"
}

/**
 * `v_moments_full.pebble`, or null: for a bare moment (JSON null), for anything
 * that is not an object, and for a pebble missing its id or emotion. A valence
 * or visibility outside the schema's CHECK sets is logged and drawn as no
 * pebble (a blank stone) rather than crashing a lookup table downstream.
 */
export function momentPebbleFromRow(raw: unknown): MomentPebble | null {
  if (typeof raw !== "object" || raw === null) return null
  const p = raw as Record<string, unknown>
  if (typeof p.id !== "string" || typeof p.emotion_id !== "string") return null
  if (!isIntensity(p.intensity) || !isPositiveness(p.positiveness)) {
    console.warn("[moments] unexpected valence", p.id)
    return null
  }
  if (!isVisibility(p.visibility)) {
    console.warn("[moments] unexpected visibility", p.id)
    return null
  }
  const createdAt = stringOr(p.created_at, "")
  return {
    id: p.id,
    intensity: p.intensity,
    positiveness: p.positiveness,
    visibility: p.visibility,
    emotion_id: p.emotion_id,
    soul_ids: idsOf(p.souls),
    domain_ids: idsOf(p.domains),
    collection_ids: idsOf(p.collections),
    cards: cardsOf(p.cards),
    glyph: pebbleGlyphFromRow(p.glyph),
    created_at: createdAt,
    updated_at: stringOr(p.updated_at, createdAt),
  }
}

/**
 * Map one `v_moments_full` row to a `Moment`. Signed snap URLs are minted in
 * one batch by the caller and looked up here by path. `name` and `description`
 * stay `string | null`, with `""` read as null (web wrote `""` before the
 * split). `updated_at` falls back to `created_at` for a select that omits it.
 */
export function momentFromViewRow(
  row: Record<string, unknown>,
  signedUrlByPath: ReadonlyMap<string, string>,
): Moment {
  const snaps = sortedSnaps(row.snaps)
  const instants = snaps
    .map((s) => signedUrlByPath.get(snapOriginalPath(s)))
    .filter((url): url is string => typeof url === "string")
  const createdAt = stringOr(row.created_at, "")
  return {
    id: stringOr(row.id, ""),
    name: stringOrNull(row.name),
    description: stringOrNull(row.description),
    happened_at: stringOr(row.happened_at, ""),
    snaps,
    instants,
    cover: pebbleCover(snaps, signedUrlByPath),
    created_at: createdAt,
    updated_at: stringOr(row.updated_at, createdAt),
    pebble: momentPebbleFromRow(row.pebble),
  }
}

export function isCompleted(m: Moment): m is CompletedMoment {
  return m.pebble !== null
}

/**
 * The look a moment's stone is drawn from, or null for a bare moment (the
 * blank stone, R4). The fossil reads the PEBBLE's `created_at`, as the flat
 * pebble did: the record date is when it was made a pebble.
 */
export function stoneLookOf(m: CompletedMoment): StoneLook
export function stoneLookOf(m: Moment): StoneLook | null
export function stoneLookOf(m: Moment): StoneLook | null {
  const p = m.pebble
  if (!p) return null
  return {
    intensity: p.intensity,
    positiveness: p.positiveness,
    emotion_id: p.emotion_id,
    glyph: p.glyph,
    happened_at: m.happened_at,
    created_at: p.created_at,
    name: m.name,
  }
}

/** The name to show: trimmed, and null when blank (the caller says "Untitled"). */
export function momentDisplayName(name: string | null): string | null {
  const trimmed = name?.trim() ?? ""
  return trimmed === "" ? null : trimmed
}

/** Newest `happened_at` first, then id descending so equal instants keep one order. */
export function compareMomentsNewestFirst(a: Moment, b: Moment): number {
  const byTime = Date.parse(b.happened_at) - Date.parse(a.happened_at)
  if (byTime !== 0 && !Number.isNaN(byTime)) return byTime
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0
}

/** The snap directories `delete_moment` returns (`{ storage_paths: [...] }`) for file cleanup. */
export function deleteMomentPaths(raw: unknown): string[] {
  if (typeof raw !== "object" || raw === null) return []
  const paths = (raw as Record<string, unknown>).storage_paths
  if (!Array.isArray(paths)) return []
  return paths.filter((p): p is string => typeof p === "string" && p !== "")
}
