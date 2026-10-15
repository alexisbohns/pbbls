import type { SharedConnectionPebble, Visibility } from "@/lib/types"
import { pebbleGlyphFromRow, pebbleNameFromRow } from "@/lib/data/pebble-row"

function isVisibility(raw: unknown): raw is Visibility {
  return raw === "secret" || raw === "private" || raw === "public"
}

/**
 * One row of a connection's shared pebbles: `pebbles` with its `emotion` and
 * `moment` (name, happened_at) embeds as PostgREST returns them, to-one embeds
 * as objects, and `glyph` from the `pebbles.glyph` computed field (#1050), which
 * answers what `resolve_pebble_glyph` would for the caller.
 * Null on a broken row (a missing moment or an incomplete emotion embed), so
 * the caller drops it and logs it instead of rendering an empty emotion.
 * `happened_at` stays the server's string, whatever its precision.
 */
export function sharedConnectionPebbleFromRow(
  row: Record<string, unknown>,
): SharedConnectionPebble | null {
  if (typeof row.id !== "string" || !isVisibility(row.visibility)) return null
  if (typeof row.intensity !== "number" || typeof row.positiveness !== "number") return null

  const moment = row.moment
  if (typeof moment !== "object" || moment === null) return null
  const m = moment as Record<string, unknown>
  if (typeof m.happened_at !== "string") return null

  const emotion = row.emotion
  if (typeof emotion !== "object" || emotion === null) return null
  const e = emotion as Record<string, unknown>
  if (
    typeof e.id !== "string" ||
    typeof e.slug !== "string" ||
    typeof e.name !== "string" ||
    typeof e.color !== "string"
  ) {
    return null
  }

  return {
    id: row.id,
    name: pebbleNameFromRow(m.name),
    happened_at: m.happened_at,
    visibility: row.visibility,
    intensity: row.intensity,
    positiveness: row.positiveness,
    emotion: { id: e.id, slug: e.slug, name: e.name, color: e.color },
    glyph: pebbleGlyphFromRow(row.glyph),
  }
}

/**
 * Newest moment first, then id descending so equal instants keep one order
 * across loads. Client side: PostgREST cannot order parents by an embedded column.
 */
export function compareSharedNewestFirst(a: SharedConnectionPebble, b: SharedConnectionPebble): number {
  const byTime = Date.parse(b.happened_at) - Date.parse(a.happened_at)
  if (byTime !== 0) return byTime
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0
}
