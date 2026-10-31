/**
 * The seeded generator every Pebbles stone is drawn from (#1384, spec D7): iOS
 * `StoneRandom`, as the 2026-10-31 exploration kit ports it. A murmur-style hash
 * of the seed's UTF-16 code units, then an xorshift-multiply step per draw, all
 * in unsigned 32-bit arithmetic, so a seed gives the same stone on every surface.
 */
export function stoneRandom(seed: string): () => number {
  let h = (1779033703 ^ seed.length) >>> 0
  for (let i = 0; i < seed.length; i++) {
    h = Math.imul(h ^ seed.charCodeAt(i), 3432918353) >>> 0
    h = ((h << 13) | (h >>> 19)) >>> 0
  }
  return () => {
    h = Math.imul(h ^ (h >>> 16), 2246822507) >>> 0
    h = Math.imul(h ^ (h >>> 13), 3266489909) >>> 0
    h = (h ^ (h >>> 16)) >>> 0
    return h / 4294967296
  }
}

/** A symmetric offset of at most `amount` from a uniform draw `u` in [0, 1). */
export function jitter(u: number, amount: number): number {
  return (u - 0.5) * 2 * amount
}
