import { presentAchievementMoment } from "@/lib/activity/achievement-moment"
import type { DataProvider } from "@/lib/data/data-provider"

/**
 * Fire-and-forget achievement evaluation after a count-changing mutation (D5).
 * Call AFTER the mutation succeeded and the store snapshot is pushed — this
 * never blocks and never throws: the mutation already landed, and a failed
 * check self-heals by construction (the next call re-evaluates live counts).
 *
 * The catalog fetch runs only when something actually unlocked (rare), and
 * joins the returned slugs to their rows so the moment can localize titles
 * from family i18n + admin overrides inside React — no translator plumbing
 * needed at the six call sites.
 *
 * This is the MUTATION path, and only it celebrates (D13): the screen-open
 * call in `useAchievements` is the retroactive grant and can return a
 * veteran's entire history at once, so it renders in the grid instead.
 */
export function fireAchievementCheck(provider: DataProvider): void {
  void provider
    .checkAchievements()
    .then(async (results) => {
      if (results.length === 0) return
      const catalog = await provider.getAchievements()
      presentAchievementMoment({ results, catalog })
    })
    .catch((err) => {
      console.warn("[achievements] check_achievements failed", err)
    })
}
