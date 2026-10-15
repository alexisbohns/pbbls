import type { MarkStroke, PebbleCover, PebbleGlyph, PebbleSnap, SharedPebble } from "@/lib/types"

/** Snap files live at `{storage_path}/original.jpg`; this is the signing key. */
export function snapOriginalPath(snap: PebbleSnap): string {
  return `${snap.storage_path}/original.jpg`
}

/** The snap's thumbnail, same ratio as the original; the Spine probes it. */
export function snapThumbPath(snap: PebbleSnap): string {
  return `${snap.storage_path}/thumb.jpg`
}

/** The snaps in the order the pebble shows them. */
export function sortedSnaps(raw: unknown): PebbleSnap[] {
  return ((raw as PebbleSnap[] | undefined) ?? []).slice().sort((a, b) => a.sort_order - b.sort_order)
}

/** The first snap, with whatever of its two files signed. */
export function pebbleCover(snaps: readonly PebbleSnap[], signedUrlByPath: ReadonlyMap<string, string>): PebbleCover | null {
  const first = snaps[0]
  if (!first) return null
  return {
    snapId: first.id,
    url: signedUrlByPath.get(snapOriginalPath(first)) ?? null,
    thumbUrl: signedUrlByPath.get(snapThumbPath(first)) ?? null,
  }
}

/**
 * A pebble name read tolerantly. After the moments split (D3 there) a moment
 * can carry a photo and no name, and still reach a flat projection (the share
 * page, a connection's pebbles). Null maps to "" so every consumer keeps a
 * string; the Spine shows "untitled" for it.
 */
export function pebbleNameFromRow(raw: unknown): string {
  return typeof raw === "string" ? raw : ""
}

function isStroke(value: unknown): value is MarkStroke {
  if (typeof value !== "object" || value === null) return false
  const s = value as Record<string, unknown>
  return typeof s.d === "string" && typeof s.width === "number"
}

/**
 * A resolved glyph jsonb (`{ id, name, strokes, view_box, source }`, from
 * `v_moments_full.pebble.glyph`, `resolve_pebble_glyph` or `get_shared_pebble`),
 * or null. A malformed embed is
 * treated as no glyph: the pebble then draws without one rather than failing
 * the whole load. Every stroke must carry a string `d` and a numeric `width`;
 * what the `d` says is not checked here, because it only ever reaches the page
 * as a React attribute (`PebbleGlyphStrokes`, #829).
 */
export function pebbleGlyphFromRow(raw: unknown): PebbleGlyph | null {
  if (typeof raw !== "object" || raw === null) return null
  const g = raw as Record<string, unknown>
  if (typeof g.id !== "string" || typeof g.view_box !== "string") return null
  if (!Array.isArray(g.strokes) || !g.strokes.every(isStroke)) return null
  return {
    id: g.id,
    name: typeof g.name === "string" ? g.name : undefined,
    strokes: g.strokes,
    viewBox: g.view_box,
  }
}

/**
 * Map the `get_shared_pebble` jsonb to a `SharedPebble`, or null when it is not
 * one (the RPC answers null for unknown, non-public and hidden ids alike).
 * Snake_case fields pass through; a photo-only moment's null name reads as "",
 * and the glyph is read like the owner's (`pebbleGlyphFromRow`).
 */
export function sharedPebbleFromRpc(raw: unknown): SharedPebble | null {
  if (typeof raw !== "object" || raw === null) return null
  const r = raw as Record<string, unknown>
  if (typeof r.id !== "string" || typeof r.emotion !== "object" || r.emotion === null) return null
  return {
    id: r.id,
    name: pebbleNameFromRow(r.name),
    description: typeof r.description === "string" ? r.description : null,
    happened_at: r.happened_at as string,
    intensity: r.intensity as number,
    positiveness: r.positiveness as number,
    glyph: pebbleGlyphFromRow(r.glyph),
    emotion: r.emotion as SharedPebble["emotion"],
  }
}
