import type { PebbleGlyph, SharedConnectionPebble, Visibility } from "@/lib/types"
import { pebbleNameFromRow } from "@/lib/data/pebble-row"

function isVisibility(raw: unknown): raw is Visibility {
  return raw === "secret" || raw === "private" || raw === "public"
}

/**
 * One row of a connection's shared pebbles: `pebbles` with its `emotion` and
 * `moment` (name, happened_at) embeds as PostgREST returns them, to-one embeds
 * as objects. The glyph is resolved by a separate `resolve_pebble_glyph` call.
 * Null on a broken row (a missing moment or an incomplete emotion embed), so
 * the caller drops it and logs it instead of rendering an empty emotion.
 * `happened_at` stays the server's string, whatever its precision.
 */
export function sharedConnectionPebbleFromRow(
  row: Record<string, unknown>,
  glyph: PebbleGlyph | null,
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
    glyph,
  }
}

/** Newest moment first. Client side: PostgREST cannot order parents by an embedded column. */
export function compareSharedNewestFirst(a: SharedConnectionPebble, b: SharedConnectionPebble): number {
  return Date.parse(b.happened_at) - Date.parse(a.happened_at)
}
