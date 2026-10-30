import { describe, expect, it, vi } from "vitest"
import { achievementCheckInFlight } from "./achievement-check-flight"
import { fireAchievementCheck } from "./fire-achievement-check"
import type { AchievementUnlockResult } from "@/lib/data/data-provider"

describe("a mutation's check in flight", () => {
  it("is counted from the call until it settles, whatever it answers", async () => {
    let settle: (rows: AchievementUnlockResult[]) => void = () => {}
    const provider = {
      checkAchievements: vi.fn(() => new Promise<AchievementUnlockResult[]>((resolve) => (settle = resolve))),
      getAchievements: vi.fn(async () => []),
    }
    expect(achievementCheckInFlight()).toBe(false)
    fireAchievementCheck(provider)
    expect(achievementCheckInFlight()).toBe(true)
    settle([])
    await new Promise((r) => setTimeout(r, 0))
    expect(achievementCheckInFlight()).toBe(false)
  })
})
