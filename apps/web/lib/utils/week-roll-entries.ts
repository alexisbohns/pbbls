import { addWeeks } from "date-fns"
import type { Pebble } from "@/lib/types"

export type WeekRollEntry = {
  weekStart: Date           // ISO Monday 00:00 local
  weekStartIso: string      // "YYYY-Www" stable key (e.g. "2026-W19")
  isoWeek: number           // 1..53
  pebbles: Pebble[]
}

/** ISO 8601 week start (Monday 00:00 local) for the given date. */
export function isoWeekStart(d: Date): Date {
  const out = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const day = out.getDay()                   // 0 = Sun, 1 = Mon, ..., 6 = Sat
  const delta = day === 0 ? -6 : 1 - day     // shift back to Monday
  out.setDate(out.getDate() + delta)
  out.setHours(0, 0, 0, 0)
  return out
}

/** ISO 8601 week number (1..53). Mirrors `Date.weekOfYear` from Swift. */
export function isoWeekNumber(d: Date): number {
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  // ISO weeks: Thursday in current week decides the year.
  const dayNr = (target.getDay() + 6) % 7    // Mon = 0, Sun = 6
  target.setDate(target.getDate() - dayNr + 3)
  const firstThursday = new Date(target.getFullYear(), 0, 4)
  const firstThursdayDayNr = (firstThursday.getDay() + 6) % 7
  firstThursday.setDate(firstThursday.getDate() - firstThursdayDayNr + 3)
  const diff = target.getTime() - firstThursday.getTime()
  return 1 + Math.round(diff / (7 * 24 * 60 * 60 * 1000))
}

/** ISO 8601 year-for-week (e.g. 2025-12-30 is in ISO year 2026). */
export function isoWeekYear(d: Date): number {
  const target = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const dayNr = (target.getDay() + 6) % 7
  target.setDate(target.getDate() - dayNr + 3)
  return target.getFullYear()
}

/** Stable string key for a week, e.g. "2026-W19". */
export function isoWeekKey(d: Date): string {
  const year = isoWeekYear(d)
  const week = isoWeekNumber(d)
  return `${year}-W${String(week).padStart(2, "0")}`
}

/** Index of `weekStart` in `entries`. Returns -1 if not found. */
export function weekIndex(entries: WeekRollEntry[], weekStart: Date): number {
  const key = isoWeekKey(weekStart)
  return entries.findIndex((e) => e.weekStartIso === key)
}

/**
 * Build the weeks roll: union of weeks that contain pebbles with the
 * current and next week, sorted ascending by `weekStart`. Past weeks
 * have their pebbles sorted oldest-first; current and future weeks
 * sort newest-first. Pivot is strict `weekStart < currentWeekStart`.
 */
export function buildWeekRollEntries(
  pebbles: Pebble[],
  today: Date,
): WeekRollEntry[] {
  const currentStart = isoWeekStart(today)
  const nextStart = isoWeekStart(addWeeks(today, 1))

  const bucket = new Map<string, { weekStart: Date; pebbles: Pebble[] }>()
  const seed = (date: Date) => {
    const key = isoWeekKey(date)
    if (!bucket.has(key)) {
      bucket.set(key, { weekStart: isoWeekStart(date), pebbles: [] })
    }
    return bucket.get(key)!
  }

  seed(currentStart)
  seed(nextStart)

  for (const p of pebbles) {
    const happened = new Date(p.happened_at)
    seed(happened).pebbles.push(p)
  }

  const entries: WeekRollEntry[] = []
  for (const { weekStart, pebbles: bucketPebbles } of bucket.values()) {
    const isPast = weekStart.getTime() < currentStart.getTime()
    const sorted = [...bucketPebbles].sort((a, b) => {
      const aT = new Date(a.happened_at).getTime()
      const bT = new Date(b.happened_at).getTime()
      return isPast ? aT - bT : bT - aT
    })
    entries.push({
      weekStart,
      weekStartIso: isoWeekKey(weekStart),
      isoWeek: isoWeekNumber(weekStart),
      pebbles: sorted,
    })
  }

  entries.sort((a, b) => a.weekStart.getTime() - b.weekStart.getTime())
  return entries
}

/**
 * Format a week's date range, locale-aware. Appends ` · YYYY` when the
 * focused-week year differs from today's calendar year.
 */
export function formatWeekRange(
  weekStart: Date,
  today: Date,
  locale: string,
): string {
  const end = new Date(weekStart)
  end.setDate(end.getDate() + 6)
  const fmt = new Intl.DateTimeFormat(locale, { month: "short", day: "numeric" })
  const range = `${fmt.format(weekStart)} – ${fmt.format(end)}`
  return weekStart.getFullYear() === today.getFullYear()
    ? range
    : `${range} · ${weekStart.getFullYear()}`
}
