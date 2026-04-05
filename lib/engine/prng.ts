/**
 * Deterministic seeded PRNG using the mulberry32 algorithm.
 *
 * Identical seeds produce identical sequences across invocations.
 * No DOM or React dependencies — pure TypeScript.
 */

export type PRNG = {
  /** Random float in [0, 1). */
  next(): number
  /** Random integer in [min, max] (inclusive). */
  nextInt(min: number, max: number): number
  /** Random float in [min, max). */
  nextFloat(min: number, max: number): number
  /** Select a random element from a non-empty array. Throws if empty. */
  pick<T>(array: readonly T[]): T
}

/**
 * Create a deterministic PRNG seeded with a 32-bit integer.
 *
 * Uses the mulberry32 algorithm. Pair with `hashUUID` to derive a seed
 * from a pebble ID:
 *
 * ```ts
 * const rng = createPRNG(hashUUID(pebble.id))
 * const hue = rng.nextInt(0, 360)
 * ```
 */
export function createPRNG(seed: number): PRNG {
  let state = seed | 0

  function next(): number {
    state = (state + 0x6d2b79f5) | 0
    let t = Math.imul(state ^ (state >>> 15), 1 | state)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 0x100000000
  }

  function nextInt(min: number, max: number): number {
    return min + Math.floor(next() * (max - min + 1))
  }

  function nextFloat(min: number, max: number): number {
    return min + next() * (max - min)
  }

  function pick<T>(array: readonly T[]): T {
    if (array.length === 0) {
      throw new Error("Cannot pick from an empty array")
    }
    return array[nextInt(0, array.length - 1)]
  }

  return { next, nextInt, nextFloat, pick }
}
