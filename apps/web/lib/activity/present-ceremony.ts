import { presentAchievementMoment } from "@/lib/activity/achievement-moment"

/**
 * A pulled ceremony (#1308, D9) in the existing unlock moment, at once: its
 * set_complete card named by `words` (iOS's ceremony title, which is the
 * catalog's, and its body), with the karma the pull paid (0 shows no karma
 * line: an unpriced row, or a pull settled only by the re-read). No catalog
 * read stands between the pull and its moment.
 */
export function presentCeremony(slug: string, karmaGranted: number, words: { title: string; description: string }): void {
  presentAchievementMoment({ results: [{ slug, karmaGranted }], catalog: [], fallback: { [slug]: words } })
}
