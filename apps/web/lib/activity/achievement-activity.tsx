import { toast } from "sonner"
import { AchievementUnlockPill } from "@/components/activity/AchievementUnlockPill"
import type { AchievementUnlockResult } from "@/lib/data/data-provider"
import type { Achievement } from "@/lib/types"

// Stable id → a new unlock batch replaces the current pill rather than stacking.
const ACHIEVEMENT_ACTIVITY_ID = "achievement-activity"

/**
 * Fire a glanceable achievement-unlock pill. Several unlocks in one
 * check_achievements() response collapse into ONE pill (D8): a single unlock
 * shows its localized title, several show a count phrase. `catalog` carries
 * the rows matching the returned slugs so the pill can localize titles
 * (family i18n + admin overrides) without another fetch path.
 */
export function notifyAchievements(
  results: AchievementUnlockResult[],
  catalog: Achievement[],
): void {
  if (results.length === 0) return
  toast.custom(
    // Sonner's toast row is full-width on mobile, so center the content-width
    // pill within it — `position="bottom-center"` only centers the container.
    (id) => (
      <div className="flex w-full justify-center">
        <AchievementUnlockPill toastId={id} results={results} catalog={catalog} />
      </div>
    ),
    { id: ACHIEVEMENT_ACTIVITY_ID, duration: 4000 },
  )
}
