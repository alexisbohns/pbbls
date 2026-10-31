/**
 * Memoised stone paths (#1384, spec D7): iOS's `remember`, a 24-entry cache
 * cleared whole when full. Components key it by seed, size in quarter units
 * and options, so a re-render or a sub-quarter-pixel resize costs nothing and a
 * split's 180 × 16 bisections run once per size.
 */
const CAPACITY = 24
const cache = new Map<string, string | string[]>()

export function cachedStone(kind: "stone", key: string, make: () => string): string
export function cachedStone(kind: "split", key: string, make: () => string[]): string[]
export function cachedStone(kind: "stone" | "split", key: string, make: () => string | string[]): string | string[] {
  const full = `${kind}|${key}`
  const hit = cache.get(full)
  if (hit !== undefined) return hit
  if (cache.size >= CAPACITY) cache.clear()
  const made = make()
  cache.set(full, made)
  return made
}

/** The cache key for a stone of `w × h` (quarter units, iOS's key). */
export function stoneKey(seed: string, w: number, h: number, extra: readonly unknown[] = []): string {
  return [seed, Math.round(w * 4), Math.round(h * 4), ...extra.map((v) => JSON.stringify(v ?? null))].join("|")
}

/** For tests: how many entries the cache holds. */
export function stoneCacheSize(): number {
  return cache.size
}
