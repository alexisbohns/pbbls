import { presentAchievementMoment } from "@/lib/activity/achievement-moment"
import type { DataProvider } from "@/lib/data/data-provider"

/**
 * A pulled ceremony (#1308, D9) in the existing unlock moment: its
 * set_complete row's card, titled as the catalog names it, with the karma the
 * pull paid (0 shows no karma line: an unpriced row, or a pull settled only by
 * the re-read). `fallback` names it when the catalog cannot be read, and gives
 * the description the row does not carry. Fire-and-forget: a failed catalog
 * read is logged and the moment still plays from the fallback.
 */
export function presentCeremony(
  provider: Pick<DataProvider, "getAchievements">,
  slug: string,
  karmaGranted: number,
  fallback: { title: string; description: string },
): Promise<void> {
  return provider
    .getAchievements()
    .catch((err: unknown) => {
      console.warn("[achievements] catalog read for the ceremony failed", err)
      return []
    })
    .then((catalog) => presentAchievementMoment({ results: [{ slug, karmaGranted }], catalog, fallback: { [slug]: fallback } }))
}
