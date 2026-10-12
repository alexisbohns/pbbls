import type { MarkStroke, Pebble, PebbleGlyph, PebbleSnap } from "@/lib/types"

/** Snap files live at `{storage_path}/original.jpg`; this is the signing key. */
export function snapOriginalPath(snap: PebbleSnap): string {
  return `${snap.storage_path}/original.jpg`
}

/**
 * A pebble name read tolerantly. After the moments split (D3 there) a moment
 * created on iOS or Android can carry a photo and no name, and still reach the
 * web through the compat `v_pebbles_full`. Null maps to "" so every consumer
 * keeps a string; the Spine shows "untitled" for it.
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
 * The `glyph` jsonb of `v_pebbles_full` (`{ id, name, strokes, view_box }`), or
 * null. A malformed embed is treated as no glyph: the stone then draws without
 * a carving rather than failing the whole Path load.
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
 * Map one `v_pebbles_full` row to a `Pebble`. The render columns are not in the
 * view and come from the base table; signed snap URLs are minted in one batch by
 * the caller and looked up here by `snapOriginalPath`.
 */
export function pebbleFromViewRow(
  row: Record<string, unknown>,
  render: { render_svg: string | null; render_version: string | null },
  signedUrlByPath: ReadonlyMap<string, string>,
): Pebble {
  const snaps = ((row.snaps as PebbleSnap[] | undefined) ?? [])
    .slice()
    .sort((a, b) => a.sort_order - b.sort_order)
  const instants = snaps
    .map((s) => signedUrlByPath.get(snapOriginalPath(s)))
    .filter((url): url is string => typeof url === "string")
  return {
    id: row.id as string,
    name: pebbleNameFromRow(row.name),
    description: (row.description as string) ?? undefined,
    happened_at: row.happened_at as string,
    intensity: row.intensity as 1 | 2 | 3,
    positiveness: row.positiveness as -1 | 0 | 1,
    visibility: (row.visibility as string) as Pebble["visibility"],
    emotion_id: row.emotion_id as string,
    soul_ids: ((row.souls as Array<{ id: string }>) ?? []).map((s) => s.id),
    domain_ids: ((row.domains as Array<{ id: string }>) ?? []).map((d) => d.id),
    collection_ids: ((row.collections as Array<{ id: string }>) ?? []).map((c) => c.id),
    mark_id: (row.glyph_id as string) ?? undefined,
    instants,
    snaps,
    cards: ((row.cards as Array<{ species_id: string; value: string }>) ?? []).map((c) => ({
      species_id: c.species_id,
      value: c.value,
    })),
    render_svg: render.render_svg,
    render_version: render.render_version,
    glyph: pebbleGlyphFromRow(row.glyph),
    created_at: row.created_at as string,
    updated_at: row.updated_at as string,
  }
}
