import type { Pebble } from "@/lib/types"

export type WeekGroup = {
  /** ISO 8601 year for the week (per ISO `yearForWeekOfYear`). */
  weekYear: number
  /** ISO 8601 week number (1..53). */
  weekNumber: number
  /** Stable key combining year + week, e.g. `2026-W19`. */
  weekKey: string
  /** Localized label, e.g. `Week 19` / `Semaine 19`. */
  label: string
  pebbles: Pebble[]
}

export type WeekLabelFormatter = (weekNumber: number) => string

/**
 * Groups pebbles by their ISO 8601 week (Mon-start, week 1 contains the
 * year's first Thursday). Mirrors the iOS `groupPebblesByISOWeek` helper
 * shipped in PR #378 so the two clients agree on bucket boundaries —
 * notably for dates that straddle a calendar-year boundary while sharing
 * a single ISO week (e.g. 2025-12-29 + 2026-01-02 both land in ISO week 1
 * of 2026).
 *
 * Returns groups ordered descending by `(weekYear, weekNumber)`. Within
 * a group, input order is preserved — callers typically pass pebbles
 * already sorted descending by `happened_at`.
 */
export function groupPebblesByISOWeek(
  pebbles: Pebble[],
  formatLabel: WeekLabelFormatter,
): WeekGroup[] {
  const sorted = [...pebbles].sort(
    (a, b) => new Date(b.happened_at).getTime() - new Date(a.happened_at).getTime(),
  )

  const groupMap = new Map<string, WeekGroup>()
  const order: string[] = []

  for (const pebble of sorted) {
    const { weekYear, weekNumber } = isoWeekOf(new Date(pebble.happened_at))
    const weekKey = `${weekYear}-W${String(weekNumber).padStart(2, "0")}`
    const existing = groupMap.get(weekKey)
    if (existing) {
      existing.pebbles.push(pebble)
    } else {
      groupMap.set(weekKey, {
        weekYear,
        weekNumber,
        weekKey,
        label: formatLabel(weekNumber),
        pebbles: [pebble],
      })
      order.push(weekKey)
    }
  }

  return order.map((key) => groupMap.get(key)!)
}

/**
 * ISO 8601 week-numbering algorithm (a.k.a. "ordinal-week of the
 * week-numbering year"): the week containing each year's first Thursday
 * is week 1. Computed in UTC to keep boundaries locale-independent — all
 * users see the same week 1 — matching iOS's
 * `Calendar(identifier: .iso8601)`.
 */
function isoWeekOf(date: Date): { weekYear: number; weekNumber: number } {
  const utc = new Date(
    Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()),
  )
  // Shift to the Thursday of the same ISO week — that anchors the
  // week-numbering year, since week 1 is by definition the week
  // containing Thursday Jan 4 (equivalently, the year's first Thursday).
  const dayNum = utc.getUTCDay() || 7 // Sun=7
  utc.setUTCDate(utc.getUTCDate() + 4 - dayNum)
  const weekYear = utc.getUTCFullYear()
  const yearStart = new Date(Date.UTC(weekYear, 0, 1))
  const weekNumber = Math.ceil(
    ((utc.getTime() - yearStart.getTime()) / 86_400_000 + 1) / 7,
  )
  return { weekYear, weekNumber }
}
