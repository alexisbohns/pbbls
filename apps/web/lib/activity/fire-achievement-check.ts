import { presentAchievementMoment } from "@/lib/activity/achievement-moment"
import { beginAchievementCheck, endAchievementCheck } from "@/lib/activity/achievement-check-flight"
import { browserStorage, markAchievementsCachesStale } from "@/lib/data/achievements-cache"
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
 * This is the MUTATION path, and only it celebrates (D13). The screen-open
 * calls (the achievements page's and the Profile shelf's reads, through
 * `AchievementsPageStore`) are the retroactive grant: they can return a
 * veteran's entire history at once, and never celebrate.
 *
 * Something unlocked, so the kept achievements payload no longer shows it:
 * it is marked stale, and the Profile shelf's next open reads (D18).
 */
export function fireAchievementCheck(provider: Pick<DataProvider, "checkAchievements" | "getAchievements">): void {
  // While it runs, the Profile shelf's grant waits its turn (achievement-check-flight).
  beginAchievementCheck()
  void provider
    .checkAchievements()
    .finally(endAchievementCheck)
    .then(async (results) => {
      if (results.length === 0) return
      const storage = browserStorage()
      if (storage) markAchievementsCachesStale(storage)
      const catalog = await provider.getAchievements()
      presentAchievementMoment({ results, catalog })
    })
    .catch((err) => {
      console.warn("[achievements] check_achievements failed", err)
    })
}
