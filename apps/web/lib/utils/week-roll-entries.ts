import type { Moment } from "@/lib/types"

export type WeekRollEntry = {
  weekStart: Date           // ISO Monday 00:00 local
  weekStartIso: string      // "YYYY-Www" stable key (e.g. "2026-W19")
  isoWeek: number           // 1..53
  /** Every moment of the week, bare ones included: they are on the path too (#1050). */
  moments: Moment[]
  /** Completed moments only: a bare moment is on the path but is not a pebble (PD-W12). */
  pebbleCount: number
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
 * The week to open the Path on for `?focus=<momentId>` (R14): the ISO key of
 * the week the moment happened in, or null while it is not in `moments` (the
 * store has not caught up yet, or the moment is gone).
 */
export function focusKeyFor(moments: readonly Moment[], id: string): string | null {
  const target = id.toLowerCase()
  const moment = moments.find((m) => m.id.toLowerCase() === target)
  if (!moment) return null
  const at = new Date(moment.happened_at)
  return Number.isNaN(at.getTime()) ? null : isoWeekKey(at)
}

/** What a `?focus=` does on this render: the id it consumes and the week it opens, if any. */
export type FocusStep = { consumed: string | null; key: string | null }

/**
 * The `?focus=<momentId>` state machine (R14), run during render. `consumed`
 * is the focus already handled; null means "no change this render".
 *
 * - A new focus whose moment is in the store opens its week and is consumed.
 * - A new focus whose moment is missing waits while the store loads, and is
 *   consumed without paging once loading settles: a focus left armed would
 *   page the Path mid-browse on a later refresh.
 * - A consumed focus is never re-applied; clearing the URL re-arms it.
 */
export function nextFocusStep(
  moments: readonly Moment[],
  focusId: string | null,
  consumed: string | null,
  loading: boolean,
): FocusStep | null {
  if (focusId === null) return consumed === null ? null : { consumed: null, key: null }
  if (focusId === consumed) return null
  const key = focusKeyFor(moments, focusId)
  if (key !== null) return { consumed: focusId, key }
  return loading ? null : { consumed: focusId, key: null }
}

/**
 * Build the weeks roll: union of weeks that contain moments (bare or
 * completed) with the current week, sorted ascending by `weekStart`. Past
 * weeks have their moments sorted oldest-first; current and future weeks (if
 * a retro moment lives there) sort newest-first. Pivot is strict
 * `weekStart < currentWeekStart`.
 */
export function buildWeekRollEntries(
  moments: readonly Moment[],
  today: Date,
): WeekRollEntry[] {
  const currentStart = isoWeekStart(today)

  const bucket = new Map<string, { weekStart: Date; moments: Moment[] }>()
  const seed = (date: Date) => {
    const key = isoWeekKey(date)
    if (!bucket.has(key)) {
      bucket.set(key, { weekStart: isoWeekStart(date), moments: [] })
    }
    return bucket.get(key)!
  }

  seed(currentStart)

  for (const m of moments) {
    const happened = new Date(m.happened_at)
    seed(happened).moments.push(m)
  }

  const entries: WeekRollEntry[] = []
  for (const { weekStart, moments: bucketMoments } of bucket.values()) {
    const isPast = weekStart.getTime() < currentStart.getTime()
    const sorted = [...bucketMoments].sort((a, b) => {
      const aT = new Date(a.happened_at).getTime()
      const bT = new Date(b.happened_at).getTime()
      return isPast ? aT - bT : bT - aT
    })
    entries.push({
      weekStart,
      weekStartIso: isoWeekKey(weekStart),
      isoWeek: isoWeekNumber(weekStart),
      moments: sorted,
      pebbleCount: sorted.filter((m) => m.pebble !== null).length,
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
