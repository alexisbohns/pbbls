import type { Collection } from "@/lib/types"
import type { CreateCollectionInput, UpdateCollectionInput } from "@/lib/data/data-provider"

// ---------------------------------------------------------------------------
// `collections` rows (R11). The glyph is written as `glyph_id` in the same
// insert or update as name and mode, never through `set_collection_glyph`.
// A glyph the owner cannot use is refused by the `collections_glyph_usable`
// trigger (42501); the form maps any refusal to the generic copy.
// ---------------------------------------------------------------------------

type Mode = NonNullable<Collection["mode"]>

function modeOf(raw: unknown): Mode | undefined {
  return raw === "stack" || raw === "pack" || raw === "track" ? raw : undefined
}

/** One `collections.*` row, with its pebble ids from `collection_pebbles`. */
export function collectionFromRow(row: Record<string, unknown>, pebbleIds: string[]): Collection {
  return {
    id: String(row.id),
    name: typeof row.name === "string" ? row.name : "",
    mode: modeOf(row.mode),
    glyph_id: typeof row.glyph_id === "string" && row.glyph_id !== "" ? row.glyph_id : null,
    pebble_ids: pebbleIds,
    created_at: typeof row.created_at === "string" ? row.created_at : "",
    updated_at: typeof row.updated_at === "string" ? row.updated_at : "",
  }
}

/** The insert: name, mode and glyph in one statement. */
export function collectionInsertRow(userId: string, input: CreateCollectionInput) {
  return {
    user_id: userId,
    name: input.name,
    mode: input.mode ?? null,
    glyph_id: input.glyph_id,
  }
}

/**
 * The update's columns, or null when none is touched (a pebble-only change).
 * `glyph_id: null` clears the glyph ("Remove glyph"); an absent key leaves it.
 */
export function collectionUpdateRow(input: UpdateCollectionInput): Record<string, unknown> | null {
  const updates: Record<string, unknown> = {}
  if (input.name !== undefined) updates.name = input.name
  if (input.mode !== undefined) updates.mode = input.mode
  if (input.glyph_id !== undefined) updates.glyph_id = input.glyph_id
  return Object.keys(updates).length > 0 ? updates : null
}
