// ---------------------------------------------------------------------------
// Karma — pure utility for computing karma earned from a pebble.
// No side effects, no dependencies on the provider or localStorage.
// Must stay pure for future PostgreSQL port.
// ---------------------------------------------------------------------------

import type { CreatePebbleInput } from "@/lib/data/data-provider"

/**
 * Compute the karma delta for a pebble based on enrichment depth.
 *
 * +1 base (pebble created)
 * +1 if description is non-empty (pearl)
 * +1 per card with non-empty value
 * +1 if at least one soul attached
 * +1 if at least one domain attached
 *
 * // TODO: account for instants (issue #66 follow-up)
 */
export function computeKarmaDelta(pebble: CreatePebbleInput): number {
  let delta = 1

  if (pebble.description?.trim()) delta += 1
  delta += pebble.cards.filter((c) => c.value.trim()).length
  if (pebble.soul_ids.length > 0) delta += 1
  if (pebble.domain_ids.length > 0) delta += 1

  return delta
}
