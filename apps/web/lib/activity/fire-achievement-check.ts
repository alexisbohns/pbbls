import { notifyAchievements } from "@/lib/activity/achievement-activity"
import type { DataProvider } from "@/lib/data/data-provider"

/**
 * Fire-and-forget achievement evaluation after a count-changing mutation (D5).
 * Call AFTER the mutation succeeded and the store snapshot is pushed — this
 * never blocks and never throws: the mutation already landed, and a failed
 * check self-heals by construction (the next call re-evaluates live counts).
 *
 * The catalog fetch runs only when something actually unlocked (rare), and
 * joins the returned slugs to their rows so the pill can localize titles from
 * family i18n + admin overrides inside React — no translator plumbing needed
 * at the six call sites.
 */
export function fireAchievementCheck(provider: DataProvider): void {
  void provider
    .checkAchievements()
    .then(async (results) => {
      if (results.length === 0) return
      const catalog = await provider.getAchievements()
      notifyAchievements(results, catalog)
    })
    .catch((err) => {
      console.warn("[achievements] check_achievements failed", err)
    })
}
