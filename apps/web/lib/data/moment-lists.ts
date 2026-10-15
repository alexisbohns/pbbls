import type { Collection, CompletedMoment, Moment } from "@/lib/types"
import { compareMomentsNewestFirst, isCompleted } from "@/lib/data/moment-row"

// ---------------------------------------------------------------------------
// The soul and collection detail lists (R9): moments carrying the caller's
// pebble tagged with that soul or collection, newest `happened_at` first. A
// bare moment has no souls or collections, so it never shows here.
// ---------------------------------------------------------------------------

/** The completed moments whose pebble names this soul, newest first. */
export function momentsWithSoul(moments: readonly Moment[], soulId: string): CompletedMoment[] {
  return moments
    .filter(isCompleted)
    .filter((m) => m.pebble.soul_ids.includes(soulId))
    .sort(compareMomentsNewestFirst)
}

/**
 * The completed moments whose pebble is in this collection, newest first.
 * `collection.pebble_ids` holds PEBBLE ids (`collection_pebbles`), never
 * moment ids.
 */
export function momentsInCollection(
  moments: readonly Moment[],
  collection: Pick<Collection, "pebble_ids">,
): CompletedMoment[] {
  const ids = new Set(collection.pebble_ids)
  return moments
    .filter(isCompleted)
    .filter((m) => ids.has(m.pebble.id))
    .sort(compareMomentsNewestFirst)
}

/** How many pebbles name each soul (the souls page). Bare moments count for none. */
export function soulPebbleCounts(moments: readonly Moment[]): Map<string, number> {
  const counts = new Map<string, number>()
  for (const m of moments) {
    if (!isCompleted(m)) continue
    for (const soulId of m.pebble.soul_ids) {
      counts.set(soulId, (counts.get(soulId) ?? 0) + 1)
    }
  }
  return counts
}
