import { afterEach, describe, expect, it, vi } from "vitest"
import { subscribeAchievementMoment, type AchievementMoment } from "./achievement-moment"
import { presentCeremony } from "./present-ceremony"

afterEach(() => vi.restoreAllMocks())

describe("presentCeremony", () => {
  it("plays the stack's card with the catalog and its fallback words", async () => {
    const seen: AchievementMoment[] = []
    const off = subscribeAchievementMoment((m) => seen.push(m))
    await presentCeremony({ getAchievements: vi.fn(async () => []) }, "complete-six-firsts", 0, { title: "The six firsts", description: "Small, medium and large." })
    off()
    expect(seen).toEqual([
      {
        results: [{ slug: "complete-six-firsts", karmaGranted: 0 }],
        catalog: [],
        fallback: { "complete-six-firsts": { title: "The six firsts", description: "Small, medium and large." } },
      },
    ])
  })

  it("still plays when the catalog cannot be read, and logs it", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
    const seen: AchievementMoment[] = []
    const off = subscribeAchievementMoment((m) => seen.push(m))
    await presentCeremony(
      { getAchievements: vi.fn(async () => Promise.reject(new Error("offline"))) },
      "complete-emotions",
      20,
      { title: "Every emotion", description: "" },
    )
    off()
    expect(seen[0].results).toEqual([{ slug: "complete-emotions", karmaGranted: 20 }])
    expect(warn).toHaveBeenCalled()
  })
})
