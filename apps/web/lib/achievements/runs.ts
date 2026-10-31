import type { AchievementsDomains, EmotionSeat } from "@/lib/types"

// Readings of the served lists, never orderings (#1308): the server seats the
// emotions in wheel order then slug, and sends the domains newest first.

/** Consecutive seats of one family, as served. A seat without a family runs with its null neighbours, in ink. */
export type FamilyRun = { family: string | null; start: number; length: number }

export function familyRuns(seats: readonly Pick<EmotionSeat, "family">[]): FamilyRun[] {
  const runs: FamilyRun[] = []
  seats.forEach((seat, index) => {
    const last = runs[runs.length - 1]
    if (last && last.family === seat.family) last.length += 1
    else runs.push({ family: seat.family, start: index, length: 1 })
  })
  return runs
}

/** One domain on the band or the wheel's ring: entered (inked) or not yet. */
export type BandItem = { domainId: string | null; on: boolean; isNew: boolean }

/**
 * The domains in the order they came, then those not yet (D11). The server
 * sends `entered` newest first for the register, so the arrival order is that
 * list read backwards: a reading, never a sort by date.
 */
export function bandItems(domains: AchievementsDomains): BandItem[] {
  return [
    ...[...domains.entered].reverse().map((e) => ({ domainId: e.domainId, on: true, isNew: e.isNew })),
    ...domains.notYet.map((domainId) => ({ domainId, on: false, isNew: false })),
  ]
}
