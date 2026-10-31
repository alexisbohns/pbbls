import { decodeAchievementsPage } from "@/lib/data/achievements-payload"
import type { AchievementsPage } from "@/lib/types"

// ---------------------------------------------------------------------------
// The last achievements payload, per user and zone, on this device (#1308,
// spec §4): shown at once while a fresh read loads, kept when that read fails.
// It stores the raw body, not the decoded page, so a decoder fix applies to
// what is already kept. Its days are local to the zone it was read in, so a
// copy read in another zone is a miss (as iOS keys it). Storage can be missing
// or throw (private mode, blocked site data): a failed read is a miss, a
// failed write is logged.
//
// It holds private families (D16), so sign-out clears it (useSupabaseAuth). A
// read in flight across that clear must not write its body back: callers take
// `achievementsCacheEpoch()` before the request and pass it to the write,
// which is skipped once a clear has bumped it.
// ---------------------------------------------------------------------------

export type CacheStorage = Pick<Storage, "getItem" | "setItem">
export type ClearableStorage = Pick<Storage, "length" | "key" | "removeItem">

const KEY_PREFIX = "pbbls-achievements:"
const VERSION = 1

let epoch = 0

export function achievementsCacheKey(userId: string): string {
  return `${KEY_PREFIX}${userId}`
}

/** Bumped by every clear: a write carrying an older epoch is dropped. */
export function achievementsCacheEpoch(): number {
  return epoch
}

export function readAchievementsCache(storage: CacheStorage | null, userId: string, zone: string): AchievementsPage | null {
  if (!storage) return null
  try {
    const text = storage.getItem(achievementsCacheKey(userId))
    if (!text) return null
    const entry: unknown = JSON.parse(text)
    if (typeof entry !== "object" || entry === null) return null
    if (Reflect.get(entry, "v") !== VERSION || Reflect.get(entry, "zone") !== zone) return null
    const decoded = decodeAchievementsPage(Reflect.get(entry, "payload"))
    if (decoded && decoded.skipped.length > 0) {
      console.warn("[achievements] kept payload sections dropped", decoded.skipped)
    }
    return decoded?.page ?? null
  } catch (err) {
    console.warn("[achievements] cache read failed", err)
    return null
  }
}

export function writeAchievementsCache(
  storage: CacheStorage | null,
  userId: string,
  zone: string,
  payload: unknown,
  readEpoch: number,
): void {
  if (!storage || readEpoch !== epoch) return
  try {
    storage.setItem(achievementsCacheKey(userId), JSON.stringify({ v: VERSION, zone, payload }))
  } catch (err) {
    console.warn("[achievements] cache write failed", err)
  }
}

/** Every user's kept payload on this device (sign-out, account deletion). */
export function clearAchievementsCaches(storage: ClearableStorage): void {
  epoch += 1
  const keys: string[] = []
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i)
    if (key?.startsWith(KEY_PREFIX)) keys.push(key)
  }
  for (const key of keys) storage.removeItem(key)
}

/** `window.localStorage`, or null where there is none or touching it throws. */
export function browserStorage(): CacheStorage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage
  } catch (err) {
    console.warn("[achievements] local storage unavailable", err)
    return null
  }
}
