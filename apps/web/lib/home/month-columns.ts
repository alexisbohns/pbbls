import type { HomeMonth, MonthWrapPebble } from "@/lib/types"
import type { CairnDay } from "./cairn"

/**
 * The current month as week columns (#1384, spec D10; iOS `WeekColumn.columns`):
 * a column per served week, each day placed by its date from the cover. A date
 * outside the month is blank, a day without a pebble is a socket, a day with
 * pebbles shows up to three in served order. Below one pebble the RPC sends no
 * weeks, so the columns are laid out from `daysInMonth` and `firstWeekday`
 * alone: every day a socket, every cairn dotted. Placement only, never
 * aggregation; dates are computed in UTC from the served month.
 */
export type ColumnDay = { kind: "outside" } | { kind: "empty" } | { kind: "pebbles"; pebbles: MonthWrapPebble[] }

export type WeekColumn = {
  /** Monday, `YYYY-MM-DD`. */
  weekStart: string
  /** The month's day indices (0-based) the week spans. */
  from: number
  to: number
  count: number
  soulCount: number
  days: ColumnDay[]
  /** The week's [moments, topIntensity] pairs; null for a week without a pebble (a dotted stone). */
  cairn: CairnDay[] | null
}

/** At most this many seals a day; a busier day shows three. */
export const PIPS = 3

const DAY_MS = 86_400_000

function utcDate(iso: string): Date {
  const [y, m, d] = iso.split("-").map(Number)
  return new Date(Date.UTC(y, m - 1, d))
}

function iso(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function monthColumns(m: HomeMonth): WeekColumn[] {
  const first = utcDate(m.month)
  const indexOf = (date: Date) => Math.round((date.getTime() - first.getTime()) / DAY_MS)
  const byDay = new Map<number, MonthWrapPebble[]>()
  for (const d of m.days) if (!byDay.has(d.day)) byDay.set(d.day, d.pebbles)

  const dayAt = (monday: Date, offset: number): ColumnDay => {
    const index = indexOf(monday) + offset
    if (index < 0 || index >= m.daysInMonth) return { kind: "outside" }
    const pebbles = byDay.get(index) ?? []
    return pebbles.length === 0 ? { kind: "empty" } : { kind: "pebbles", pebbles: pebbles.slice(0, PIPS) }
  }
  const week = (monday: Date) => Array.from({ length: 7 }, (_, k) => dayAt(monday, k))

  if (m.weeks) {
    return m.weeks.map((w) => {
      const monday = utcDate(w.weekStart)
      return {
        weekStart: w.weekStart,
        from: w.from,
        to: w.to,
        count: w.count,
        soulCount: w.soulCount,
        days: week(monday),
        cairn: w.count > 0 ? w.days : null,
      }
    })
  }

  const columns: WeekColumn[] = []
  for (let start = -m.firstWeekday; start < m.daysInMonth; start += 7) {
    const monday = new Date(first.getTime() + start * DAY_MS)
    columns.push({
      weekStart: iso(monday),
      from: Math.max(0, start),
      to: Math.min(m.daysInMonth - 1, start + 6),
      count: 0,
      soulCount: 0,
      days: week(monday),
      cairn: null,
    })
  }
  return columns
}

/** A seal's width in px for an intensity, from the pip (15): 0.85 / 1 / 1.15. */
export function sealWidth(intensity: 1 | 2 | 3, pip = 15): number {
  return pip * [0.85, 1, 1.15][intensity - 1]
}
