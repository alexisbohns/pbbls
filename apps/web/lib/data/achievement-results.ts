import type { AchievementUnlockResult } from "@/lib/data/data-provider"

/**
 * `check_achievements()` rows (`returns table (slug, karma_granted)`): only the
 * rows to celebrate, since #1305; silent ones are recorded and never returned.
 * A null row, or one without a slug, is dropped alone; a missing karma is 0.
 */
export function unlockResultsFrom(raw: unknown): AchievementUnlockResult[] {
  if (!Array.isArray(raw)) return []
  return raw.flatMap((row: unknown) => {
    if (typeof row !== "object" || row === null) return []
    const slug: unknown = Reflect.get(row, "slug")
    const karma: unknown = Reflect.get(row, "karma_granted")
    if (typeof slug !== "string" || slug === "") return []
    return [{ slug, karmaGranted: typeof karma === "number" && Number.isInteger(karma) ? karma : 0 }]
  })
}
