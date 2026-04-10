/**
 * Deterministic UUID-to-seed hashing using FNV-1a.
 *
 * Converts any UUID string into a 32-bit unsigned integer suitable as a PRNG
 * seed. Identical inputs always produce identical outputs across invocations.
 */

/** FNV-1a 32-bit offset basis. */
const FNV_OFFSET = 0x811c9dc5
/** FNV-1a 32-bit prime. */
const FNV_PRIME = 0x01000193

/**
 * Hash a UUID string into a deterministic 32-bit unsigned integer
 * using the FNV-1a algorithm.
 */
export function hashUUID(uuid: string): number {
  let hash = FNV_OFFSET
  for (let i = 0; i < uuid.length; i++) {
    hash ^= uuid.charCodeAt(i)
    hash = Math.imul(hash, FNV_PRIME)
  }
  return hash >>> 0
}
