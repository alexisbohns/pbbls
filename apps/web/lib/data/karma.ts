// ---------------------------------------------------------------------------
// Karma — pure utility for computing karma earned from a pebble.
// No side effects, no dependencies on the provider or localStorage.
// Must stay pure for future PostgreSQL port.
// ---------------------------------------------------------------------------

import type { CreatePebbleInput } from "@/lib/data/data-provider"
import type { Pebble } from "@/lib/types"

/**
 * Compute the karma delta for a pebble based on enrichment depth.
 *
 * Mirrors the SQL `compute_karma_delta` RPC (packages/supabase/supabase/migrations/
 * 20260411000005_security_hardening.sql) — keep the two in sync:
 *
 * +1 base (pebble created)
 * +1 if description is non-empty (pearl)
 * +1 per card with non-empty value, capped at 4
 * +1 if at least one soul attached
 * +1 if at least one domain attached
 * +1 if a glyph is attached
 * +1 if at least one snap is attached
 * total capped at 10
 */
export function computeKarmaDelta(pebble: CreatePebbleInput | Pebble): number {
  let delta = 1

  if (pebble.description?.trim()) delta += 1
  if (pebble.cards?.length) delta += Math.min(pebble.cards.filter((c) => c.value.trim()).length, 4)
  if (pebble.soul_ids.length > 0) delta += 1
  if (pebble.domain_ids.length > 0) delta += 1
  if (pebble.mark_id) delta += 1
  if (pebble.snaps.length > 0) delta += 1

  return Math.min(delta, 10)
}
