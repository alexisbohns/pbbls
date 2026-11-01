import { isoWeekStart, type WeekRollEntry } from "@/lib/utils/week-roll-entries"

/**
 * The Path's week focus, as pure functions (#1384, two-area spec D4): which
 * entry a focus or a `?week=` lands on. They left `PathScreen` so the focus
 * can be shared by the Path's two halves.
 */

/**
 * The entry whose `weekStart` is closest to `preferred`; on a tie, the
 * earlier week. The caller guarantees `entries.length > 0`.
 */
export function closestEntry(entries: readonly WeekRollEntry[], preferred: Date): WeekRollEntry {
  return entries.reduce((best, e) => {
    const dBest = Math.abs(best.weekStart.getTime() - preferred.getTime())
    const dE = Math.abs(e.weekStart.getTime() - preferred.getTime())
    if (dE < dBest) return e
    if (dE === dBest && e.weekStart.getTime() < best.weekStart.getTime()) return e
    return best
  }, entries[0])
}

/**
 * The week key a `?week=` Monday (UTC midnight, #1384 D9) lands on: the
 * closest week the Path has, read in the reader's calendar. Null when the
 * Path has no week yet.
 */
export function weekParamKey(focusWeek: Date, entries: readonly WeekRollEntry[]): string | null {
  if (entries.length === 0) return null
  const monday = new Date(focusWeek.getUTCFullYear(), focusWeek.getUTCMonth(), focusWeek.getUTCDate())
  return closestEntry(entries, isoWeekStart(monday)).weekStartIso
}

/** The focused entry: the one with `key`, else the week closest to today. */
export function focusedEntryOf(
  entries: readonly WeekRollEntry[],
  key: string,
  today: Date,
): WeekRollEntry | undefined {
  return entries.find((e) => e.weekStartIso === key) ?? (entries.length > 0 ? closestEntry(entries, isoWeekStart(today)) : undefined)
}

/** The neighbouring week's key for an arrow key, or null at either end. */
export function steppedKey(entries: readonly WeekRollEntry[], key: string, step: -1 | 1): string | null {
  const idx = entries.findIndex((entry) => entry.weekStartIso === key)
  return entries[idx + step]?.weekStartIso ?? null
}
