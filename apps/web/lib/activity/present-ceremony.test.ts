import { describe, expect, it } from "vitest"
import { subscribeAchievementMoment, type AchievementMoment } from "./achievement-moment"
import { presentCeremony } from "./present-ceremony"

describe("presentCeremony", () => {
  it("plays the stack's card at once, named by its words, with the karma the pull paid", () => {
    const seen: AchievementMoment[] = []
    const off = subscribeAchievementMoment((m) => seen.push(m))
    presentCeremony("complete-six-firsts", 0, { title: "The six firsts", description: "Small, medium and large." })
    off()
    expect(seen).toEqual([
      {
        results: [{ slug: "complete-six-firsts", karmaGranted: 0 }],
        catalog: [],
        fallback: { "complete-six-firsts": { title: "The six firsts", description: "Small, medium and large." } },
      },
    ])
  })
})
