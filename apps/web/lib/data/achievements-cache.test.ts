import { afterEach, describe, expect, it, vi } from "vitest"
import {
  achievementsCacheEpoch,
  achievementsCacheKey,
  clearAchievementsCaches,
  readAchievementsCache,
  writeAchievementsCache,
} from "./achievements-cache"
import { DUMPED_GET_ACHIEVEMENTS } from "./__fixtures__/achievements-captured"
import { MemoryStorage } from "./__fixtures__/memory-storage"

const PAYLOAD: unknown = JSON.parse(DUMPED_GET_ACHIEVEMENTS)
const PARIS = "Europe/Paris"

afterEach(() => vi.restoreAllMocks())

describe("the achievements cache", () => {
  it("round-trips the raw payload, per user and zone", () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", PARIS, PAYLOAD, achievementsCacheEpoch())
    expect(readAchievementsCache(storage, "alice", PARIS)?.daysKept).toBe(1)
    expect(readAchievementsCache(storage, "bob", PARIS)).toBeNull()
    expect(readAchievementsCache(storage, "alice", "America/New_York")).toBeNull()
  })

  it("misses on a corrupt entry, logging it, and on another version", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const storage = new MemoryStorage()
    storage.setItem(achievementsCacheKey("alice"), "{not json")
    expect(readAchievementsCache(storage, "alice", PARIS)).toBeNull()
    expect(warn).toHaveBeenCalledWith("[achievements] cache read failed", expect.any(SyntaxError))
    storage.setItem(achievementsCacheKey("alice"), JSON.stringify({ v: 0, zone: PARIS, payload: PAYLOAD }))
    expect(readAchievementsCache(storage, "alice", PARIS)).toBeNull()
  })

  it("survives a storage that throws, and works without one", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const throwing = {
      getItem: () => {
        throw new Error("SecurityError")
      },
      setItem: () => {
        throw new Error("QuotaExceededError")
      },
    }
    expect(readAchievementsCache(throwing, "alice", PARIS)).toBeNull()
    writeAchievementsCache(throwing, "alice", PARIS, {}, achievementsCacheEpoch())
    expect(warn).toHaveBeenCalledWith("[achievements] cache write failed", expect.any(Error))
    expect(readAchievementsCache(null, "alice", PARIS)).toBeNull()
    writeAchievementsCache(null, "alice", PARIS, {}, achievementsCacheEpoch())
  })

  it("clears every user's entry and nothing else", () => {
    const storage = new MemoryStorage()
    writeAchievementsCache(storage, "alice", PARIS, {}, achievementsCacheEpoch())
    writeAchievementsCache(storage, "bob", PARIS, {}, achievementsCacheEpoch())
    storage.setItem("pbbls-consent-gate", "alice|v3")
    clearAchievementsCaches(storage)
    expect(storage.length).toBe(1)
    expect(storage.getItem("pbbls-consent-gate")).toBe("alice|v3")
  })

  it("drops a write from a read that started before a clear", () => {
    const storage = new MemoryStorage()
    const before = achievementsCacheEpoch()
    clearAchievementsCaches(storage)
    writeAchievementsCache(storage, "alice", PARIS, PAYLOAD, before)
    expect(storage.length).toBe(0)
    writeAchievementsCache(storage, "alice", PARIS, PAYLOAD, achievementsCacheEpoch())
    expect(storage.length).toBe(1)
  })
})
